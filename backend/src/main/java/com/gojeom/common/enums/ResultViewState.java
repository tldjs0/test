package com.gojeom.common.enums;

/**
 * 결과 화면의 두 상태. 같은 스키마를 두 화면이 공유한다. (API.md §6.4 · PRD F-07)
 *
 * <table>
 *   <caption>상태별 프론트 분기</caption>
 *   <tr><th></th><th>FRESH</th><th>SAVED</th></tr>
 *   <tr><td>키워드 칩</td><td>체크박스형</td><td>pill형</td></tr>
 *   <tr><td>CTA</td><td>서랍에 저장 / 새로 분석</td><td>맞춤형 목표로 설정</td></tr>
 *   <tr><td>활성 탭</td><td>홈</td><td>서랍</td></tr>
 * </table>
 */
public enum ResultViewState {

    /** 분석 직후 ({@code GET /analyses/{id}/result}) */
    FRESH,

    /** 서랍 열람 ({@code GET /saved-results/{id}}) */
    SAVED
}
