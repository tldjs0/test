package com.gojeom.routine.entity;

import com.gojeom.common.entity.BaseCreatedEntity;
import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.RoutineSourceType;
import com.gojeom.common.enums.RoutineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 목표. 생성 경로 2종을 한 테이블이 담는다. (ERD.md §3.9 · PRD F-09)
 *
 * <p><b>정적 팩터리를 경로별로 나눈 이유</b> — {@code ck_routine_source} CHECK 제약이
 * 경로마다 다른 컬럼 조합을 요구한다. 생성자 하나로 열어두면 잘못된 조합이
 * DB까지 내려가 제약 위반으로 터진다. 팩터리에서 조합을 고정해 애초에 만들 수 없게 한다.
 */
@Entity
@Table(name = "routines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RoutineSourceType sourceType;

    /** {@code FROM_ANALYSIS}일 때만 값. */
    @Column(name = "analysis_result_id")
    private UUID analysisResultId;

    /** {@code STANDALONE}일 때만 값. 카테고리는 태스크 단위 속성이기도 하다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 10)
    private Category category;

    @Column(name = "duration_weeks")
    private Short durationWeeks;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutineStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** {@code STANDALONE}만 값을 갖는다. 경로 A는 기간 개념이 없다. (ERD.md E-3) */
    @Column(name = "end_date")
    private LocalDate endDate;

    private Routine(UUID userId, RoutineSourceType sourceType, UUID analysisResultId,
                    Category category, Short durationWeeks, String title,
                    LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.sourceType = sourceType;
        this.analysisResultId = analysisResultId;
        this.category = category;
        this.durationWeeks = durationWeeks;
        this.title = title;
        this.status = RoutineStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 경로 A — 여러 카테고리에 걸친 목표 1개. {@code category}·{@code durationWeeks}는 NULL이다. */
    public static Routine fromAnalysis(UUID userId, UUID analysisResultId, String title,
                                       LocalDate startDate) {
        return new Routine(userId, RoutineSourceType.FROM_ANALYSIS, analysisResultId,
                null, null, title, startDate, null);
    }

    /**
     * 경로 B — 카테고리당 목표 1개. {@code analysisResultId}는 NULL이다.
     *
     * <p>{@code end_date = start_date + duration_weeks * 7 - 1} (ERD.md §3.9)
     */
    public static Routine standalone(UUID userId, Category category, int durationWeeks,
                                     String title, LocalDate startDate) {
        return new Routine(userId, RoutineSourceType.STANDALONE, null,
                category, (short) durationWeeks, title, startDate,
                startDate.plusDays((long) durationWeeks * 7 - 1));
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    /**
     * 태스크 완료율에 맞춰 상태를 맞춘다.
     *
     * <p>전부 끝나면 {@code COMPLETED}, 다시 체크를 풀면 {@code ACTIVE}로 되돌린다.
     * 서랍의 "현재 진행중인 목표" 섹션이 {@code ACTIVE}로 판정하므로, 끝난 목표가
     * 계속 진행 중으로 남지 않게 하려면 이 전이가 필요하다. (ERD.md §3.8)
     *
     * <p>사용자가 취소한 목표({@code CANCELED})는 건드리지 않는다.
     */
    public void syncStatus(long doneCount, long totalCount) {
        if (status == RoutineStatus.CANCELED) {
            return;
        }
        this.status = totalCount > 0 && doneCount == totalCount
                ? RoutineStatus.COMPLETED
                : RoutineStatus.ACTIVE;
    }
}
