package com.gojeom.common.enums;

/**
 * 분석 상태 기계. (ERD.md §3.4)
 *
 * <pre>
 * CREATED → EXTRACTING → KEYWORDS_READY → GENERATING → DONE
 *              ↓                              ↓
 *            FAILED                         FAILED
 * </pre>
 *
 * <p><b>이미지 생성 상태는 여기 없다.</b> {@link ImageStatus}로 따로 추적하며,
 * 이미지가 실패해도 분석 자체는 {@code DONE}이다.
 */
public enum AnalysisStatus {

    CREATED,
    EXTRACTING,
    KEYWORDS_READY,
    GENERATING,
    DONE,
    FAILED;

    /**
     * 서버가 작업 중인 상태.
     *
     * <p>{@code KEYWORDS_READY}는 포함하지 않는다. 사용자가 키워드를 고르는 동안
     * 머무는 상태라 시간 제한을 걸면 안 된다. 좀비 정리 대상 판정에 그대로 쓰인다.
     * (ARCHITECTURE.md §5.4)
     */
    public boolean isInProgress() {
        return this == CREATED || this == EXTRACTING || this == GENERATING;
    }

    public boolean isTerminal() {
        return this == DONE || this == FAILED;
    }
}
