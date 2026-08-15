package com.gojeom.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gojeom.ai.dto.ChatDtos.ChatRequest;
import com.gojeom.ai.dto.ChatDtos.ChatResponse;
import com.gojeom.ai.dto.ChatDtos.Choice;
import com.gojeom.ai.dto.ChatDtos.Message;
import com.gojeom.ai.dto.ChatDtos.ResponseFormat;
import com.gojeom.ai.job.AiJobRecorder;
import com.gojeom.common.config.OpenAiProperties;
import com.gojeom.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI {@code /v1/chat/completions} 호출. (ARCHITECTURE.md §6.1)
 *
 * <p><b>이 클래스를 트랜잭션 안에서 부르지 않는다.</b> 한 번에 4~11초가 걸려
 * DB 커넥션 풀이 마른다. 호출부는 반드시 트랜잭션 밖이어야 한다. (§5.2 · 규칙 L-4)
 *
 * <table>
 *   <caption>호출 정책</caption>
 *   <tr><td>타임아웃</td><td>connect 5초 / read 60초 (설정값)</td></tr>
 *   <tr><td>재시도</td><td>429·5xx·스키마 위반만 최대 2회, 지수 백오프</td></tr>
 *   <tr><td>재시도 안 함</td><td>그 외 4xx · 정책 거부 · read 타임아웃</td></tr>
 * </table>
 *
 * <p>read 타임아웃을 재시도하지 않는 이유 — 60초를 기다린 뒤 또 60초를 기다리면
 * 좀비 정리(3분)와 프론트 폴링 상한(60초)을 모두 넘긴다. 사용자는 이미 떠났다.
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final Duration[] BACKOFF = {Duration.ofSeconds(1), Duration.ofSeconds(2)};

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiJobRecorder recorder;

    public OpenAiClient(OpenAiProperties properties, ObjectMapper objectMapper, AiJobRecorder recorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.recorder = recorder;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.timeout().connectSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeout().readSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    /**
     * 스키마대로 응답을 받아 파싱한다.
     *
     * @throws AiException 재시도까지 실패하면. {@code errorCode()}가 실패 사유를 들고 있다
     */
    public <T> T complete(OpenAiRequest<T> request) {
        ChatRequest body = new ChatRequest(
                properties.model().text(),
                List.of(Message.system(request.system()), Message.user(request.userParts())),
                ResponseFormat.jsonSchema(request.schema()));

        AiException last = null;
        int attempts = properties.maxRetries() + 1;

        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0) {
                sleep(BACKOFF[Math.min(attempt - 1, BACKOFF.length - 1)]);
                log.info("AI 재시도 stage={} attempt={}/{}", request.stage(), attempt + 1, attempts);
            }
            try {
                return attempt(request, body);
            } catch (AiException e) {
                if (!e.retryable()) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    private <T> T attempt(OpenAiRequest<T> request, ChatRequest body) {
        long started = System.nanoTime();
        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    // 기본 에러 핸들러를 끄고 상태 코드를 직접 판정한다.
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(String.class);
        } catch (ResourceAccessException e) {
            throw failed(request, elapsedMs(started), ErrorCode.ANALYSIS_TIMEOUT, false,
                    "OpenAI 응답 없음", e);
        }

        int elapsed = elapsedMs(started);
        HttpStatusCode status = response.getStatusCode();

        if (status.isError()) {
            throw mapHttpError(request, status, response.getBody(), elapsed);
        }

        ChatResponse parsed;
        try {
            parsed = objectMapper.readValue(response.getBody(), ChatResponse.class);
        } catch (Exception e) {
            throw failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, true, "응답 봉투 파싱 실패", e);
        }

        String model = parsed.model() != null ? parsed.model() : properties.model().text();
        Choice choice = parsed.firstChoice();

        if (choice == null || choice.message() == null) {
            throw failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, true, "choices 비어 있음", null);
        }
        // 모델이 응답을 거부한 경우. 예외 상황이 아니라 정책상 정상 경로다.
        if (choice.message().refusal() != null) {
            throw failed(request, elapsed, ErrorCode.CONTENT_POLICY_BLOCKED, false,
                    "모델이 응답을 거부했다", null);
        }
        if ("content_filter".equals(choice.finishReason())) {
            throw failed(request, elapsed, ErrorCode.CONTENT_POLICY_BLOCKED, false, "content_filter", null);
        }
        if ("length".equals(choice.finishReason())) {
            // 잘린 JSON이라 파싱해도 의미가 없다.
            throw failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, true,
                    "출력이 잘렸다 (finish_reason=length)", null);
        }

        T payload;
        try {
            payload = objectMapper.readValue(choice.message().content(), request.responseType());
        } catch (Exception e) {
            // strict 스키마를 어긴 출력. 파싱을 완화하지 않고 재시도한다. (§6.2)
            throw failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, true, "스키마 위반 출력", e);
        }

        recorder.recordDone(request.analysisId(), request.stage(), model,
                parsed.usage() != null ? parsed.usage().promptTokens() : null,
                parsed.usage() != null ? parsed.usage().completionTokens() : null,
                elapsed);
        log.info("AI 완료 stage={} model={} {}ms", request.stage(), model, elapsed);
        return payload;
    }

    private AiException mapHttpError(OpenAiRequest<?> request, HttpStatusCode status,
                                     String body, int elapsed) {
        if (status.value() == 429 || status.is5xxServerError()) {
            return failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, true,
                    "OpenAI %d 응답".formatted(status.value()), null);
        }
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (lower.contains("content_policy") || lower.contains("content_filter")) {
            return failed(request, elapsed, ErrorCode.CONTENT_POLICY_BLOCKED, false,
                    "정책 위반으로 거절됨", null);
        }
        // 그 외 4xx는 요청 자체가 틀린 것이다. 다시 불러도 같은 답이 온다.
        // 본문에 프롬프트가 섞여 있을 수 있어 원문을 남기지 않고 상태 코드만 남긴다. (규칙 9)
        log.error("OpenAI 요청 거절 stage={} status={}", request.stage(), status.value());
        return failed(request, elapsed, ErrorCode.AI_PROVIDER_ERROR, false,
                "OpenAI %d 응답".formatted(status.value()), null);
    }

    /** 실패를 {@code ai_jobs}에 남기고 던질 예외를 만든다. */
    private AiException failed(OpenAiRequest<?> request, int elapsed, ErrorCode code,
                               boolean retryable, String detail, Throwable cause) {
        recorder.recordFailed(request.analysisId(), request.stage(),
                properties.model().text(), elapsed, code.name());
        return new AiException(code, retryable, detail, cause);
    }

    private int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(ErrorCode.AI_PROVIDER_ERROR, false, "재시도 대기 중 인터럽트", e);
        }
    }
}
