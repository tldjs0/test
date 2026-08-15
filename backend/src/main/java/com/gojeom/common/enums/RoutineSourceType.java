package com.gojeom.common.enums;

/**
 * 목표 생성 경로 2종. (PRD F-09 · ERD.md §3.9)
 *
 * <p>경로마다 필요한 컬럼이 달라 {@code ck_routine_source} CHECK 제약이 강제한다.
 * 값을 잘못 조합하면 INSERT가 DB에서 거부된다.
 *
 * <table>
 *   <caption>경로별 컬럼</caption>
 *   <tr><th></th><th>analysis_result_id</th><th>category · duration_weeks</th></tr>
 *   <tr><td>FROM_ANALYSIS</td><td>NOT NULL</td><td>NULL</td></tr>
 *   <tr><td>STANDALONE</td><td>NULL</td><td>NOT NULL</td></tr>
 * </table>
 */
public enum RoutineSourceType {

    /** 저장된 분석 결과 기반. 여러 카테고리에 걸친 목표 <b>1개</b>. */
    FROM_ANALYSIS,

    /** 분석 없이 생성. 선택한 <b>카테고리당 목표 1개</b> (최대 3개). */
    STANDALONE
}
