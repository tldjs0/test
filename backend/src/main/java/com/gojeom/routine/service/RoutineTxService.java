package com.gojeom.routine.service;

import com.gojeom.ai.dto.AiPayloads.PlannedRoutine;
import com.gojeom.ai.dto.AiPayloads.PlannedTask;
import com.gojeom.ai.prompt.ProfileFacts;
import com.gojeom.analysis.entity.Analysis;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.entity.CategoryChange;
import com.gojeom.analysis.entity.DailyCare;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.common.enums.Category;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.routine.dto.RoutineDtos.RoutineItem;
import com.gojeom.routine.dto.RoutineDtos.RoutineSummary;
import com.gojeom.routine.entity.Routine;
import com.gojeom.routine.entity.RoutineTask;
import com.gojeom.routine.repository.RoutineRepository;
import com.gojeom.routine.repository.RoutineTaskRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표 생성의 <b>짧은 트랜잭션</b>만 담당하는 빈. (ARCHITECTURE.md §5.2 · L-4)
 *
 * <p>{@link RoutineService}와 분리되어 있어야 한다. 같은 클래스 안에서 호출하면
 * 프록시를 타지 않아 트랜잭션이 걸리지 않는다. 분석 파이프라인의
 * {@code AnalysisTxService}와 같은 구조다.
 */
@Service
@RequiredArgsConstructor
public class RoutineTxService {

    private final RoutineRepository routineRepository;
    private final RoutineTaskRepository routineTaskRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final ProfileRepository profileRepository;

    // ------------------------------------------------------------ 컨텍스트 로드

    /**
     * 경로 A — 결과 소유권 확인 + 프롬프트 재료 수집.
     *
     * <p><b>우선순위는 분석 시점이 아니라 현재 활성 프로필에서 읽는다.</b> 사용자가
     * 그사이 우선순위를 바꿨다면 새로 만드는 목표에는 바뀐 값이 반영되어야 한다.
     * (PRD F-09 "두 경로 모두 profiles.priorities를 가중치로 반영한다")
     */
    @Transactional(readOnly = true)
    public RoutineCreationContext loadFromAnalysis(UUID userId, UUID analysisResultId) {
        AnalysisResult result = analysisResultRepository.findById(analysisResultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Analysis analysis = analysisRepository.findById(result.getAnalysisId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!analysis.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
        Profile profile = activeProfile(userId);

        return new RoutineCreationContext(analysisResultId, profile.getPriorities(),
                facts(profile), digest(result));
    }

    /** 경로 B — 분석 결과가 없으므로 프로필만 있으면 된다. */
    @Transactional(readOnly = true)
    public RoutineCreationContext loadStandalone(UUID userId) {
        Profile profile = activeProfile(userId);
        return new RoutineCreationContext(null, profile.getPriorities(), facts(profile), null);
    }

    private Profile activeProfile(UUID userId) {
        return profileRepository.findByUserIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_REQUIRED));
    }

    private String facts(Profile profile) {
        return ProfileFacts.render(profile.getPriorities(), profile.getHeightCm(),
                profile.getWeightKg(), profile.getSleepHours(), profile.getInbody(),
                profile.getAnalysisSummary());
    }

    /** 결과지를 프롬프트가 읽을 수 있는 텍스트로 펼친다. */
    private String digest(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(result.getTitle()).append('\n');
        sb.append("요약: ").append(result.getSummary()).append('\n');
        sb.append("유지할 점: ").append(String.join(", ", result.getKeepPoints())).append('\n');
        sb.append("강조할 점: ").append(String.join(", ", result.getEmphasizePoints())).append('\n');
        sb.append("변화 강도: ").append(String.join(", ", result.getChangeIntensity())).append('\n');
        sb.append("\n카테고리별 변화\n");
        for (CategoryChange change : result.getCategoryChanges()) {
            sb.append("- [").append(change.category().name()).append("] ")
                    .append(change.description()).append('\n');
        }
        sb.append("\n오늘 해볼 관리\n");
        for (DailyCare care : result.getDailyCares()) {
            sb.append("- ").append(care.title()).append(" — ").append(care.description()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 저장

    /**
     * 경로 A 저장 — 목표 1개 + 태스크.
     *
     * <p><b>모든 태스크를 {@code startDate} 하루에 배치한다.</b> 경로 A에는 기간
     * 개념이 없기 때문이다(ERD.md E-3 미결). ERD가 제시한 "AI 출력 태스크를
     * start_date 기준으로 배치"를 그대로 따랐고, API.md §6.6의 예시
     * ({@code progress: {done: 2, total: 5}})와도 개수가 맞는다.
     */
    @Transactional
    public RoutineSummary persistFromAnalysis(UUID userId, UUID analysisResultId, String title,
                                              List<PlannedTask> tasks, LocalDate startDate) {
        Routine routine = routineRepository.save(
                Routine.fromAnalysis(userId, analysisResultId, title, startDate));

        for (PlannedTask task : tasks) {
            routineTaskRepository.save(RoutineTask.of(routine.getId(), task.category(),
                    task.title(), task.timing(), task.durationLabel(), task.amountLabel(), startDate));
        }
        return summary(routine, tasks.size());
    }

    /**
     * 경로 B 저장 — 카테고리당 목표 1개.
     *
     * <p><b>태스크 묶음을 주 단위로 반복 배치한다.</b> {@code taskCount = 태스크 수 ×
     * durationWeeks}가 되며, API.md §6.6 예시(4주 · {@code taskCount: 24})가 주당 6건
     * 구성과 정확히 맞아떨어진다. 매일 반복하면 4주에 168건이 되어 화면이 무너진다.
     *
     * <p>태스크의 {@code category}는 AI 출력을 믿지 않고 <b>목표의 카테고리로 덮어쓴다.</b>
     * {@code routine_tasks.category}가 목표와 어긋나면 목표 화면의 분류가 깨진다.
     */
    @Transactional
    public List<RoutineSummary> persistStandalone(UUID userId, List<PlannedRoutine> plans,
                                                  Map<Category, Integer> weeksByCategory,
                                                  LocalDate startDate) {
        List<RoutineSummary> summaries = new ArrayList<>();

        for (PlannedRoutine plan : plans) {
            int weeks = weeksByCategory.get(plan.category());
            Routine routine = routineRepository.save(
                    Routine.standalone(userId, plan.category(), weeks, plan.title(), startDate));

            long count = 0;
            for (int week = 0; week < weeks; week++) {
                LocalDate scheduled = startDate.plusWeeks(week);
                for (PlannedTask task : plan.tasks()) {
                    routineTaskRepository.save(RoutineTask.of(routine.getId(), plan.category(),
                            task.title(), task.timing(), task.durationLabel(), task.amountLabel(),
                            scheduled));
                    count++;
                }
            }
            summaries.add(summary(routine, count));
        }
        return summaries;
    }

    private RoutineSummary summary(Routine routine, long taskCount) {
        return new RoutineSummary(routine.getId(), routine.getSourceType(), routine.getCategory(),
                routine.getTitle(), routine.getDurationWeeks(), routine.getStartDate(),
                routine.getEndDate(), taskCount);
    }

    /** 요청 순서를 유지한 채 카테고리 → 기간 맵으로 바꾼다. 중복은 호출부가 이미 걸렀다. */
    static Map<Category, Integer> weeksByCategory(List<RoutineItem> items) {
        Map<Category, Integer> map = new LinkedHashMap<>();
        items.forEach(item -> map.put(item.category(), item.durationWeeks()));
        return map;
    }
}
