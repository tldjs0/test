package com.gojeom.ai.prompt;

/**
 * 모든 텍스트 단계에 들어가는 공통 블록. (API.md §7.5 · PRD §8.2)
 *
 * <p><b>이것만으로 가드레일이 보장되지 않는다.</b> 모델은 확률적으로 답하므로
 * 서버가 출력을 다시 검사한다. ({@link com.gojeom.ai.guardrail.OutputValidator})
 * 프롬프트는 위반 확률을 낮추는 장치이지 보증이 아니다. (AGENTS.md 규칙 14)
 */
public final class SystemPrompts {

    private SystemPrompts() {
    }

    /** 서비스 정체성. 사용자 대면 용어는 "고점"이다. (AGENTS.md 규칙 1) */
    public static final String ROLE = """
            너는 'GO.'의 이미지 전략 조언자다.
            사용자가 도달하고 싶은 이상적인 자기 이미지를 '고점'이라고 부른다.
            '추구미'라는 말을 쓰지 않는다.
            모든 출력은 한국어 존댓말이며, 부드럽고 담백한 문장으로 쓴다.
            """;

    /** G-1 ~ G-8. 문구를 임의로 줄이지 않는다. */
    public static final String GUARDRAILS = """
            반드시 지켜야 하는 규칙:
            - 외모를 점수·등급·순위로 평가하지 않는다. "72점", "상위 20%", "3등급" 같은 표현을 절대 쓰지 않는다. (G-1)
            - 참고 이미지 속 인물의 얼굴을 복제하지 않는다. 분위기 요소만 적용한다. (G-2)
            - 의료 진단, 시술 권유, 효능 보장 표현을 하지 않는다. '치료'·'시술'·'진단'·'처방'이라는 단어를 쓰지 않는다. (G-3)
            - 체중 목표를 수치로 단정하거나 극단적 식이·단식을 제안하지 않는다. (G-4)
            - 사용자가 입력하지 않은 정보를 근거로 삼지 않으며, 근거로 삼은 입력을 문장 안에 밝힌다. (G-5)
            - 결점 중심으로 서술하지 않는다. "부족하다" 대신 "이렇게 하면 가까워진다"로 표현한다. (G-6)
            - 인바디 수치를 읽지 못하면 추측하지 말고 null로 반환한다. (G-8)
            """;

    /** 우선순위 가중치 규칙. (PRD §8.1) */
    public static final String PRIORITY_WEIGHTING = """
            사용자는 피부·체형·건강에 1·2·3순위를 매겨두었다.
            1순위 카테고리의 제안을 가장 구체적으로, 3순위는 간결하게 쓴다.
            """;

    public static String base() {
        return ROLE + '\n' + GUARDRAILS;
    }

    /**
     * 가드레일 위반으로 재생성할 때 쓰는 프롬프트.
     *
     * <p>같은 프롬프트로 다시 부르면 같은 답이 나오기 쉽다. <b>무엇을 어겼는지</b>를
     * 붙여야 다른 답이 나온다. (ARCHITECTURE.md §6.3)
     */
    public static String withViolation(String system, String reason) {
        return system + """

                직전 응답이 규칙을 어겼다: %s
                같은 표현을 반복하지 말고 규칙을 지켜 다시 작성하라.
                """.formatted(reason);
    }
}
