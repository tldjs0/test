package com.gojeom.drawer.repository;

import com.gojeom.drawer.entity.SavedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedResultRepository extends JpaRepository<SavedResult, UUID> {

    /** 서랍 목록. 3섹션 모두 이 한 번의 조회에서 갈라진다. */
    List<SavedResult> findByUserIdOrderBySavedAtDesc(UUID userId);

    /** 결과 응답의 {@code saved} 판정. 결과당 최대 1행이다. */
    Optional<SavedResult> findByAnalysisResultId(UUID analysisResultId);

    boolean existsByAnalysisResultId(UUID analysisResultId);
}
