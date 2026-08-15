package com.gojeom.analysis.repository;

import com.gojeom.analysis.entity.Analysis;
import com.gojeom.common.enums.AnalysisStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    List<Analysis> findByUserId(UUID userId);

    Optional<Analysis> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, Collection<AnalysisStatus> statuses);

    /**
     * 좀비 분석 정리. (ARCHITECTURE.md §5.4)
     *
     * <p>애플리케이션이 재시작되면 진행 중이던 {@code @Async} 작업은 사라지고
     * DB에는 {@code EXTRACTING}인 행만 남는다. 클라이언트는 영영 오지 않을 응답을
     * 기다린다. 이 쿼리가 그 행들을 실패로 정리한다.
     *
     * <p><b>{@code KEYWORDS_READY}는 대상이 아니다.</b> 사용자가 키워드를 고르는
     * 동안 머무는 상태라 시간 제한을 걸면 멀쩡한 분석이 죽는다.
     * ({@link AnalysisStatus#isInProgress()}와 같은 3종)
     *
     * <p><b>분석권은 차감하지 않는다.</b> 결과를 못 받았기 때문이다. (PRD §8.3)
     *
     * <p>{@code updatedAt}을 직접 넣는 이유 — 벌크 UPDATE는 영속성 컨텍스트를 거치지
     * 않아 Auditing이 동작하지 않는다. 빠뜨리면 갱신 시각이 과거에 머물러
     * 다음 스윕에서 같은 행을 또 집는다.
     *
     * @return 실패로 전환된 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Analysis a
               SET a.status = com.gojeom.common.enums.AnalysisStatus.FAILED,
                   a.failureCode = :failureCode,
                   a.updatedAt = :now
             WHERE a.status IN (com.gojeom.common.enums.AnalysisStatus.CREATED,
                                com.gojeom.common.enums.AnalysisStatus.EXTRACTING,
                                com.gojeom.common.enums.AnalysisStatus.GENERATING)
               AND a.updatedAt < :threshold
            """)
    int failStale(@Param("threshold") OffsetDateTime threshold,
                  @Param("now") OffsetDateTime now,
                  @Param("failureCode") String failureCode);
}
