-- 분석을 지워도 그 분석으로 만든 목표는 남는다
--
-- 배경: 시안 11의 "내 분석 전체 삭제" 모달이 "*계정, 목표 정보는 삭제되지 않아요"를
-- 약속한다. 그런데 V1 스키마는 정반대를 강제하고 있었다.
--   - routines.analysis_result_id 가 FROM_ANALYSIS 일 때 NOT NULL (ck_routine_source)
--   - FK 가 ON DELETE 없음 = NO ACTION
-- 그래서 분석을 지우면 analysis_results 로 CASCADE 가 내려가다 이 FK 에서 막혀
-- DELETE /analyses 자체가 실패한다. 목표를 함께 지우지 않고서는 구현이 불가능했다.
--
-- 결정(2026-08-14): 목표를 남긴다. 사용자가 몇 주에 걸쳐 쌓은 완료 기록을
-- 분석 삭제의 부수 효과로 잃게 두지 않는다. 개인정보 삭제 요구는 이미지·분석
-- 원본을 지우는 것으로 충족되며, 목표의 태스크 목록에는 사진도 고점 원문도 없다.
--
-- 효과: 근거 분석이 사라진 FROM_ANALYSIS 목표는 analysis_result_id 가 NULL 이 된다.
-- 목표 화면은 고점 요약 카드만 빠지고 태스크·진행률은 그대로 뜬다
-- (RoutineService.detail 이 result == null 을 이미 처리한다).

-- ① FROM_ANALYSIS 에서 analysis_result_id 의 NOT NULL 요구를 뺀다.
--    나머지 조합 규칙은 그대로 둔다 — STANDALONE 이 분석을 가리키는 것은 여전히 금지다.
ALTER TABLE routines DROP CONSTRAINT ck_routine_source;

ALTER TABLE routines ADD CONSTRAINT ck_routine_source CHECK (
    (source_type = 'FROM_ANALYSIS'
        AND category IS NULL AND duration_weeks IS NULL)
 OR (source_type = 'STANDALONE'
        AND analysis_result_id IS NULL
        AND category IS NOT NULL AND duration_weeks IS NOT NULL)
);

-- ② 분석 결과가 지워지면 참조를 NULL 로 끊는다. 목표 행은 살아남는다.
ALTER TABLE routines DROP CONSTRAINT routines_analysis_result_id_fkey;

ALTER TABLE routines ADD CONSTRAINT routines_analysis_result_id_fkey
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_results(id) ON DELETE SET NULL;
