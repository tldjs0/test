package com.gojeom.ai.job;

import com.gojeom.ai.AiStage;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 호출 기록. (ARCHITECTURE.md §6.1)
 *
 * <p>{@code REQUIRES_NEW}인 이유 — 호출부가 실패해 롤백해도 <b>호출했다는 사실은
 * 남아야 한다.</b> 비용은 이미 발생했고, 실패 기록이야말로 이 테이블의 존재 이유다.
 *
 * <p>{@code @Transactional} 대신 {@link TransactionTemplate}을 쓴 이유 —
 * 애노테이션 방식은 같은 클래스 내부 호출에서 프록시를 타지 않고, 예외를 안에서
 * 삼켜도 커밋 시점에 {@code UnexpectedRollbackException}이 다시 튀어나온다.
 * 여기서는 <b>기록 실패가 파이프라인을 절대 죽이지 않는 것</b>이 요구사항이라
 * 트랜잭션 경계를 직접 잡고 바깥에서 예외를 삼킨다.
 */
@Slf4j
@Component
public class AiJobRecorder {

    private final AiJobRepository aiJobRepository;
    private final TransactionTemplate requiresNew;

    public AiJobRecorder(AiJobRepository aiJobRepository, PlatformTransactionManager transactionManager) {
        this.aiJobRepository = aiJobRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordDone(UUID analysisId, AiStage stage, String model,
                           Integer inputTokens, Integer outputTokens, int latencyMs) {
        save(AiJob.done(analysisId, stage, model, inputTokens, outputTokens, latencyMs));
    }

    public void recordFailed(UUID analysisId, AiStage stage, String model,
                             int latencyMs, String errorCode) {
        save(AiJob.failed(analysisId, stage, model, latencyMs, errorCode));
    }

    private void save(AiJob job) {
        try {
            requiresNew.executeWithoutResult(status -> aiJobRepository.save(job));
        } catch (RuntimeException e) {
            // 관측용 부가 기능이 본 기능을 무너뜨리면 안 된다.
            log.warn("ai_jobs 기록 실패 stage={} : {}", job.getStage(), e.getClass().getSimpleName());
        }
    }
}
