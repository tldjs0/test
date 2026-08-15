package com.gojeom.analysis;

import com.gojeom.analysis.service.AnalysisPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 이후에 비동기 파이프라인을 건다.
 *
 * <p>{@code @TransactionalEventListener}와 {@code @Async}를 한 메서드에 겹치지 않는다.
 * 여기서는 커밋 시점만 잡고, 스레드 전환은 {@link AnalysisPipeline}의 {@code @Async}가
 * 담당한다. 두 애노테이션이 각각 다른 빈에 있어야 프록시가 순서대로 적용된다.
 */
@Component
@RequiredArgsConstructor
public class AnalysisEventListener {

    private final AnalysisPipeline pipeline;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCreated(AnalysisEvents.AnalysisCreated event) {
        pipeline.runKeywordExtraction(event.analysisId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKeywordsSelected(AnalysisEvents.KeywordsSelected event) {
        pipeline.runResultGeneration(event.analysisId());
    }
}
