package com.gojeom.analysis.service;

import com.gojeom.analysis.entity.Analysis;
import com.gojeom.analysis.entity.AnalysisReferenceImage;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.repository.AnalysisReferenceImageRepository;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.storage.deletion.StorageDeletionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 삭제. {@code DELETE /analyses}와 계정 삭제가 함께 쓴다. (PRD §10 · ERD.md §7)
 *
 * <p><b>스토리지 객체를 즉시 지운다.</b> DB 행만 지우면 S3에 얼굴 사진이 남는다.
 * 개인정보 삭제 요구는 "행이 안 보인다"가 아니라 "객체가 없다"이다.
 *
 * <p><b>목표는 지우지 않는다.</b> 시안 11의 모달이 "*계정, 목표 정보는 삭제되지
 * 않아요"를 약속한다. V4 마이그레이션으로 {@code routines.analysis_result_id}가
 * {@code ON DELETE SET NULL}이 되어, 근거 분석이 사라져도 목표와 완료 기록은 남는다.
 * 목표 화면은 고점 요약 카드만 빠지고 태스크·진행률은 그대로 뜬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisPurgeService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisReferenceImageRepository referenceImageRepository;
    private final AnalysisResultRepository resultRepository;
    private final StorageDeletionService storageDeletionService;

    /**
     * 사용자의 분석을 전부 지운다. 시안 11의 "내 분석 전체 삭제". (F-13)
     *
     * <p>키워드·결과·참고사진 행은 FK {@code ON DELETE CASCADE}로 함께 사라지고,
     * 서랍 항목도 {@code saved_results}의 CASCADE로 정리된다.
     *
     * @return 삭제한 분석 수
     */
    @Transactional
    public int purgeAll(UUID userId) {
        List<Analysis> analyses = analysisRepository.findByUserId(userId);
        if (analyses.isEmpty()) {
            return 0;
        }
        List<UUID> analysisIds = analyses.stream().map(Analysis::getId).toList();

        // 행을 지우기 전에 key를 모아야 한다. 지운 뒤에는 무엇을 지울지 알 수 없다.
        storageDeletionService.enqueueAll(collectStorageKeys(analysisIds));

        analysisRepository.deleteAll(analyses);
        log.info("분석 {}건 삭제", analyses.size());
        return analyses.size();
    }

    /** 참고 사진 + 비교 이미지 key. 프로필 사진은 프로필 쪽에서 따로 지운다. */
    private List<String> collectStorageKeys(List<UUID> analysisIds) {
        List<String> keys = new ArrayList<>();

        referenceImageRepository.findByAnalysisIdIn(analysisIds).stream()
                .map(AnalysisReferenceImage::getImageKey)
                .forEach(keys::add);

        resultRepository.findByAnalysisIdIn(analysisIds).stream()
                .map(AnalysisResult::getComparisonImageKey)
                .filter(key -> key != null && !key.isBlank())
                .forEach(keys::add);

        return keys;
    }
}
