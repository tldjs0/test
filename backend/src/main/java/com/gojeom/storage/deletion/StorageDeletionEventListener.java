package com.gojeom.storage.deletion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 삭제 작업이 커밋된 뒤 즉시 S3 삭제를 시도한다. */
@Component
@RequiredArgsConstructor
public class StorageDeletionEventListener {

    private final StorageDeletionService deletionService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onRequested(StorageDeletionRequested event) {
        deletionService.process(event.jobId());
    }
}
