package com.gojeom.routine.entity;

import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 목표의 태스크 1건. (ERD.md §3.9)
 *
 * <p>화면 표기는 {@code title} 아래에 {@code timing / durationLabel / amountLabel}을
 * {@code " / "}로 이어 한 줄로 그린다.
 *
 * <pre>
 * 자외선 차단제 바르기
 * 매일 외출 전 / 약 2분 / 4ml          [ ] 완료
 * </pre>
 *
 * <p>{@code created_at} 컬럼이 없어 베이스 엔티티를 상속하지 않는다.
 */
@Entity
@Table(name = "routine_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineTask {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "routine_id", nullable = false)
    private UUID routineId;

    /** 3종이다. 목표가 여러 카테고리에 걸치므로 카테고리는 태스크 단위 속성이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private Category category;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "timing", length = 40)
    private String timing;

    @Column(name = "duration_label", length = 20)
    private String durationLabel;

    @Column(name = "amount_label", length = 20)
    private String amountLabel;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** 알림 시각용. 현재는 채우지 않는다 — 알림 설정 화면이 붙을 때 쓴다. */
    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    private RoutineTask(UUID routineId, Category category, String title, String timing,
                        String durationLabel, String amountLabel, LocalDate scheduledDate) {
        this.routineId = routineId;
        this.category = category;
        this.title = title;
        this.timing = timing;
        this.durationLabel = durationLabel;
        this.amountLabel = amountLabel;
        this.scheduledDate = scheduledDate;
        this.status = TaskStatus.PENDING;
    }

    public static RoutineTask of(UUID routineId, Category category, String title, String timing,
                                 String durationLabel, String amountLabel, LocalDate scheduledDate) {
        return new RoutineTask(routineId, category, title, timing,
                durationLabel, amountLabel, scheduledDate);
    }

    /**
     * 완료 체크 토글.
     *
     * <p>{@code completedAt}은 {@code DONE}일 때만 남긴다. 체크를 풀면 지운다 —
     * 완료 시각이 남아 있는 미완료 태스크는 데이터로서 거짓이다.
     */
    public void changeStatus(TaskStatus next) {
        this.status = next;
        this.completedAt = next == TaskStatus.DONE ? OffsetDateTime.now(ZoneOffset.UTC) : null;
    }
}
