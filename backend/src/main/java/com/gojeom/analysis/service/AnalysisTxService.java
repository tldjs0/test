package com.gojeom.analysis.service;

import com.gojeom.ai.dto.AiPayloads.ExtractedKeyword;
import com.gojeom.ai.dto.AiPayloads.PeakResult;
import com.gojeom.analysis.entity.Analysis;
import com.gojeom.analysis.entity.AnalysisKeyword;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.entity.CategoryChange;
import com.gojeom.analysis.entity.DailyCare;
import com.gojeom.analysis.repository.AnalysisKeywordRepository;
import com.gojeom.analysis.repository.AnalysisReferenceImageRepository;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.analysis.service.AnalysisContext.SelectedKeyword;
import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.enums.ImageStatus;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.subscription.repository.SubscriptionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파이프라인의 <b>짧은 트랜잭션</b>만 담당하는 빈. ★ (ARCHITECTURE.md §5.2)
 *
 * <p>{@link AnalysisPipeline}과 반드시 분리되어 있어야 한다. 같은 클래스 안에서
 * 호출하면 <b>프록시를 타지 않아 트랜잭션이 걸리지 않는다.</b> D2에서 가장 밟기 쉬운
 * 함정이라 클래스를 나누는 것으로 구조에 못박았다.
 *
 * <p>여기 있는 메서드는 전부 밀리초 단위로 끝난다. OpenAI 호출은 이 빈을 거치지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTxService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisKeywordRepository keywordRepository;
    private final AnalysisReferenceImageRepository referenceImageRepository;
    private final AnalysisResultRepository resultRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 파이프라인이 필요한 값을 한 번에 읽어 나간다.
     *
     * <p>분석·프로필·참고사진·선택 키워드를 각각 조회한다. 연관을 엔티티로 묶지 않은
     * 이유는 트랜잭션 밖으로 나가는 값이 명시적이길 원해서다.
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisContext> loadContext(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return Optional.empty();
        }
        Profile profile = profileRepository.findById(analysis.getProfileId()).orElse(null);
        if (profile == null) {
            // 프로필이 사라진 분석은 근거가 없다. 진행하지 않는다.
            return Optional.empty();
        }
        List<String> referenceKeys = referenceImageRepository
                .findByAnalysisIdOrderByDisplayOrderAsc(analysisId).stream()
                .map(image -> image.getImageKey())
                .toList();
        List<SelectedKeyword> selected = keywordRepository
                .findByAnalysisIdAndSelectedTrueOrderByDisplayOrderAsc(analysisId).stream()
                .map(k -> new SelectedKeyword(k.getLabel(), k.getReason(), k.getCategory()))
                .toList();

        return Optional.of(new AnalysisContext(
                analysis.getId(), analysis.getUserId(), analysis.getStatus(), analysis.getInputText(),
                profile.getPriorities(), profile.getHeightCm(), profile.getWeightKg(),
                profile.getSleepHours(), profile.getInbody(), profile.getAnalysisSummary(),
                referenceKeys, selected));
    }

    /**
     * {@code CREATED → EXTRACTING}.
     *
     * @return 이미 좀비 정리에 걸렸거나 상태가 다르면 false. 그때는 파이프라인이 멈춘다
     */
    @Transactional
    public boolean markExtracting(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null || analysis.getStatus() != AnalysisStatus.CREATED) {
            return false;
        }
        analysis.markExtracting();
        return true;
    }

    /** 키워드 저장 + {@code EXTRACTING → KEYWORDS_READY}. */
    @Transactional
    public void saveKeywords(UUID analysisId, List<ExtractedKeyword> keywords) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        for (int i = 0; i < keywords.size(); i++) {
            ExtractedKeyword k = keywords.get(i);
            keywordRepository.save(AnalysisKeyword.of(analysisId, k.label(), k.reason(), k.category(), i + 1));
        }
        analysis.markKeywordsReady();
    }

    /**
     * 결과 저장 + 분석권 차감 + {@code GENERATING → DONE}을 <b>한 트랜잭션</b>에서.
     *
     * <p>"결과는 저장됐는데 차감 안 됨" 또는 그 반대가 생기면 안 된다.
     * (ARCHITECTURE.md §7 · PRD O-7)
     *
     * <p>차감을 먼저 하는 이유 — {@code consumeCredit}은 {@code clearAutomatically}가
     * 걸린 벌크 UPDATE라 영속성 컨텍스트를 비운다. 엔티티를 먼저 로드해두면 그 변경이
     * 사라진다. <b>차감 → 로드 → 저장</b> 순서를 지킨다.
     *
     * @throws BusinessException 잔여 분석권이 없으면 {@code NO_ANALYSIS_CREDIT},
     *                           그사이 상태가 바뀌었으면 {@code ANALYSIS_INVALID_STATE}
     */
    @Transactional
    public void completeWithResult(UUID analysisId, UUID userId, PeakResult payload,
                                   List<CategoryChange> orderedChanges, ImageStatus imageStatus) {
        if (subscriptionRepository.consumeCredit(userId) == 0) {
            throw new BusinessException(ErrorCode.NO_ANALYSIS_CREDIT);
        }
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (analysis.getStatus() != AnalysisStatus.GENERATING) {
            // 좀비 정리가 먼저 실패로 돌렸다. 차감을 되돌리기 위해 예외로 롤백한다.
            throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE);
        }

        List<DailyCare> cares = payload.dailyCares().stream()
                .map(c -> new DailyCare(c.title(), c.description()))
                .toList();

        resultRepository.save(AnalysisResult.create(
                analysisId, payload.title(), payload.summary(),
                payload.keepPoints(), payload.emphasizePoints(), payload.changeIntensity(),
                orderedChanges, cares, imageStatus));

        analysis.markDone();
    }

    /**
     * 비교 이미지를 만들 수 있는 분석인지.
     *
     * <p>참고 사진이 한 장도 없으면 만들지 않는다 — 무엇을 향해 바꿀지 알 수 없다.
     * (PRD F-05 · ERD.md §3.5)
     */
    @Transactional(readOnly = true)
    public boolean hasReferenceImages(UUID analysisId) {
        return !referenceImageRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysisId).isEmpty();
    }

    /**
     * 비교 이미지 생성에 필요한 값 묶음. 트랜잭션 밖으로 나간다.
     *
     * @param photoKey 사용자 사진. 없으면 이미지를 만들 수 없다
     */
    public record ImageContext(
            UUID analysisId,
            UUID userId,
            UUID resultId,
            String photoKey,
            List<String> referenceKeys,
            List<String> keywordLabels) {
    }

    @Transactional(readOnly = true)
    public Optional<ImageContext> loadImageContext(UUID analysisId) {
        AnalysisResult result = resultRepository.findByAnalysisId(analysisId).orElse(null);
        Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (result == null || analysis == null) {
            return Optional.empty();
        }
        Profile profile = profileRepository.findById(analysis.getProfileId()).orElse(null);
        if (profile == null || profile.getPhotoKey() == null) {
            return Optional.empty();
        }
        return Optional.of(new ImageContext(
                analysisId,
                analysis.getUserId(),
                result.getId(),
                profile.getPhotoKey(),
                referenceImageRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysisId).stream()
                        .map(image -> image.getImageKey())
                        .toList(),
                keywordRepository.findByAnalysisIdAndSelectedTrueOrderByDisplayOrderAsc(analysisId).stream()
                        .map(keyword -> keyword.getLabel())
                        .toList()));
    }

    @Transactional
    public void markImageDone(UUID analysisId, String comparisonImageKey) {
        resultRepository.findByAnalysisId(analysisId)
                .ifPresent(result -> result.applyComparisonImage(comparisonImageKey));
    }

    /** 이미지 실패는 <b>분석권 미차감 사유가 아니다.</b> 텍스트 결과는 이미 저장됐다. */
    @Transactional
    public void markImageFailed(UUID analysisId) {
        resultRepository.findByAnalysisId(analysisId)
                .ifPresent(result -> {
                    if (result.markImageFailed()) {
                        log.info("비교 이미지 실패 처리");
                    }
                });
    }

    /**
     * 실패 기록. 이미 끝난 분석은 건드리지 않는다.
     *
     * <p>실패해도 <b>분석권은 차감하지 않는다.</b> 차감은 결과 저장과 같은
     * 트랜잭션에서만 일어나므로, 여기 도달했다는 것은 차감이 없었다는 뜻이다.
     */
    @Transactional
    public void markFailed(UUID analysisId, ErrorCode code) {
        analysisRepository.findById(analysisId).ifPresent(analysis -> {
            if (analysis.markFailed(code)) {
                log.info("분석 실패 처리 code={}", code.name());
            }
        });
    }
}
