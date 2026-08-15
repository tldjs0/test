package com.gojeom.ai.guardrail;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AI 출력 후검증. ★ (ARCHITECTURE.md §6.3 · API.md §7.5)
 *
 * <p><b>프롬프트에 G-1~G-8을 넣는 것만으로는 가드레일이 보장되지 않는다.</b>
 * 모델은 확률적으로 답한다. 서버가 나온 글자를 직접 검사해야 규칙이 규칙이 된다.
 * (AGENTS.md 규칙 14)
 *
 * <p>검출 시 호출부가 <b>1회 재생성</b>하고, 재차 걸리면 {@code AI_PROVIDER_ERROR}로
 * 실패 처리한다. 위반한 텍스트를 사용자에게 보여주느니 실패가 낫다.
 */
@Component
public class OutputValidator {

    /**
     * 점수·등급·순위 표현. (PRD G-1 · AGENTS.md 규칙 4)
     *
     * <p>"72점", "상위 20%", "3등급" 같은 표현을 잡는다. 숫자를 요구하므로
     * "장점"·"관점" 같은 정상 단어는 걸리지 않는다.
     *
     * <p>{@code \d+\s*위}(순위)는 <b>일부러 넣지 않았다.</b> 프롬프트가 우선순위를
     * 알려주므로 모델이 "1순위 피부"처럼 정상적으로 쓸 수 있고, 그때마다 재생성이
     * 돌면 실패율만 올라간다. 외모를 순위로 매기는 것과 카테고리 우선순위는 다르다.
     */
    private static final Pattern SCORE = Pattern.compile("\\d+\\s*점|상위\\s*\\d+\\s*%|\\d+\\s*등급");

    /**
     * 의료·시술 표현. (PRD G-3)
     *
     * <p>ARCHITECTURE.md §6.3의 4개를 그대로 쓴다. API.md §7.5는 3개만 적고 있으나
     * "처방"을 뺄 이유가 없어 넓은 쪽을 택했다.
     */
    private static final List<String> BANNED = List.of("치료", "시술받", "진단", "처방");

    /**
     * @throws GuardrailViolation 점수 패턴이나 금지어가 검출되면
     */
    public void validate(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (SCORE.matcher(text).find()) {
            throw new GuardrailViolation("외모를 점수·등급·순위로 표현했다. 수치 평가 표현을 쓰지 마라.");
        }
        for (String banned : BANNED) {
            if (text.contains(banned)) {
                throw new GuardrailViolation(
                        "의료·시술을 연상시키는 표현('%s')을 썼다. 일상 관리 표현으로 바꿔라.".formatted(banned));
            }
        }
    }
}
