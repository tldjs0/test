package com.gojeom.ai.prompt;

import com.gojeom.ai.AiStage;
import com.gojeom.ai.OpenAiRequest;
import com.gojeom.ai.dto.AiPayloads.InbodyOcrPayload;
import com.gojeom.ai.schema.JsonSchemas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 인바디 서류 촬영본 → 6개 항목. (PRD §8.1 [2] · F-03 · API.md §7.4)
 *
 * <p><b>추측하지 않는 것이 이 단계의 전부다.</b> 잘못 읽은 수치가 프로필에 저장되면
 * 이후 모든 분석이 남의 몸을 근거로 삼는다. 읽지 못하면 null로 두게 한다. (G-8)
 *
 * <p>결과는 <b>저장되지 않는다.</b> 입력 폼을 채우는 용도이며 사용자가 확인·수정한
 * 뒤에야 저장된다. (API.md §6.3)
 */
@Component
@RequiredArgsConstructor
public class InbodyOcrPrompt {

    private static final String INSTRUCTION = """

            [이 단계의 작업]
            인바디(체성분 분석) 결과지 사진에서 6개 항목의 숫자를 읽는다.

            - bodyWaterL: 체수분 (L)
            - proteinKg: 단백질 (kg)
            - mineralKg: 무기질 (kg)
            - bodyFatKg: 체지방량 (kg)
            - skeletalMuscleKg: 골격근량 (kg)
            - bmi: BMI (단위 없음)

            반드시 지킨다:
            - **숫자가 흐리거나 가려져 확실하지 않으면 그 항목은 null로 둔다.**
              비슷한 값을 추측해 채우지 않는다. 빈칸이 틀린 값보다 낫다. (G-8)
            - 표에 없는 항목은 null이다. 다른 항목에서 계산해 만들어내지 않는다.
            - 단위가 표기와 다르면(예: 체지방률 %) 그 항목은 null로 둔다.
              체지방'량'(kg)과 체지방'률'(%)은 다른 값이다.
            - 인바디 결과지가 아닌 사진이면 6개 항목 전부 null로 반환한다.
            """;

    private final JsonSchemas schemas;

    public OpenAiRequest<InbodyOcrPayload> build(String documentUrl) {
        return OpenAiRequest.builder(AiStage.INBODY_OCR, InbodyOcrPayload.class)
                // 분석에 속하지 않는 단계라 analysisId는 null이다. (ERD.md §3.10)
                .schema(schemas.inbodyOcr())
                .system(SystemPrompts.ROLE + '\n' + SystemPrompts.GUARDRAILS + INSTRUCTION)
                .text("[인바디 결과지 사진]")
                .image(documentUrl)
                .build();
    }
}
