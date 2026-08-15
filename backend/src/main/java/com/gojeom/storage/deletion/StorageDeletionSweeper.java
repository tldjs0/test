package com.gojeom.storage.deletion;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실패했거나 앱 재시작으로 남은 S3 삭제 작업을 재시도한다. */
@Component
@RequiredArgsConstructor
public class StorageDeletionSweeper {

    private final StorageDeletionService deletionService;

    @Scheduled(fixedDelayString = "60000")
    public void retryDueJobs() {
        deletionService.findDueJobIds(OffsetDateTime.now(ZoneOffset.UTC))
                .forEach(deletionService::process);
    }
}
