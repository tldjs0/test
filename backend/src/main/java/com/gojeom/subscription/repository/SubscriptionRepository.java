package com.gojeom.subscription.repository;

import com.gojeom.subscription.entity.Subscription;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    /** 같은 사용자의 분석 생성 요청을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId")
    Optional<Subscription> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * 분석권 1회 차감.
     *
     * <p><b>조회 후 저장하지 않는다.</b> 동시 요청이 같은 잔여값을 읽고 각자 차감하면
     * 중복 사용이 발생한다. 단일 UPDATE로 원자성을 확보한다. (ARCHITECTURE.md §7)
     *
     * @return 1이면 차감 성공, 0이면 잔여 없음
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Subscription s
               SET s.analysisCredits = s.analysisCredits - 1
             WHERE s.userId = :userId
               AND s.analysisCredits > 0
            """)
    int consumeCredit(@Param("userId") UUID userId);
}
