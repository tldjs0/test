package com.gojeom.subscription.entity;

import com.gojeom.common.enums.SubscriptionPlan;
import com.gojeom.common.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 구독과 분석권.
 *
 * <p><b>분석권 차감은 이 엔티티를 통해 하지 않는다.</b> 동시 요청으로 중복 차감되는 것을
 * 막기 위해 리포지토리의 단일 UPDATE 문으로 처리한다. (ARCHITECTURE.md §7)
 *
 * <p>테이블에 {@code created_at}이 없어 베이스 엔티티를 상속하지 않는다.
 * 생성 시각은 {@code startedAt}이 대신한다.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    /** 가입 시 제공되는 무료 분석권. (PRD F-12) */
    private static final short TRIAL_CREDITS = 1;

    /** 무료 체험 기간. */
    private static final int TRIAL_MONTHS = 1;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 10)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private SubscriptionStatus status;

    @Column(name = "analysis_credits", nullable = false)
    private short analysisCredits;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    private Subscription(UUID userId, SubscriptionPlan plan, SubscriptionStatus status,
                         short analysisCredits, OffsetDateTime startedAt, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.plan = plan;
        this.status = status;
        this.analysisCredits = analysisCredits;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /** 회원가입 시 자동 발급되는 무료 체험. */
    public static Subscription startTrial(UUID userId, OffsetDateTime now) {
        return new Subscription(
                userId,
                SubscriptionPlan.TRIAL,
                SubscriptionStatus.ACTIVE,
                TRIAL_CREDITS,
                now,
                now.plusMonths(TRIAL_MONTHS));
    }

    /** 분석을 시작할 수 있는지. 만료됐거나 분석권이 없으면 false. */
    public boolean canAnalyze(OffsetDateTime now) {
        return status == SubscriptionStatus.ACTIVE
                && analysisCredits > 0
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    /**
     * 목표 생성 가능 여부.
     *
     * <p>분석권과 무관하다. 이미 저장된 결과로 목표를 만드는 것은 추가 분석이 아니다.
     */
    public boolean canCreateRoutine(OffsetDateTime now) {
        return status == SubscriptionStatus.ACTIVE
                && (expiresAt == null || expiresAt.isAfter(now));
    }
}
