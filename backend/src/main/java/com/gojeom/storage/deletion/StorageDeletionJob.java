package com.gojeom.storage.deletion;

import com.gojeom.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** S3 삭제가 실패해도 대상 key를 잃지 않기 위한 영속 작업. */
@Entity
@Table(name = "storage_deletion_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageDeletionJob extends BaseTimeEntity {

    private static final int MAX_BACKOFF_EXPONENT = 10;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    private StorageDeletionJob(String objectKey, OffsetDateTime now) {
        this.objectKey = objectKey;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public static StorageDeletionJob create(String objectKey) {
        return new StorageDeletionJob(objectKey, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** 1·2·4분 순으로 늘리고 최대 약 17시간으로 제한한다. */
    public void scheduleRetry(OffsetDateTime now) {
        long delayMinutes = 1L << Math.min(attempts, MAX_BACKOFF_EXPONENT);
        attempts++;
        nextAttemptAt = now.plusMinutes(delayMinutes);
    }
}
