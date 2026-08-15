-- S3 삭제가 일시적으로 실패해도 사진 key를 잃지 않고 재시도한다.
CREATE TABLE storage_deletion_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key      VARCHAR(512) NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_storage_deletion_jobs_due
    ON storage_deletion_jobs(next_attempt_at);
