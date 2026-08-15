package com.gojeom.analysis.service;

import com.gojeom.ai.AiException;
import com.gojeom.ai.AiStage;
import com.gojeom.ai.AiTextService;
import com.gojeom.ai.dto.AiPayloads.ExtractedKeyword;
import com.gojeom.ai.dto.AiPayloads.KeywordExtraction;
import com.gojeom.ai.dto.AiPayloads.PeakResult;
import com.gojeom.ai.guardrail.GuardrailViolation;
import com.gojeom.ai.guardrail.TruncationDetector;
import com.gojeom.ai.prompt.KeywordExtractionPrompt;
import com.gojeom.ai.prompt.ProfileFacts;
import com.gojeom.ai.prompt.ResultGenerationPrompt;
import com.gojeom.ai.schema.JsonSchemas;
import com.gojeom.analysis.entity.CategoryChange;
import com.gojeom.common.config.AsyncConfig;
import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.ImageStatus;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.storage.StorageService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 비동기 분석 파이프라인. ★ 이 시스템의 핵심이다. (ARCHITECTURE.md §5)
 *
 * <p><b>이 클래스에 {@code @Transactional}이 하나도 없는 것이 요점이다.</b>
 * OpenAI 호출은 4~11초가 걸린다. 트랜잭션 안에서 부르면 DB 커넥션을 그동안 붙잡아
 * 풀이 마른다. DB 접근은 전부 {@link AnalysisTxService}가 짧게 잡는다.
 *
 * <pre>
 * runKeywordExtraction
 *   ├─ analysisTx.markExtracting(id)        ← 짧은 트랜잭션
 *   ├─ openAi.extractKeywords(...)          ← 트랜잭션 밖 (수 초)
 *   └─ analysisTx.saveKeywords(id, ...)     ← 짧은 트랜잭션
 * </pre>
 *
 * <p>시작은 {@code @TransactionalEventListener(AFTER_COMMIT)}가 건다. 커밋 전에
 * 시작하면 비동기 스레드가 아직 없는 행을 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisPipeline {

    private final AnalysisTxService analysisTx;
    private final ImagePipeline imagePipeline;
    private final StorageService storageService;
    private final AiTextService aiTextService;
    private final TruncationDetector truncationDetector;
    private final KeywordExtractionPrompt keywordPrompt;
    private final ResultGenerationPrompt resultPrompt;

    // ------------------------------------------------------------ 키워드 추출

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public void runKeywordExtraction(UUID analysisId) {
        AnalysisContext context = analysisTx.loadContext(analysisId).orElse(null);
        if (context == null) {
            log.warn("키워드 추출 중단 — 분석 또는 프로필이 없다");
            return;
        }
        if (!analysisTx.markExtracting(analysisId)) {
            // 좀비 정리가 먼저 실패로 돌렸거나 이미 진행 중이다.
            log.info("키워드 추출 건너뜀 — 상태가 CREATED가 아니다");
            return;
        }

        try {
            List<String> referenceUrls = context.referenceImageKeys().stream()
                    .map(storageService::presignDownload)
                    .toList();

            KeywordExtraction extraction = aiTextService.generate(
                    keywordPrompt.build(analysisId, context.inputText(), facts(context), referenceUrls),
                    AnalysisPipeline::keywordText);

            List<ExtractedKeyword> keywords = extraction.keywords();
            if (keywords == null || keywords.isEmpty()) {
                // 스키마가 최소 5개를 강제하므로 정상적으로는 오지 않는다.
                // 여기 온다면 입력이 너무 모호했다는 뜻이다. (PRD §8.3)
                throw new AiException(ErrorCode.ANALYSIS_NO_KEYWORD, false, "키워드 0건");
            }
            analysisTx.saveKeywords(analysisId, keywords);
            log.info("키워드 추출 완료 count={}", keywords.size());

        } catch (AiException e) {
            analysisTx.markFailed(analysisId, e.errorCode());
        } catch (BusinessException e) {
            // 저장하는 사이 좀비 정리가 먼저 실패로 돌린 경우. 이미 종료 상태라
            // markFailed는 아무것도 바꾸지 않고, 원래 실패 사유가 보존된다.
            log.info("키워드 저장 무산 code={}", e.errorCode().name());
            analysisTx.markFailed(analysisId, e.errorCode());
        } catch (RuntimeException e) {
            log.error("키워드 추출 중 예상 못 한 오류", e);
            analysisTx.markFailed(analysisId, ErrorCode.AI_PROVIDER_ERROR);
        }
    }

    // ------------------------------------------------------------ 결과 생성

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public void runResultGeneration(UUID analysisId) {
        AnalysisContext context = analysisTx.loadContext(analysisId).orElse(null);
        if (context == null) {
            log.warn("결과 생성 중단 — 분석 또는 프로필이 없다");
            return;
        }
        if (context.status() != AnalysisStatus.GENERATING) {
            log.info("결과 생성 건너뜀 — 상태가 GENERATING이 아니다");
            return;
        }
        if (context.selectedKeywords().isEmpty()) {
            log.error("결과 생성 중단 — 선택된 키워드가 없다");
            analysisTx.markFailed(analysisId, ErrorCode.ANALYSIS_INVALID_STATE);
            return;
        }

        try {
            List<String> keywordLines = context.selectedKeywords().stream()
                    .map(AnalysisContext.SelectedKeyword::toPromptLine)
                    .toList();

            PeakResult result = aiTextService.generate(
                    resultPrompt.build(analysisId, context.inputText(), facts(context),
                            keywordLines, context.priorities()),
                    PeakResult::userFacingText,
                    AnalysisPipeline::requireOneChangePerCategory);

            warnIfTruncated(result);

            // 참고 사진이 있어야 무엇을 향해 바꿀지 알 수 있다. 없으면 만들지 않는다.
            boolean willGenerateImage = !context.referenceImageKeys().isEmpty();

            analysisTx.completeWithResult(analysisId, context.userId(), result,
                    orderByPriorities(result, context.priorities()),
                    willGenerateImage ? ImageStatus.PENDING : ImageStatus.SKIPPED);
            log.info("결과 생성 완료");

            // 텍스트 결과는 이미 확정됐다. 이미지는 별도 풀에서 뒤따라간다 —
            // 여기서 기다리면 사용자가 35초를 더 본다. (ARCHITECTURE.md §5.3)
            if (willGenerateImage) {
                imagePipeline.run(analysisId);
            }

        } catch (AiException e) {
            analysisTx.markFailed(analysisId, e.errorCode());
        } catch (BusinessException e) {
            // 분석권 소진 · 상태 불일치. 결과는 저장되지 않았다(같은 트랜잭션에서 롤백).
            analysisTx.markFailed(analysisId, e.errorCode());
        } catch (RuntimeException e) {
            log.error("결과 생성 중 예상 못 한 오류", e);
            analysisTx.markFailed(analysisId, ErrorCode.AI_PROVIDER_ERROR);
        }
    }

    // ------------------------------------------------------------ 후검증 · 정렬

    /**
     * {@code categoryChanges} 3건이 SKIN·BODY·HEALTH 각 1건인지. (ARCHITECTURE.md §6.4)
     *
     * <p>스키마는 "3건"까지만 강제한다. 카테고리 구성은 강제되지 않아 SKIN이 두 번
     * 나오고 HEALTH가 빠질 수 있다. 결과 화면은 3종 칩을 전제하므로 서버가 막는다.
     *
     * <p>{@link GuardrailViolation}을 던지면 {@link AiTextService}가 사유를 붙여
     * 1회 재생성한다.
     */
    private static void requireOneChangePerCategory(PeakResult result) {
        Map<Category, Long> counts = result.categoryChanges().stream()
                .collect(Collectors.groupingBy(c -> c.category(), Collectors.counting()));

        for (Category category : Category.values()) {
            if (counts.getOrDefault(category, 0L) != 1L) {
                throw new GuardrailViolation(
                        "categoryChanges는 SKIN·BODY·HEALTH 각각 정확히 1건이어야 한다. "
                                + "지금은 %s가 %d건이다.".formatted(category.name(),
                                counts.getOrDefault(category, 0L)));
            }
        }
    }

    /**
     * {@code profiles.priorities} 순서로 재정렬한다. (ERD.md §5.5)
     *
     * <p>1순위 카테고리가 결과 화면 맨 위에 온다. AI가 준 순서를 그대로 쓰면
     * 우선순위 지정이 화면에 반영되지 않는다.
     */
    private static List<CategoryChange> orderByPriorities(PeakResult result, List<Category> priorities) {
        Map<Category, String> byCategory = new EnumMap<>(Category.class);
        result.categoryChanges().forEach(c -> byCategory.put(c.category(), c.description()));

        return priorities.stream()
                .map(category -> new CategoryChange(category, byCategory.get(category)))
                .toList();
    }

    /**
     * 짧은 라벨 3종이 상한에 걸려 잘렸는지 살핀다.
     *
     * <p>결과 화면의 칩 자리라 문장이 들어오면 중간에서 끊긴다. 실패시키지 않고
     * 로그로만 남긴다 — 프롬프트 회귀를 눈에 보이게 하는 것이 목적이다.
     */
    private void warnIfTruncated(PeakResult result) {
        truncationDetector.inspect(AiStage.RESULT_GENERATION, "keepPoints",
                JsonSchemas.SHORT_LABEL_MAX, result.keepPoints());
        truncationDetector.inspect(AiStage.RESULT_GENERATION, "emphasizePoints",
                JsonSchemas.SHORT_LABEL_MAX, result.emphasizePoints());
        truncationDetector.inspect(AiStage.RESULT_GENERATION, "changeIntensity",
                JsonSchemas.SHORT_LABEL_MAX, result.changeIntensity());
    }

    /** 가드레일 검사 대상. 사용자가 화면에서 읽게 될 문자열만 모은다. */
    private static String keywordText(KeywordExtraction extraction) {
        if (extraction.keywords() == null) {
            return "";
        }
        return extraction.keywords().stream()
                .map(k -> k.label() + '\n' + k.reason())
                .collect(Collectors.joining("\n"));
    }

    private static String facts(AnalysisContext context) {
        return ProfileFacts.render(context.priorities(), context.heightCm(), context.weightKg(),
                context.sleepHours(), context.inbody(), context.profileSummary());
    }
}
