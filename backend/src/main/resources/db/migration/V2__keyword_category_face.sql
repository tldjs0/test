-- 키워드 카테고리에 FACE 추가
--
-- 배경: AI 스파이크(2026-08-13)에서 "차분한 인상", "또렷한 인상" 같은 얼굴 관련
-- 키워드가 SKIN/BODY/HEALTH 3종 중 어디에도 맞지 않아 HEALTH로 잘못 분류되는 것을
-- 확인했다. Figma 시안의 실제 키워드(다이아몬드형·귀족턱·큰 눈)도 대부분 얼굴 관련이다.
--
-- 범위: 키워드에만 FACE를 허용한다.
--   - profiles.priorities      : 3종 유지 (시안 06의 우선순위는 피부·체형·건강)
--   - routines/routine_tasks   : 3종 유지 (루틴 카테고리는 3종)
--   - analysis_results.category_changes : 3종 유지 (결과 화면 칩이 3종)

ALTER TABLE analysis_keywords DROP CONSTRAINT analysis_keywords_category_check;

ALTER TABLE analysis_keywords ADD CONSTRAINT analysis_keywords_category_check
    CHECK (category IN ('SKIN', 'FACE', 'BODY', 'HEALTH'));
