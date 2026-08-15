package com.gojeom.storage.deletion;

import java.util.UUID;

/** DB 커밋 뒤 S3 삭제를 즉시 시도하기 위한 이벤트. */
public record StorageDeletionRequested(UUID jobId) {
}
