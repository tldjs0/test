-- GO. 초기 스키마
-- 정본: ERD.md v2.1
-- 규칙: PK는 UUID, 시간은 TIMESTAMPTZ(UTC), Enum은 VARCHAR + CHECK

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255),
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('LOCAL','GOOGLE')),
    provider_user_id VARCHAR(255),
    nickname         VARCHAR(20)  NOT NULL,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE consents (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code      VARCHAR(20) NOT NULL CHECK (code IN ('TERMS','PRIVACY','BIOMETRIC','MARKETING')),
    required  BOOLEAN     NOT NULL,
    version   VARCHAR(20) NOT NULL,
    agreed    BOOLEAN     NOT NULL,
    agreed_at TIMESTAMPTZ
);

-- ---------------------------------------------------------------- profiles
-- priorities: 순서가 곧 순위인 3개 배열. 예) ["SKIN","HEALTH","BODY"]
-- birth_date / gender는 입력 화면 미설계로 현재 nullable. (PRD O-2)
CREATE TABLE profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    photo_key        VARCHAR(512) NOT NULL,
    priorities       JSONB        NOT NULL,
    birth_date       DATE,
    gender           VARCHAR(20)  CHECK (gender IN ('MALE','FEMALE','UNSPECIFIED')),
    height_cm        SMALLINT     NOT NULL CHECK (height_cm BETWEEN 100 AND 250),
    weight_kg        NUMERIC(4,1) NOT NULL CHECK (weight_kg BETWEEN 30 AND 200),
    sleep_hours      NUMERIC(3,1) CHECK (sleep_hours BETWEEN 0 AND 14),
    inbody           JSONB,
    analysis_summary JSONB,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_priorities_len CHECK (jsonb_array_length(priorities) = 3)
);

