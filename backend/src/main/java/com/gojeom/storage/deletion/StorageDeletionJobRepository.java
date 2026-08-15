package com.gojeom.storage.deletion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageDeletionJobRepository extends JpaRepository<StorageDeletionJob, UUID> {

    List<StorageDeletionJob> findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            OffsetDateTime now);
}
