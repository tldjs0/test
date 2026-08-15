package com.gojeom.profile;

import com.gojeom.ai.AiException;
import com.gojeom.ai.AiStage;
import com.gojeom.ai.AiTextService;
import com.gojeom.ai.dto.AiPayloads.ProfileAnalysisPayload;
import com.gojeom.ai.guardrail.TruncationDetector;
import com.gojeom.ai.prompt.ProfileAnalysisPrompt;
import com.gojeom.ai.prompt.ProfileFacts;
import com.gojeom.ai.schema.JsonSchemas;
import com.gojeom.common.config.AsyncConfig;
import com.gojeom.common.config.OpenAiProperties;
import com.gojeom.profile.entity.ProfileAnalysisSummary;
import com.gojeom.storage.StorageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 프로필 AI 분석. (D2-2 · PRD §8.1 [1] · F-04)
 *
 * <p>사진과 신체 정보를 요약해 {@code profiles.analysis_summary}에 채운다.
 * 이 값은 뒤따르는 키워드 추출·결과 생성에 <b>텍스트로</b> 전달되어, 그 단계들이
 * 사진을 다시 보내지 않아도 되게 만든다.
 *
 * <p><b>실패해도 프로필 등록은 성공이다.</b> 요약은 분석 품질을 높이는 보조 정보이지
 * 프로필의 필수 구성이 아니다. 실패하면 {@code analysis_summary}가 null로 남고,
 * 이후 단계는 요약 없이 진행된다. 가짜 값을 채우지 않는다. (AGENTS.md 규칙 15)
 *
 * <p>트랜잭션이 없다. DB 접근은 {@link ProfileTxService}가 짧게 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileAnalysisPipeline {

    private final ProfileTxService profileTx;
    private final StorageService storageService;
    private final AiTextService aiTextService;
    private final TruncationDetector truncationDetector;
    private final ProfileAnalysisPrompt prompt;
    private final OpenAiProperties openAiProperties;

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public void run(UUID profileId) {
        ProfileSnapshot snapshot = profileTx.loadSnapshot(profileId).orElse(null);
        if (snapshot == null) {
            log.info("프로필 분석 건너뜀 — 프로필이 없다");
            return;
        }
        if (snapshot.photoKey() == null) {
            // 사진 없이 얼굴 인상을 만들 수 없다. 추측하지 않고 비운다.
            log.info("프로필 분석 건너뜀 — 사진 없음");
            return;
        }

        String facts = ProfileFacts.render(snapshot.priorities(), snapshot.heightCm(),
                snapshot.weightKg(), snapshot.sleepHours(), snapshot.inbody(), null);
        String photoUrl = storageService.presignDownload(snapshot.photoKey());

        try {
            ProfileAnalysisPayload payload = aiTextService.generate(
                    prompt.build(photoUrl, facts),
                    ProfileAnalysisPayload::userFacingText);

            truncationDetector.inspect(AiStage.PROFILE_ANALYSIS, "faceImpression",
                    JsonSchemas.FACE_IMPRESSION_MAX, payload.faceImpression());
            truncationDetector.inspect(AiStage.PROFILE_ANALYSIS, "bodyRange",
                    JsonSchemas.BODY_RANGE_MAX, payload.bodyRange());
            truncationDetector.inspect(AiStage.PROFILE_ANALYSIS, "healthNotes",
                    JsonSchemas.HEALTH_NOTE_MAX, payload.healthNotes());

            profileTx.applySummary(profileId, new ProfileAnalysisSummary(
                    payload.faceImpression(),
                    payload.bodyRange(),
                    payload.healthNotes(),
                    openAiProperties.model().text(),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC))));

            log.info("프로필 분석 완료");
        } catch (AiException e) {
            // 사진 key·URL을 로그에 남기지 않는다. (AGENTS.md 규칙 9)
            log.warn("프로필 분석 실패 code={} : {}", e.errorCode().name(), e.getMessage());
        }
    }
}
