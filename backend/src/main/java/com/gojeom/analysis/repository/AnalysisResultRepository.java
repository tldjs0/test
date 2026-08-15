package com.gojeom.analysis.repository;

import com.gojeom.analysis.entity.AnalysisResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

    /** 분석당 1건이다. ({@code ux_results_analysis}) */
    Optional<AnalysisResult> findByAnalysisId(UUID analysisId);

    /** 삭제 시 지울 비교 이미지를 모으는 데 쓴다. */
    java.util.List<AnalysisResult> findByAnalysisIdIn(java.util.Collection<UUID> analysisIds);

    /**
     * 멈춘 이미지 생성 정리. (ARCHITECTURE.md §5.4와 같은 이유)
     *
     * <p>앱이 재시작되면 진행 중이던 이미지 {@code @Async} 작업도 사라진다.
     * {@code PENDING}으로 남으면 프론트가 영영 스켈레톤을 띄운 채 폴링한다.
     *
     * <p>분석 본체와 달리 <b>결과는 이미 저장돼 있다.</b> 이미지만 실패로 돌리며,
     * 분석권은 애초에 결과 저장 시점에 차감돼 되돌리지 않는다.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            UPDATE AnalysisResult r
               SET r.imageStatus = com.gojeom.common.enums.ImageStatus.FAILED
             WHERE r.imageStatus = com.gojeom.common.enums.ImageStatus.PENDING
               AND r.createdAt < :threshold
            """)
    int failStaleImages(@org.springframework.data.repository.query.Param("threshold")
                        java.time.OffsetDateTime threshold);
}
