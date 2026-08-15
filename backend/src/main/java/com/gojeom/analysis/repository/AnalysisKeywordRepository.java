package com.gojeom.analysis.repository;

import com.gojeom.analysis.entity.AnalysisKeyword;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisKeywordRepository extends JpaRepository<AnalysisKeyword, UUID> {

    List<AnalysisKeyword> findByAnalysisIdOrderByDisplayOrderAsc(UUID analysisId);

    List<AnalysisKeyword> findByAnalysisIdAndSelectedTrueOrderByDisplayOrderAsc(UUID analysisId);
}
