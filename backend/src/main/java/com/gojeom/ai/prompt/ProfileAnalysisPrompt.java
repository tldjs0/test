package com.gojeom.ai.prompt;

import com.gojeom.ai.AiStage;
import com.gojeom.ai.OpenAiRequest;
import com.gojeom.ai.dto.AiPayloads.ProfileAnalysisPayload;
import com.gojeom.ai.schema.JsonSchemas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사진 + 신체 정보 → 현재 상태 구조화 요약. (PRD §8.1 [1] · F-04)
 *
 * <p>이 단계의 산출물이 {@code profiles.analysis_summary}가 되고, 이후 키워드 추출과
 * 결과 생성에 <b>텍스트로</b> 전달된다. 덕분에 뒤 단계는 사진을 다시 보내지 않아도 된다.
 *
 * <p><b>점수·등급을 만들지 않는다.</b> 스키마에 해당 필드가 없고 프롬프트로도 막는다.
 * (PRD G-1 · F-04)
 */
@Component
@RequiredArgsConstructor
public class ProfileAnalysisPrompt {

    private static final String INSTRUCTION = """

            [이 단계의 작업]
            사용자의 사진과 입력한 신체 정보로 '현재 상태'를 담백하게 요약한다.
            이것은 평가가 아니라, 뒤따르는 고점 분석이 참조할 기록이다.

            세 항목 모두 **문장이 아니라 짧은 메모**다. 길게 쓰면 중간에서 잘린다.
            마침표를 찍지 않고, 서술어로 끝내지 않는다.

            - faceImpression: 얼굴선·눈매·전체 인상을 가리키는 **12자 이내 명사구**.
              좋고 나쁨을 가르지 않는다.
              좋은 예) "부드러운 얼굴선" · "자연스러운 표정"
              나쁜 예) "얼굴선과 눈매가 단정하게 보이는 편입니다"
            - bodyRange: 키·몸무게·인바디를 근거로 한 **15자 이내 표현** 한 개.
              좋은 예) "표준 범위"
              체중을 목표 수치로 단정하지 않는다. (G-4)
            - healthNotes: **25자 이내 한 줄** 메모. 근거가 된 입력값을 함께 적는다. (G-5)
              좋은 예) "평균 수면 6.5시간 · 사용자 입력 기준"
              수치를 여러 개 나열하지 않는다. 항목당 하나씩만 적는다.
            - 사용자가 입력하지 않은 항목은 언급하지 않는다. 추측하지 않는다.
            - 얼굴이 보이지 않거나 사람이 여럿이면 그 사실만 faceImpression에 적는다.
            """;

    private final JsonSchemas schemas;

    public OpenAiRequest<ProfileAnalysisPayload> build(String photoUrl, String profileFacts) {
        return OpenAiRequest.builder(AiStage.PROFILE_ANALYSIS, ProfileAnalysisPayload.class)
                // 분석에 속하지 않는 단계라 analysisId는 null이다. (ERD.md §3.10)
                .schema(schemas.profileAnalysis())
                .system(SystemPrompts.base() + INSTRUCTION)
                .text(profileFacts)
                .text("\n[사용자 사진]")
                .image(photoUrl)
                .build();
    }
}
