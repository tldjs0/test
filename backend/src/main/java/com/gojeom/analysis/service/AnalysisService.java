package com.gojeom.analysis.service;

import com.gojeom.analysis.AnalysisEvents;
import com.gojeom.analysis.dto.AnalysisDtos.AnalysisAcceptedResponse;
import com.gojeom.analysis.dto.AnalysisDtos.AnalysisCreateRequest;
import com.gojeom.analysis.dto.AnalysisDtos.AnalysisStatusResponse;
import com.gojeom.analysis.dto.AnalysisDtos.KeywordItem;
import com.gojeom.analysis.dto.AnalysisDtos.KeywordListResponse;
import com.gojeom.analysis.dto.AnalysisDtos.KeywordSelectionRequest;
import com.gojeom.analysis.dto.AnalysisDtos.ResultResponse;
import com.gojeom.analysis.entity.Analysis;
import com.gojeom.analysis.entity.AnalysisKeyword;
import com.gojeom.analysis.entity.AnalysisReferenceImage;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.repository.AnalysisKeywordRepository;
import com.gojeom.analysis.repository.AnalysisReferenceImageRepository;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.enums.ImageStatus;
import com.gojeom.common.enums.ResultViewState;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.storage.StorageService;
import com.gojeom.storage.UploadPurpose;
import com.gojeom.subscription.entity.Subscription;
import com.gojeom.subscription.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고점 분석의 동기 경로 — 생성 · 폴링 · 키워드 조회/확정 · 결과 조회. (API.md §6.4)
 *
 * <p>AI 호출은 여기 없다. 전부 {@link AnalysisPipeline}이 비동기로 처리한다.
 * 이 클래스의 메서드는 모두 밀리초 단위로 끝나야 한다.
 *
 * <p>모든 진입부에서 <b>소유권을 먼저 확인</b>한다. 남의 분석은 404가 아니라
 * {@code 403 FORBIDDEN_RESOURCE}다. (ARCHITECTURE.md L-5 · §9)
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    /** 키워드 선택 개수. (PRD F-06 · API.md §6.4) */
    private static final int MIN_SELECT = 1;
    private static final int MAX_SELECT = 4;

    /** 분석권이 아직 차감되지 않은 비종료 상태. 사용자당 동시에 하나만 허용한다. */
    private static final List<AnalysisStatus> ACTIVE_ANALYSIS_STATUSES = List.of(
            AnalysisStatus.CREATED,
            AnalysisStatus.EXTRACTING,
            AnalysisStatus.KEYWORDS_READY,
            AnalysisStatus.GENERATING);

    private final ResultAssembler resultAssembler;
    private final AnalysisRepository analysisRepository;
    private final AnalysisKeywordRepository keywordRepository;
    private final AnalysisReferenceImageRepository referenceImageRepository;
    private final AnalysisResultRepository resultRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------ 생성

    /**
     * 분석 생성 → {@code 202}.
     *
     * <p><b>분석권을 여기서 차감하지 않는다.</b> 결과 생성이 성공한 시점에 차감한다.
     * (PRD O-7 · API.md §6.4)
     */
    @Transactional
    public AnalysisAcceptedResponse create(UUID userId, AnalysisCreateRequest request) {
        String inputText = request.inputText().trim();
        if (inputText.length() < 10) {
            // 공백만 채워 길이를 맞춘 입력을 걸러낸다. DB CHECK도 같은 범위를 본다.
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("inputText", "10자 이상 적어주세요."));
        }

        // 사용자별 구독 행을 잠가 동시 생성 요청을 직렬화한다. 잠금 없이
        // exists 검사만 하면 두 요청이 모두 "진행 중 분석 없음"을 보고 AI 작업을 만든다.
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ANALYSIS_CREDIT));
        if (!subscription.canAnalyze(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.NO_ANALYSIS_CREDIT);
        }
        analysisRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId, ACTIVE_ANALYSIS_STATUSES)
                .ifPresent(active -> {
                    throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE,
                            Map.of(
                                    "analysisId", active.getId(),
                                    "status", active.getStatus(),
                                    "message", "진행 중인 분석을 먼저 완료해주세요."));
                });

        Profile profile = profileRepository.findByUserIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_REQUIRED));
        if (profile.getPhotoKey() == null) {
            // 사진을 지운 프로필로는 새 분석을 시작할 수 없다. (ERD.md §3.3 · V3 마이그레이션)
            throw new BusinessException(ErrorCode.PROFILE_REQUIRED,
                    Map.of("photo", "사진을 먼저 등록해주세요."));
        }

        // 클라이언트가 남의 경로 key를 보낼 수 있다. 저장 전에 다시 확인한다. (§8)
        List<String> referenceKeys = List.copyOf(new LinkedHashSet<>(request.safeReferenceImageKeys()));
        referenceKeys.forEach(key -> storageService.validateUploadedImage(
                key, UploadPurpose.REFERENCE_IMAGE, userId));

        UUID retriedFrom = resolveRetriedFrom(userId, request.retriedFrom());

        Analysis analysis = analysisRepository.save(
                Analysis.create(userId, profile.getId(), inputText, retriedFrom));

        for (int i = 0; i < referenceKeys.size(); i++) {
            referenceImageRepository.save(
                    AnalysisReferenceImage.of(analysis.getId(), referenceKeys.get(i), i));
        }

        eventPublisher.publishEvent(new AnalysisEvents.AnalysisCreated(analysis.getId()));

        // 커밋 직후 파이프라인이 EXTRACTING으로 올린다. 프론트는 이 상태를 기대하면 된다.
        return new AnalysisAcceptedResponse(analysis.getId(), AnalysisStatus.EXTRACTING,
                AnalysisProgress.pollAfterMs(AnalysisStatus.EXTRACTING));
    }

    /** "새로 분석하기"의 원본. 남의 분석을 가리킬 수 없다. (PRD R-1) */
    private UUID resolveRetriedFrom(UUID userId, UUID retriedFrom) {
        if (retriedFrom == null) {
            return null;
        }
        findOwned(userId, retriedFrom);
        return retriedFrom;
    }

    // ------------------------------------------------------------ 폴링

    @Transactional(readOnly = true)
    public AnalysisStatusResponse getStatus(UUID userId, UUID analysisId) {
        Analysis analysis = findOwned(userId, analysisId);
        AnalysisStatus status = analysis.getStatus();

        ImageStatus imageStatus = resultRepository.findByAnalysisId(analysisId)
                .map(AnalysisResult::getImageStatus)
                // 결과 행이 생기기 전에는 확정할 수 없다. 이미지 생성 단계가 없는
                // 현재는 결과가 생겨도 SKIPPED이므로 같은 값을 미리 내려준다.
                .orElse(ImageStatus.SKIPPED);

        return new AnalysisStatusResponse(
                analysisId,
                status,
                imageStatus,
                AnalysisProgress.percent(status),
                AnalysisProgress.message(status, analysis.getFailureCode()),
                analysis.getFailureCode(),
                AnalysisProgress.pollAfterMs(status));
    }

    // ------------------------------------------------------------ 키워드

    @Transactional(readOnly = true)
    public KeywordListResponse getKeywords(UUID userId, UUID analysisId) {
        findOwned(userId, analysisId);

        List<AnalysisKeyword> keywords = keywordRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysisId);
        if (keywords.isEmpty()) {
            // 아직 추출 전이거나 실패했다. 없는 목록을 빈 배열로 내려주면
            // 프론트가 "0개 추천"으로 오해한다.
            throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE);
        }

        return new KeywordListResponse(analysisId, MIN_SELECT, MAX_SELECT,
                keywords.stream()
                        .map(k -> new KeywordItem(k.getId(), k.getLabel(), k.getReason(),
                                k.getCategory(), k.getDisplayOrder()))
                        .toList());
    }

    /**
     * 키워드 확정 → 결과 생성 시작 → {@code 202}.
     *
     * <p>{@code status != KEYWORDS_READY}면 {@code 409 ANALYSIS_INVALID_STATE}다.
     * 상태 전이 판정은 {@link Analysis#markGenerating()}이 직접 한다.
     */
    @Transactional
    public AnalysisAcceptedResponse selectKeywords(UUID userId, UUID analysisId,
                                                   KeywordSelectionRequest request) {
        Analysis analysis = findOwned(userId, analysisId);

        List<UUID> requested = request.keywordIds();
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(requested);
        if (unique.size() != requested.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("keywordIds", "같은 키워드를 두 번 고를 수 없어요."));
        }

        Map<UUID, AnalysisKeyword> byId = new LinkedHashMap<>();
        keywordRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysisId)
                .forEach(k -> byId.put(k.getId(), k));
        if (!byId.keySet().containsAll(unique)) {
            // 다른 분석의 키워드 ID를 섞어 보낸 경우다.
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("keywordIds", "이 분석의 키워드가 아니에요."));
        }

        analysis.markGenerating();
        unique.forEach(id -> byId.get(id).select());

        eventPublisher.publishEvent(new AnalysisEvents.KeywordsSelected(analysisId));

        return new AnalysisAcceptedResponse(analysisId, AnalysisStatus.GENERATING,
                AnalysisProgress.pollAfterMs(AnalysisStatus.GENERATING));
    }

    // ------------------------------------------------------------ 결과

    @Transactional(readOnly = true)
    public ResultResponse getResult(UUID userId, UUID analysisId) {
        Analysis analysis = findOwned(userId, analysisId);
        if (analysis.getStatus() != AnalysisStatus.DONE) {
            throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE);
        }
        AnalysisResult result = resultRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 서랍 상세와 같은 조립기를 쓴다. 두 응답이 갈라지면 프론트가 결과 화면
        // 컴포넌트를 재사용할 수 없다. (API.md §6.5)
        return resultAssembler.assemble(result, ResultViewState.FRESH);
    }

    /**
     * 분석의 결과 엔티티. 서랍 저장({@code POST .../result/save})이 쓴다.
     *
     * @throws BusinessException 남의 분석이면 403, 아직 완료 전이면 409
     */
    @Transactional(readOnly = true)
    public AnalysisResult requireCompletedResult(UUID userId, UUID analysisId) {
        Analysis analysis = findOwned(userId, analysisId);
        if (analysis.getStatus() != AnalysisStatus.DONE) {
            throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE);
        }
        return resultRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    // ------------------------------------------------------------ 공통

    /**
     * 소유권 확인. 없으면 404, 남의 것이면 403이다.
     *
     * <p>403을 404로 감추지 않는 이유 — API.md §2가 타인 리소스 접근을
     * {@code FORBIDDEN_RESOURCE}로 규정했고, 프론트가 두 경우에 다른 화면을 띄운다.
     */
    private Analysis findOwned(UUID userId, UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!analysis.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        return analysis;
    }
}
