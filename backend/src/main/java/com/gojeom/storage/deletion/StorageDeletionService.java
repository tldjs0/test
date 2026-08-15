package com.gojeom.storage.deletion;

import com.gojeom.storage.StorageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 삭제 요청 저장과 S3 삭제 재시도를 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageDeletionService {

    private final StorageDeletionJobRepository jobRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /** 호출자의 DB 변경과 같은 트랜잭션에 삭제 대상을 기록한다. */
    @Transactional
    public void enqueue(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        StorageDeletionJob job = jobRepository.save(StorageDeletionJob.create(key));
        eventPublisher.publishEvent(new StorageDeletionRequested(job.getId()));
    }

    @Transactional
    public void enqueueAll(Collection<String> keys) {
        new LinkedHashSet<>(keys).forEach(this::enqueue);
    }

    /** 이벤트와 스케줄러가 함께 사용한다. S3 I/O 중에는 DB 트랜잭션이 없다. */
    public void process(UUID jobId) {
        String key = transactionTemplate.execute(status -> jobRepository.findById(jobId)
                .map(StorageDeletionJob::getObjectKey)
                .orElse(null));
        if (key == null) {
            return;
        }

        try {
            storageService.delete(key);
            transactionTemplate.executeWithoutResult(status -> jobRepository.deleteById(jobId));
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> jobRepository.findById(jobId)
                    .ifPresent(job -> job.scheduleRetry(OffsetDateTime.now(ZoneOffset.UTC))));
            // 사진 경로인 key는 로그에 남기지 않는다.
            log.warn("storage deletion deferred: {}", e.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueJobIds(OffsetDateTime now) {
        return jobRepository
                .findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(now).stream()
                .map(StorageDeletionJob::getId)
                .toList();
    }
}
