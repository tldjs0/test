package com.gojeom.routine.repository;

import com.gojeom.routine.entity.RoutineTask;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineTaskRepository extends JpaRepository<RoutineTask, UUID> {

    List<RoutineTask> findByRoutineIdOrderByScheduledDateAscTitleAsc(UUID routineId);

    /**
     * 목표별 완료/전체 집계.
     *
     * <p>태스크 행을 전부 끌어와 자바에서 세지 않는다. 서랍 목록은 목표 수만큼
     * 진행률이 필요해, 목표마다 태스크를 로드하면 N+1이 된다.
     */
    @Query("""
            SELECT t.routineId AS routineId,
                   COUNT(t)    AS total,
                   SUM(CASE WHEN t.status = com.gojeom.common.enums.TaskStatus.DONE
                            THEN 1L ELSE 0L END) AS done
              FROM RoutineTask t
             WHERE t.routineId IN :routineIds
             GROUP BY t.routineId
            """)
    List<ProgressRow> countProgressByRoutineIds(@Param("routineIds") Collection<UUID> routineIds);

    long countByRoutineId(UUID routineId);

    /** 집계 결과 투영. */
    interface ProgressRow {
        UUID getRoutineId();

        long getTotal();

        long getDone();
    }
}
