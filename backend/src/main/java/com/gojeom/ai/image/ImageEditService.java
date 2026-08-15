package com.gojeom.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gojeom.ai.AiException;
import com.gojeom.ai.AiStage;
import com.gojeom.ai.job.AiJobRecorder;
import com.gojeom.common.config.OpenAiProperties;
import com.gojeom.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 비교 이미지 생성. {@code /v1/images/edits} 호출. (ARCHITECTURE.md §6.5 · API.md §7.6)
 *
 * <p><b>텍스트 단계와 분리된 클라이언트를 쓴다.</b> 실측 35초로 텍스트(2~4초)보다
 * 한 자릿수 느리고, multipart라 직렬화 방식도 다르다. read 타임아웃을 넉넉히 잡는다.
 *
 * <p><b>정책 거부는 정상 시나리오다.</b> 실제 인물 사진 편집은 제공자 정책에 걸릴 수
 * 있다. 예외로 흘려보내지 않고 호출부가 {@code image_status = FAILED}로 저장한다.
 * 이미지 실패는 <b>분석권 미차감 사유가 아니다</b> — 텍스트 결과는 이미 나왔다.
 */
@Slf4j
@Component
public class ImageEditService {

    /** 이미지 생성은 오래 걸린다. 텍스트용 60초로는 모자란다. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    /** 정사각 1024. 결과 화면이 좌우 2장을 나란히 놓으므로 정사각이 잘 맞는다. */
    private static final String SIZE = "1024x1024";

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final AiJobRecorder recorder;

    public ImageEditService(OpenAiProperties properties, ObjectMapper objectMapper, AiJobRecorder recorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.recorder = recorder;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.timeout().connectSeconds()));
        factory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    /**
     * 사용자 사진(주) + 참고 사진(보조)으로 고점 예상 이미지를 만든다.
     *
     * @param userPhoto       사용자 본인 사진 바이트. <b>첫 번째로 보낸다</b> — 프롬프트가 "첫 번째 이미지"를 본인으로 지칭한다
     * @param referencePhotos 참고 사진. 분위기 요소만 쓰인다 (G-2)
     * @return PNG 바이트
     * @throws AiException 실패 시. 호출부는 {@code image_status = FAILED}로 저장한다
     */
    public byte[] generate(UUID analysisId, String prompt, byte[] userPhoto, List<byte[]> referencePhotos) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", properties.model().image());
        form.add("prompt", prompt);
        form.add("size", SIZE);
        form.add("n", "1");
        // 얼굴을 최대한 보존한다. 이 값이 없으면(기본 low) 모델이 인물을 새로 그려
        // "다른 사람"이 나온다. 정체성 유지가 이 단계의 전제다.
        form.add("input_fidelity", "high");
        form.add("image[]", namedImage(userPhoto, "user.png"));
        for (int i = 0; i < referencePhotos.size(); i++) {
            form.add("image[]", namedImage(referencePhotos.get(i), "reference-" + i + ".png"));
        }

        long started = System.nanoTime();
        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .uri("/images/edits")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> { })
                    .toEntity(String.class);
        } catch (ResourceAccessException e) {
            throw failed(analysisId, elapsedMs(started), ErrorCode.ANALYSIS_TIMEOUT, "이미지 응답 없음", e);
        }

        int elapsed = elapsedMs(started);
        if (response.getStatusCode().isError()) {
            throw mapError(analysisId, response.getStatusCode(), response.getBody(), elapsed);
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode encoded = root.path("data").path(0).path("b64_json");
            if (encoded.isMissingNode() || encoded.asText().isBlank()) {
                throw failed(analysisId, elapsed, ErrorCode.AI_PROVIDER_ERROR, "b64_json 없음", null);
            }
            byte[] png = Base64.getDecoder().decode(encoded.asText());

            recorder.recordDone(analysisId, AiStage.IMAGE_GENERATION, properties.model().image(),
                    root.path("usage").path("input_tokens").isMissingNode() ? null : root.path("usage").path("input_tokens").asInt(),
                    root.path("usage").path("output_tokens").isMissingNode() ? null : root.path("usage").path("output_tokens").asInt(),
                    elapsed);
            log.info("비교 이미지 생성 완료 {}ms {}bytes", elapsed, png.length);
            return png;

        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw failed(analysisId, elapsed, ErrorCode.AI_PROVIDER_ERROR, "이미지 응답 파싱 실패", e);
        }
    }

    private ByteArrayResource namedImage(byte[] bytes, String filename) {
        // multipart는 파일명이 있어야 파일 파트로 인식된다.
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private AiException mapError(UUID analysisId, HttpStatusCode status, String body, int elapsed) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);

        // 실제 인물 편집 거부. 예외 상황이 아니라 정책상 정상 경로다.
        if (lower.contains("content_policy") || lower.contains("safety") || lower.contains("moderation")) {
            return failed(analysisId, elapsed, ErrorCode.CONTENT_POLICY_BLOCKED, "이미지 정책 거부", null);
        }
        // 본문에 사용자 사진 정보가 섞일 수 있어 상태 코드만 남긴다. (AGENTS.md 규칙 9)
        log.warn("이미지 생성 거절 status={}", status.value());
        return failed(analysisId, elapsed, ErrorCode.AI_PROVIDER_ERROR,
                "이미지 %d 응답".formatted(status.value()), null);
    }

    private AiException failed(UUID analysisId, int elapsed, ErrorCode code, String detail, Throwable cause) {
        recorder.recordFailed(analysisId, AiStage.IMAGE_GENERATION, properties.model().image(), elapsed, code.name());
        return new AiException(code, false, detail, cause);
    }

    private int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
