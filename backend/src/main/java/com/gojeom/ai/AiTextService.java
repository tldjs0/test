package com.gojeom.ai;

import com.gojeom.ai.guardrail.GuardrailViolation;
import com.gojeom.ai.guardrail.OutputValidator;
import com.gojeom.ai.prompt.SystemPrompts;
import com.gojeom.common.exception.ErrorCode;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 텍스트 단계의 공통 실행 절차 — 호출 → 후검증 → <b>1회 재생성</b> → 실패.
 * (ARCHITECTURE.md §6.3 · §6.4)
 *
 * <p>두 종류의 후검증을 같은 방식으로 다룬다.
 * <ul>
 *   <li><b>가드레일</b> — 점수 표현·금지어 ({@link OutputValidator})</li>
 *   <li><b>정합성</b> — 스키마로 강제되지 않는 규칙. 예: {@code categoryChanges} 3건의 카테고리 구성</li>
 * </ul>
 * 둘 다 "무엇이 틀렸는지 알려주고 한 번 더 시킨다"가 정답이라 {@link GuardrailViolation}
 * 하나로 신호를 통일했다.
 *
 * <p><b>재생성은 1회뿐이다.</b> 두 번째도 어기면 실패시킨다. 규칙을 어긴 텍스트를
 * 사용자에게 보여주느니 "다시 시도해주세요"가 낫다. (AGENTS.md 규칙 4·5)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTextService {

    private final OpenAiClient openAiClient;
    private final OutputValidator outputValidator;

    public <T> T generate(OpenAiRequest<T> request, Function<T, String> userFacingText) {
        return generate(request, userFacingText, payload -> { });
    }

    /**
     * @param userFacingText 사용자에게 노출될 문자열만 모아 반환한다
     * @param consistency    스키마로 강제 못 하는 규칙 검사. 위반이면 {@link GuardrailViolation}을 던진다
     * @throws AiException 재생성 후에도 위반이면 {@code AI_PROVIDER_ERROR}
     */
    public <T> T generate(OpenAiRequest<T> request, Function<T, String> userFacingText,
                          Consumer<T> consistency) {
        try {
            return verify(openAiClient.complete(request), userFacingText, consistency);
        } catch (GuardrailViolation first) {
            log.warn("후검증 위반 → 1회 재생성 stage={} reason={}", request.stage(), first.reason());
            OpenAiRequest<T> retry = request.withSystem(
                    SystemPrompts.withViolation(request.system(), first.reason()));
            try {
                return verify(openAiClient.complete(retry), userFacingText, consistency);
            } catch (GuardrailViolation second) {
                log.error("재생성 후에도 후검증 위반 stage={} reason={}", request.stage(), second.reason());
                throw new AiException(ErrorCode.AI_PROVIDER_ERROR, false,
                        "가드레일 재위반: " + second.reason());
            }
        }
    }

    private <T> T verify(T payload, Function<T, String> userFacingText, Consumer<T> consistency) {
        outputValidator.validate(userFacingText.apply(payload));
        consistency.accept(payload);
        return payload;
    }
}
