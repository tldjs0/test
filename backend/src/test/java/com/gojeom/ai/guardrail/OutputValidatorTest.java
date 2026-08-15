package com.gojeom.ai.guardrail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 가드레일 후검증 회귀 테스트.
 *
 * <p>ARCHITECTURE.md §12가 "회귀 방지 가치가 가장 큰" 테스트로 지목한 자리다.
 * 프롬프트를 손볼 때마다 출력이 바뀌므로, 무엇을 막아야 하는지는 코드로 고정한다.
 *
 * <p>Docker 없이 도는 순수 단위 테스트다. (이 환경에는 Testcontainers를 띄울 수 없다)
 */
class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator();

    @DisplayName("점수·등급·순위 표현을 잡는다 (G-1)")
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "피부 상태는 72점 정도로 보여요.",
            "피부 상태는 72 점 정도로 보여요.",
            "또래 중 상위 20% 수준이에요.",
            "또래 중 상위 20 % 수준이에요.",
            "전체적으로 3등급에 해당해요."
    })
    void 점수_표현을_막는다(String text) {
        assertThatThrownBy(() -> validator.validate(text))
                .isInstanceOf(GuardrailViolation.class);
    }

    @DisplayName("의료·시술 표현을 잡는다 (G-3)")
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "여드름 치료를 받아보세요.",
            "레이저 시술받는 것을 권해요.",
            "건조증 진단이 필요해 보여요.",
            "영양제를 처방받으세요."
    })
    void 금지어를_막는다(String text) {
        assertThatThrownBy(() -> validator.validate(text))
                .isInstanceOf(GuardrailViolation.class);
    }

    @DisplayName("정상 문구는 통과시킨다")
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "수분 섭취를 조금 늘려보시면 좋겠어요.",
            "부드러운 얼굴선이 강점이에요.",
            // 숫자가 붙지 않은 '점'은 정상 단어다. 과잉 차단하면 멀쩡한 결과가 실패한다.
            "이 부분이 장점으로 보여요.",
            "관점을 조금 바꿔보세요.",
            // 우선순위 표현은 외모 순위 매기기가 아니다.
            "1순위인 피부부터 살펴볼게요.",
            // 사용자가 입력한 수치를 근거로 밝히는 것은 권장 사항이다. (G-5)
            "평균 수면 6.5시간을 근거로 보았어요.",
            "체수분 32.5L, 골격근량 24.1kg을 함께 보았어요."
    })
    void 정상_문구는_통과한다(String text) {
        assertThatCode(() -> validator.validate(text)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null·빈 문자열은 검사 대상이 아니다")
    void 빈_입력은_통과한다() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("   ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("위반 사유가 재생성 프롬프트에 쓸 수 있게 담긴다")
    void 위반_사유를_담는다() {
        assertThatThrownBy(() -> validator.validate("피부 82점이에요."))
                .isInstanceOf(GuardrailViolation.class)
                .satisfies(e -> assertThatCode(
                        () -> ((GuardrailViolation) e).reason().isBlank()).doesNotThrowAnyException());
    }
}
