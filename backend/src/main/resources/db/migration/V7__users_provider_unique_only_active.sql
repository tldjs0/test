-- 탈퇴한 Google 계정이 같은 Google 계정으로 다시 가입할 수 있게 한다.
--
-- V5는 email 유일성만 활성 계정으로 좁혔다. provider_user_id의 기존 인덱스는
-- 탈퇴 계정까지 포함하므로, GoogleTokenVerifier가 같은 sub를 반환하면 신규 INSERT가
-- ux_users_provider 위반으로 500이 된다.
--
-- UserRepository의 조회 기준도 deleted_at IS NULL이므로 DB 유일성 범위를 동일하게
-- 맞춘다. 활성 Google 계정 사이에서는 provider + sub 조합이 계속 유일하다.

DROP INDEX ux_users_provider;

CREATE UNIQUE INDEX ux_users_provider_active
    ON users(provider, provider_user_id)
    WHERE provider_user_id IS NOT NULL AND deleted_at IS NULL;
