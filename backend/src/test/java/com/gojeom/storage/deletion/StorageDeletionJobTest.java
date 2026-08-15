package com.gojeom.storage.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageDeletionJobTest {

    @Test
    @DisplayName("삭제 실패 횟수에 따라 재시도 간격이 증가한다")
    void 재시도_백오프() {
        StorageDeletionJob job = StorageDeletionJob.create("profiles/user/photo.jpg");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        job.scheduleRetry(now);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(now.plusMinutes(1));

        job.scheduleRetry(now);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getNextAttemptAt()).isEqualTo(now.plusMinutes(2));
    }
}
