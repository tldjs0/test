package com.gojeom.common.enums;

/**
 * 비교 이미지 생성 상태. 분석 상태와 <b>분리</b>되어 있다. (ERD.md §3.4)
 *
 * <p>프론트는 이 값으로 결과 화면의 비교 슬라이더를 분기한다. (API.md §6.4)
 */
public enum ImageStatus {

    /** 비교 이미지를 만들지 않는다. 프론트는 슬라이더를 렌더링하지 않는다. */
    SKIPPED,

    /** 생성 중. 프론트는 스켈레톤을 유지하고 폴링을 계속한다. */
    PENDING,

    DONE,

    /** 생성 실패. 텍스트 결과는 정상 노출하고 안내 문구만 띄운다. */
    FAILED
}
