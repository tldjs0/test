package com.gojeom.analysis;

import java.util.UUID;

/**
 * 분석 파이프라인 시작 신호.
 *
 * <p>둘 다 {@code AFTER_COMMIT}에서 받는다. 커밋 전에 비동기를 시작하면 다른
 * 스레드가 <b>아직 없는 행</b>을 조회한다. (ARCHITECTURE.md §5.2)
 */
public final class AnalysisEvents {

    private AnalysisEvents() {
    }

    /** {@code POST /analyses} 커밋 후 → 키워드 추출. */
    public record AnalysisCreated(UUID analysisId) {
    }

    /** {@code POST /analyses/{id}/keywords/selection} 커밋 후 → 결과 생성. */
    public record KeywordsSelected(UUID analysisId) {
    }
}
