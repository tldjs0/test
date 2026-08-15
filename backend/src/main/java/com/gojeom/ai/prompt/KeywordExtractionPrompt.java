package com.gojeom.ai.prompt;

import com.gojeom.ai.AiStage;
import com.gojeom.ai.OpenAiRequest;
import com.gojeom.ai.dto.AiPayloads.KeywordExtraction;
import com.gojeom.ai.schema.JsonSchemas;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 고점 텍스트(+ 참고 사진 N장) → 키워드 5~8개. (PRD §8.1 [3])
 *
 * <p><b>사용자 본인 사진은 넣지 않는다.</b> PRD가 이 단계의 입력을 "고점 텍스트 +
 * 참고 사진"으로 정의했고, 얼굴 정보는 앞선 {@code PROFILE_ANALYSIS}의 요약
 * 텍스트로 이미 들어온다. 사진을 한 장 더 넣으면 지연과 비용만 늘어난다.
 *
 * <p>참고 사진은 <b>분위기 추출용</b>이다. 프롬프트에 얼굴 복제 금지를 명시한다. (G-2)
 */
@Component
@RequiredArgsConstructor
public class KeywordExtractionPrompt {

    private static final String INSTRUCTION = """

            [이 단계의 작업]
            사용자가 적은 고점 설명과 참고 사진의 분위기에서 '고점 키워드'를 5~8개 뽑는다.

            - 키워드(label)는 사용자가 화면에서 체크박스로 고를 짧은 말이다. 15자 이내로 쓴다.
            - reason에는 그 키워드를 왜 뽑았는지, 어떤 입력에서 나왔는지 밝힌다. (G-5)
            - category는 SKIN(피부) · FACE(얼굴형·인상) · BODY(체형) · HEALTH(건강) 중 하나다.
              얼굴선·눈매·인상에 대한 키워드는 반드시 FACE로 분류한다. HEALTH에 넣지 않는다.
            - 참고 사진이 있으면 그 인물의 얼굴을 묘사하거나 복제하지 않는다.
              색감·분위기·정돈된 정도 같은 요소만 읽는다. (G-2)
            - 서로 겹치는 키워드를 만들지 않는다. 사용자가 고를 수 있게 서로 다른 축으로 나눈다.
            """;

    private final JsonSchemas schemas;

    /**
     * @param referenceImageUrls presigned GET URL 목록. 비어 있으면 텍스트 전용 분석이다
     */
    public OpenAiRequest<KeywordExtraction> build(UUID analysisId, String inputText,
                                                  String profileFacts, List<String> referenceImageUrls) {
        var builder = OpenAiRequest.builder(AiStage.KEYWORD_EXTRACTION, KeywordExtraction.class)
                .analysisId(analysisId)
                .schema(schemas.keywordExtraction())
                .system(SystemPrompts.base() + INSTRUCTION)
                .text(profileFacts)
                .text("\n[사용자가 적은 고점]\n" + inputText);

        if (!referenceImageUrls.isEmpty()) {
            builder.text("\n[참고 사진 %d장] — 분위기 요소만 읽는다. 인물의 얼굴을 묘사하거나 복제하지 않는다."
                    .formatted(referenceImageUrls.size()));
            referenceImageUrls.forEach(builder::image);
        }
        return builder.build();
    }
}
