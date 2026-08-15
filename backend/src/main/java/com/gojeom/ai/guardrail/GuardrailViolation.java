package com.gojeom.ai.guardrail;

/**
 * 가드레일 후검증 위반. 프롬프트가 지켜지지 않았다는 신호다.
 *
 * <p>{@link #reason()}은 재생성 시 시스템 프롬프트에 덧붙여 모델에게 무엇을
 * 어겼는지 알려준다. 같은 프롬프트로 다시 부르면 같은 답이 나오기 쉽다.
 */
public class GuardrailViolation extends RuntimeException {

    private final String reason;

    public GuardrailViolation(String reason) {
        super("guardrail violation: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
