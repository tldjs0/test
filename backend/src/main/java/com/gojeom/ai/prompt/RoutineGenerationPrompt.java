package com.gojeom.ai.prompt;

import com.gojeom.ai.AiStage;
import com.gojeom.ai.OpenAiRequest;
import com.gojeom.ai.dto.AiPayloads.RoutinePlan;
import com.gojeom.ai.dto.AiPayloads.StandalonePlan;
import com.gojeom.ai.schema.JsonSchemas;
import com.gojeom.common.enums.Category;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 목표 생성. 경로 2종을 각각 다른 프롬프트·스키마로 다룬다. (PRD F-09 · §8.1 [6])
 *
 * <p>두 경로 모두 <b>우선순위를 가중치로 반영</b>한다. 1순위 카테고리의 태스크를
 * 가장 구체적으로 만든다.
 *
 * <p>태스크의 세 라벨은 화면에서 {@code " / "}로 이어 한 줄에 붙는다. 길면 잘리므로
 * 글자 수를 숫자로 못박는다. ([AGENTS.md](../../../../../../../../AGENTS.md) N-1)
 */
@Component
@RequiredArgsConstructor
public class RoutineGenerationPrompt {

    private static final String TASK_FORMAT = """

            [태스크 작성 규칙]
            화면에서 각 태스크는 이렇게 두 줄로 그려진다.

                자외선 차단제 바르기
                매일 외출 전 / 약 2분 / 4ml          [ ] 완료

            - title: 무엇을 하는지. **15자 이내 동사구.** 예) "자외선 차단제 바르기"
            - timing: 언제 하는지. **10자 이내.** 예) "매일 외출 전" · "자기 전"
            - durationLabel: 얼마나 걸리는지. **8자 이내.** 예) "약 2분"
            - amountLabel: 얼마나 하는지. **8자 이내.** 예) "4ml" · "10회"
            - durationLabel·amountLabel은 **해당 개념이 없으면 null로 둔다.**
              억지로 채우지 않는다. "1회" 같은 의미 없는 값을 넣지 마라.
            - 도구나 비용이 거의 들지 않고, 오늘 바로 시작할 수 있는 것으로 고른다.
            - 사용자를 비난하거나 실패로 규정하는 표현을 쓰지 않는다. (PRD F-10)
            """;

    private static final String FROM_ANALYSIS = """

            [이 단계의 작업]
            저장된 고점 분석 결과를 근거로 **목표 1개**를 만든다.
            이 목표는 여러 카테고리에 걸친다 — 태스크마다 category를 알맞게 붙인다.

            - title: 이 목표가 무엇을 향하는지 나타내는 60자 이내 제목.
            - tasks: 결과지의 '카테고리별 변화'와 '오늘 해볼 관리'를 실행 단위로 옮긴다.
              결과지에 없는 이야기를 새로 만들지 않는다. (G-5)
            - 우선순위 1순위 카테고리의 태스크를 가장 구체적으로 쓴다.
            """;

    private static final String STANDALONE = """

            [이 단계의 작업]
            분석 결과 없이, 사용자가 고른 **카테고리마다 목표 1개씩** 만든다.

            - 요청받은 카테고리 수와 정확히 같은 수의 목표를 만든다. 빠뜨리거나 더하지 않는다.
            - 각 목표의 tasks에 붙는 category는 **그 목표의 카테고리와 같아야 한다.**
            - 목표끼리 같은 태스크를 중복해서 넣지 않는다.
            - 근거는 사용자가 입력한 신체 정보와 우선순위뿐이다. 분석 결과가 없으므로
              고점 키워드를 지어내지 않는다. (G-5)
            - 기간이 긴 카테고리일수록 태스크를 천천히 쌓이는 구성으로 만든다.
            """;

    private final JsonSchemas schemas;

    /**
     * 경로 A — 저장된 분석 결과 기반.
     *
     * @param resultDigest 결과지 내용을 텍스트로 펼친 것
     */
    public OpenAiRequest<RoutinePlan> forAnalysis(String profileFacts, String resultDigest,
                                                  List<Category> priorities) {
        return OpenAiRequest.builder(AiStage.ROUTINE_GENERATION, RoutinePlan.class)
                .schema(schemas.routineFromAnalysis())
                .system(SystemPrompts.base() + SystemPrompts.PRIORITY_WEIGHTING
                        + FROM_ANALYSIS + TASK_FORMAT)
                .text(profileFacts)
                .text("\n[근거가 되는 고점 분석 결과]\n" + resultDigest)
                .text("\n[우선순위 가중치] " + priorityLine(priorities))
                .build();
    }

    /**
     * 경로 B — 카테고리 + 기간만으로 생성.
     *
     * @param itemLines "피부 · 4주" 형태의 줄
     */
    public OpenAiRequest<StandalonePlan> forStandalone(String profileFacts, List<String> itemLines,
                                                       List<Category> priorities) {
        return OpenAiRequest.builder(AiStage.ROUTINE_GENERATION, StandalonePlan.class)
                .schema(schemas.routineStandalone())
                .system(SystemPrompts.base() + SystemPrompts.PRIORITY_WEIGHTING
                        + STANDALONE + TASK_FORMAT)
                .text(profileFacts)
                .text("\n[사용자가 고른 카테고리와 기간] — 이 %d개 각각에 목표를 하나씩 만든다\n%s"
                        .formatted(itemLines.size(), String.join("\n", itemLines)))
                .text("\n[우선순위 가중치] " + priorityLine(priorities))
                .build();
    }

    private String priorityLine(List<Category> priorities) {
        return priorities.stream().map(Category::label).reduce((a, b) -> a + " > " + b).orElse("");
    }
}
