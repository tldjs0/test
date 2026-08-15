-- 탈퇴한 이메일이 재가입을 영구히 막지 않게 한다
--
-- 배경: users.email 이 전체 UNIQUE 였다. 계정 삭제는 soft delete(deleted_at 기록)라
-- 행이 남으므로, 탈퇴한 사람이 같은 이메일로 다시 가입하면
-- users_email_key 위반으로 500 이 났다.
--
-- AuthService.signup 은 existsByEmailAndDeletedAtIsNull() 로 미리 확인하는데,
-- 이 조회는 탈퇴 계정을 제외하므로 "중복 아님"으로 통과한 뒤 INSERT 에서 터진다.
-- 애플리케이션의 유일성 기준(활성 계정)과 DB 의 기준(전체 행)이 어긋나 있었다.
-- D3-5 검증에서 DELETE /users/me 를 붙이고 나서야 드러났다.
--
-- 해결: 유일성 범위를 애플리케이션과 똑같이 "활성 계정"으로 좁힌다.
-- 이 저장소는 이미 같은 방식을 쓰고 있다 —
--   ux_profiles_active ON profiles(user_id) WHERE is_active
--   ux_users_provider  ON users(provider, provider_user_id) WHERE provider_user_id IS NOT NULL
--
-- "이메일 기준 1계정" 규칙은 그대로다. 활성 계정 중에는 여전히 하나뿐이다.

ALTER TABLE users DROP CONSTRAINT users_email_key;

CREATE UNIQUE INDEX ux_users_email_active ON users(email) WHERE deleted_at IS NULL;
