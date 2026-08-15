-- profiles.photo_key 를 nullable 로 변경
--
-- 배경: PRD §10과 ERD §7은 "사진 삭제 시 photo_key 를 NULL 로" 규정하는데
-- V1 스키마는 NOT NULL 이었다. DELETE /profiles/me/photo 가 제약 위반으로
-- 실패하는 것을 D1-7 검증에서 확인했다.
--
-- 개인정보 삭제 요구가 우선이므로 스키마를 맞춘다.
-- 사진이 없는 프로필로는 신규 분석을 시작할 수 없다는 규칙은
-- 애플리케이션 레이어에서 강제한다.

ALTER TABLE profiles ALTER COLUMN photo_key DROP NOT NULL;
