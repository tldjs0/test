package com.gojeom.routine.repository;

import com.gojeom.common.enums.RoutineSourceType;
import com.gojeom.common.enums.RoutineStatus;
import com.gojeom.routine.entity.Routine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {

    List<Routine> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 서랍의 "현재 진행중인 목표" 판정용. (ERD.md §3.8)
     *
     * <p>{@code source_type='FROM_ANALYSIS'} AND {@code status='ACTIVE'}인 목표가
     * 연결된 결과만 그 섹션에 들어간다.
     */
    List<Routine> findBySourceTypeAndStatusAndAnalysisResultIdIn(
            RoutineSourceType sourceType, RoutineStatus status, Collection<UUID> analysisResultIds);

    List<Routine> findByAnalysisResultIdIn(Collection<UUID> analysisResultIds);
}
