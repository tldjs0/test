package com.gojeom.analysis.repository;

import com.gojeom.analysis.entity.AnalysisReferenceImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReferenceImageRepository extends JpaRepository<AnalysisReferenceImage, UUID> {

    List<AnalysisReferenceImage> findByAnalysisIdOrderByDisplayOrderAsc(UUID analysisId);

    /** 삭제 시 지울 스토리지 객체를 모으는 데 쓴다. */
    List<AnalysisReferenceImage> findByAnalysisIdIn(java.util.Collection<UUID> analysisIds);
}