-- ---------------------------------------------------------------- analyses
CREATE TABLE analyses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id   UUID NOT NULL REFERENCES profiles(id),
    input_text   TEXT        NOT NULL CHECK (char_length(input_text) BETWEEN 10 AND 500),
    status       VARCHAR(20) NOT NULL DEFAULT 'CREATED'
                 CHECK (status IN ('CREATED','EXTRACTING','KEYWORDS_READY','GENERATING','DONE','FAILED')),
    failure_code VARCHAR(50),
    retried_from UUID REFERENCES analyses(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 고점 참고 사진은 여러 장 첨부할 수 있다. 0행이면 텍스트 전용 분석.
CREATE TABLE analysis_reference_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id   UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    image_key     VARCHAR(512) NOT NULL,
    display_order SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE analysis_keywords (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id   UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    label         VARCHAR(40) NOT NULL,
    reason        TEXT        NOT NULL,
    category      VARCHAR(10) NOT NULL CHECK (category IN ('SKIN','BODY','HEALTH')),
    display_order SMALLINT    NOT NULL,
    selected      BOOLEAN     NOT NULL DEFAULT FALSE
);

-- category_changes: 카테고리별 1건씩 정확히 3건. priorities 순서로 정렬해 저장한다.
-- change_intensity: 퍼센트가 아니라 짧은 텍스트 배열. (시안 17·20)
CREATE TABLE analysis_results (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id          UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    title                VARCHAR(60) NOT NULL,
    summary              TEXT        NOT NULL,
    keep_points          JSONB       NOT NULL DEFAULT '[]',
    emphasize_points     JSONB       NOT NULL DEFAULT '[]',
    change_intensity     JSONB       NOT NULL DEFAULT '[]',
    category_changes     JSONB       NOT NULL DEFAULT '[]',
    daily_cares          JSONB       NOT NULL DEFAULT '[]',
    comparison_image_key VARCHAR(512),
    image_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (image_status IN ('SKIPPED','PENDING','DONE','FAILED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- drawer
CREATE TABLE saved_results (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    analysis_result_id UUID NOT NULL REFERENCES analysis_results(id) ON DELETE CASCADE,
    saved_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- routines
-- 목표 생성 경로 2종. 필요한 컬럼이 달라 CHECK로 강제한다. (PRD F-09)
--   FROM_ANALYSIS: 저장된 분석 결과 기반. 여러 카테고리에 걸친 목표 1개.
--   STANDALONE   : 분석 없이 생성. 카테고리당 목표 1개.
CREATE TABLE routines (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type        VARCHAR(20) NOT NULL
                       CHECK (source_type IN ('FROM_ANALYSIS','STANDALONE')),
    analysis_result_id UUID REFERENCES analysis_results(id),
    category           VARCHAR(10) CHECK (category IN ('SKIN','BODY','HEALTH')),
    duration_weeks     SMALLINT    CHECK (duration_weeks BETWEEN 1 AND 12),
    title              VARCHAR(60) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE','COMPLETED','CANCELED')),
    start_date         DATE        NOT NULL,
    end_date           DATE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_routine_source CHECK (
        (source_type = 'FROM_ANALYSIS'
            AND analysis_result_id IS NOT NULL
            AND category IS NULL AND duration_weeks IS NULL)
     OR (source_type = 'STANDALONE'
            AND analysis_result_id IS NULL
            AND category IS NOT NULL AND duration_weeks IS NOT NULL)
    )
);

-- 표기: title / timing · duration_label · amount_label
--       "자외선 차단제 바르기" / "매일 외출 전 / 약 2분 / 4ml"
CREATE TABLE routine_tasks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routine_id     UUID NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    category       VARCHAR(10) NOT NULL CHECK (category IN ('SKIN','BODY','HEALTH')),
    title          VARCHAR(60) NOT NULL,
    timing         VARCHAR(40),
    duration_label VARCHAR(20),
    amount_label   VARCHAR(20),
    scheduled_date DATE NOT NULL,
    scheduled_time TIME,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','DONE','MISSED')),
    completed_at   TIMESTAMPTZ
);

-- ---------------------------------------------------------------- notification
CREATE TABLE notification_settings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    default_time TIME    NOT NULL DEFAULT '21:00',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE device_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL UNIQUE,
    platform   VARCHAR(10)  NOT NULL CHECK (platform IN ('WEB','IOS','ANDROID')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- subscription
CREATE TABLE subscriptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan             VARCHAR(10) NOT NULL CHECK (plan IN ('TRIAL','MONTHLY','YEARLY')),
    status           VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELED')),
    analysis_credits SMALLINT    NOT NULL DEFAULT 1 CHECK (analysis_credits >= 0),
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ
);

CREATE TABLE payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id   UUID NOT NULL REFERENCES subscriptions(id),
    pg_transaction_id VARCHAR(100) NOT NULL UNIQUE,
    amount            INTEGER      NOT NULL,
    status            VARCHAR(10)  NOT NULL CHECK (status IN ('PAID','FAILED','REFUNDED')),
    paid_at           TIMESTAMPTZ
);

-- ---------------------------------------------------------------- ai_jobs
-- 비용·지연·실패 추적용. 프롬프트 원문과 사용자 사진은 저장하지 않는다. (PRD §9)
CREATE TABLE ai_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id   UUID REFERENCES analyses(id) ON DELETE CASCADE,
    stage         VARCHAR(30) NOT NULL,
    model         VARCHAR(60) NOT NULL,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','DONE','FAILED')),
    input_tokens  INTEGER,
    output_tokens INTEGER,
    latency_ms    INTEGER,
    error_code    VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- indexes
CREATE UNIQUE INDEX ux_users_provider     ON users(provider, provider_user_id) WHERE provider_user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_profiles_active    ON profiles(user_id) WHERE is_active;
CREATE UNIQUE INDEX ux_consents_user_code ON consents(user_id, code);
CREATE        INDEX ix_analyses_user      ON analyses(user_id, created_at DESC);
CREATE        INDEX ix_analyses_status    ON analyses(status) WHERE status IN ('CREATED','EXTRACTING','GENERATING');
CREATE        INDEX ix_ref_images         ON analysis_reference_images(analysis_id, display_order);
CREATE        INDEX ix_keywords_analysis  ON analysis_keywords(analysis_id, display_order);
CREATE UNIQUE INDEX ux_results_analysis   ON analysis_results(analysis_id);
CREATE UNIQUE INDEX ux_saved_result       ON saved_results(analysis_result_id);
CREATE        INDEX ix_saved_user         ON saved_results(user_id, saved_at DESC);
CREATE        INDEX ix_routines_user      ON routines(user_id, status);
CREATE        INDEX ix_tasks_routine_date ON routine_tasks(routine_id, scheduled_date);
CREATE        INDEX ix_tasks_pending      ON routine_tasks(scheduled_date) WHERE status = 'PENDING';
CREATE        INDEX ix_ai_jobs_analysis   ON ai_jobs(analysis_id, stage);
