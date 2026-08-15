package com.gojeom.drawer;

import com.gojeom.analysis.dto.AnalysisDtos.ResultResponse;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.analysis.service.AnalysisService;
import com.gojeom.analysis.service.ResultAssembler;
import com.gojeom.common.enums.ResultViewState;
import com.gojeom.common.enums.RoutineSourceType;
import com.gojeom.common.enums.RoutineStatus;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.drawer.dto.SavedResultDtos.DrawerItem;
import com.gojeom.drawer.dto.SavedResultDtos.DrawerResponse;
import com.gojeom.drawer.dto.SavedResultDtos.SaveResponse;
import com.gojeom.drawer.entity.SavedResult;
import com.gojeom.drawer.repository.SavedResultRepository;
import com.gojeom.routine.entity.Routine;
import com.gojeom.routine.repository.RoutineRepository;
import com.gojeom.routine.repository.RoutineTaskRepository;
import com.gojeom.storage.StorageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서랍. (API.md §6.5 · PRD F-08 · D3-1)
 *
 * <p><b>자동 저장은 없다.</b> 사용자가 "서랍에 결과 저장하기"를 눌렀을 때만 저장된다.
 * (PRD R-4)
 */
@Service
@RequiredArgsConstructor
public class SavedResultService {

    /** "최근 분석 결과" 섹션 기준. (ERD.md §3.8) */
    private static final int RECENT_MONTHS = 1;

    private final SavedResultRepository savedResultRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final RoutineRepository routineRepository;
    private final RoutineTaskRepository routineTaskRepository;
    private final AnalysisService analysisService;
    private final ResultAssembler resultAssembler;
    private final StorageService storageService;

    // ------------------------------------------------------------ 저장

    /**
     * 결과를 서랍에 담는다.
     *
     * <p>같은 결과를 두 번 저장하려 하면 {@code 409}다. {@code ux_saved_result}
     * 유니크 인덱스가 DB에서도 막지만, 제약 위반을 500으로 흘리지 않고 여기서 잡는다.
     */
    @Transactional
    public SaveResponse save(UUID userId, UUID analysisId) {
        AnalysisResult result = analysisService.requireCompletedResult(userId, analysisId);

        if (savedResultRepository.existsByAnalysisResultId(result.getId())) {
            throw alreadySaved();
        }
        SavedResult saved;
        try {
            // exists 검사 뒤 동시에 들어온 요청도 ux_saved_result가 막는다.
            // 즉시 flush해야 트랜잭션 커밋 시점이 아니라 여기서 충돌을 409로 바꿀 수 있다.
            saved = savedResultRepository.saveAndFlush(SavedResult.of(userId, result.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw alreadySaved();
        }
        return new SaveResponse(saved.getId(), saved.getSavedAt());
    }

    // ------------------------------------------------------------ 목록

    /**
     * 서랍 3섹션. (시안 19)
     *
     * <table>
     *   <caption>섹션 판정</caption>
     *   <tr><td>inProgress</td><td>진행 중({@code ACTIVE}) 목표가 연결된 결과</td></tr>
     *   <tr><td>recent</td><td>저장 후 1개월 이내</td></tr>
     *   <tr><td>all</td><td>전부</td></tr>
     * </table>
     *
     * <p>섹션마다 쿼리를 따로 돌리지 않는다. 한 번 읽어 자바에서 가른다 — 세 섹션이
     * 같은 모수의 부분집합이라 DB를 세 번 때릴 이유가 없다.
     */
    @Transactional(readOnly = true)
    public DrawerResponse list(UUID userId) {
        List<SavedResult> saved = savedResultRepository.findByUserIdOrderBySavedAtDesc(userId);
        if (saved.isEmpty()) {
            return new DrawerResponse(List.of(), List.of(), List.of());
        }

        List<UUID> resultIds = saved.stream().map(SavedResult::getAnalysisResultId).toList();
        Map<UUID, AnalysisResult> results = analysisResultRepository.findAllById(resultIds).stream()
                .collect(Collectors.toMap(AnalysisResult::getId, r -> r));

        // 진행 중인 목표가 연결된 결과 + 그 목표의 진행률
        List<Routine> activeRoutines = routineRepository.findBySourceTypeAndStatusAndAnalysisResultIdIn(
                RoutineSourceType.FROM_ANALYSIS, RoutineStatus.ACTIVE, resultIds);
        Map<UUID, Double> rateByResultId = progressByResultId(activeRoutines);
        Set<UUID> inProgressResultIds = rateByResultId.keySet();

        OffsetDateTime recentFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(RECENT_MONTHS);

        List<DrawerItem> all = saved.stream()
                .filter(s -> results.containsKey(s.getAnalysisResultId()))
                .map(s -> toItem(s, results.get(s.getAnalysisResultId()),
                        rateByResultId.get(s.getAnalysisResultId())))
                .toList();

        Map<UUID, SavedResult> savedById = saved.stream()
                .collect(Collectors.toMap(SavedResult::getId, s -> s, (a, b) -> a, LinkedHashMap::new));

        List<DrawerItem> inProgress = all.stream()
                .filter(item -> inProgressResultIds.contains(item.resultId()))
                .toList();
        List<DrawerItem> recent = all.stream()
                .filter(item -> savedById.get(item.savedResultId()).getSavedAt().isAfter(recentFrom))
                .toList();

        return new DrawerResponse(inProgress, recent, all);
    }

    /**
     * 결과별 진행률. 목표가 여러 개면 태스크를 합산한다.
     *
     * <p>목표마다 태스크를 로드하지 않고 집계 쿼리 한 번으로 끝낸다. 서랍은 목록
     * 화면이라 N+1이 그대로 체감된다.
     */
    private Map<UUID, Double> progressByResultId(List<Routine> routines) {
        if (routines.isEmpty()) {
            return Map.of();
        }
        Map<UUID, long[]> totals = new HashMap<>();
        Map<UUID, UUID> resultIdByRoutineId = routines.stream()
                .collect(Collectors.toMap(Routine::getId, Routine::getAnalysisResultId));

        routineTaskRepository.countProgressByRoutineIds(resultIdByRoutineId.keySet())
                .forEach(row -> {
                    UUID resultId = resultIdByRoutineId.get(row.getRoutineId());
                    long[] acc = totals.computeIfAbsent(resultId, k -> new long[2]);
                    acc[0] += row.getDone();
                    acc[1] += row.getTotal();
                });

        Map<UUID, Double> rates = new HashMap<>();
        // 태스크가 0건인 목표도 "진행 중"이다. 진행률만 0.0으로 둔다.
        routines.forEach(r -> rates.put(r.getAnalysisResultId(), 0.0));
        totals.forEach((resultId, acc) -> rates.put(resultId, rate(acc[0], acc[1])));
        return rates;
    }

    private DrawerItem toItem(SavedResult saved, AnalysisResult result, Double progressRate) {
        return new DrawerItem(
                saved.getId(),
                result.getId(),
                // 이미지가 없는 결과(SKIPPED·FAILED)는 null이다. 프론트가 브랜드
                // placeholder를 띄운다. (API.md §6.5)
                storageService.presignDownload(result.getComparisonImageKey()),
                result.getTitle(),
                result.getCreatedAt(),
                progressRate);
    }

    // ------------------------------------------------------------ 상세 · 삭제

    /**
     * 서랍 상세. {@code GET /analyses/{id}/result}와 <b>동일한 스키마</b>이며
     * {@code viewState}만 {@code SAVED}다. (API.md §6.5)
     */
    @Transactional(readOnly = true)
    public ResultResponse detail(UUID userId, UUID savedResultId) {
        SavedResult saved = findOwned(userId, savedResultId);
        AnalysisResult result = analysisResultRepository.findById(saved.getAnalysisResultId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return resultAssembler.assemble(result, ResultViewState.SAVED);
    }

    /**
     * 서랍에서 뺀다.
     *
     * <p>분석과 결과 자체는 지우지 않는다. 서랍에서 내리는 것과 분석을 삭제하는 것은
     * 다른 동작이다 — 분석 전체 삭제는 {@code DELETE /analyses}가 따로 있다.
     */
    @Transactional
    public void delete(UUID userId, UUID savedResultId) {
        savedResultRepository.delete(findOwned(userId, savedResultId));
    }

    private SavedResult findOwned(UUID userId, UUID savedResultId) {
        SavedResult saved = savedResultRepository.findById(savedResultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!saved.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        return saved;
    }

    private BusinessException alreadySaved() {
        return new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE,
                Map.of("savedResult", "이미 서랍에 저장된 결과예요."));
    }

    /** 소수점 첫째 자리까지. (API.md 예시 {@code 62.5} · {@code 40.0}) */
    static double rate(long done, long total) {
        return total == 0 ? 0.0 : Math.round(done * 1000.0 / total) / 10.0;
    }
}
