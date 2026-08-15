package com.gojeom.analysis.service;

import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.exception.ErrorCode;

/**
 * 폴링 응답의 {@code progress} · {@code message} · {@code pollAfterMs} 매핑. (D2-5)
 *
 * <p>진행률은 실제 작업량이 아니라 <b>화면이 멈춰 보이지 않게 하는 장치</b>다.
 * EXTRACTING 0~50 / GENERATING 50~99 / DONE 100 구간을 지킨다.
 *
 * <p>문구는 서버가 소유한다. 프론트가 상태별 문구 테이블을 따로 들지 않는다.
 */
public final class AnalysisProgress {

    private static final long POLL_FAST_MS = 2000L;
    private static final long POLL_SLOW_MS = 3000L;

    private AnalysisProgress() {
    }

    public static int percent(AnalysisStatus status) {
        return switch (status) {
            case CREATED -> 5;
            case EXTRACTING -> 25;
            case KEYWORDS_READY -> 50;
            case GENERATING -> 75;
            // 실패도 더 기다릴 것이 없다는 뜻에서 종료값을 준다. 프론트는 progress가
            // 아니라 status로 분기하므로 이 값이 성공처럼 보이지 않는다.
            case DONE, FAILED -> 100;
        };
    }

    public static String message(AnalysisStatus status, String failureCode) {
        return switch (status) {
            case CREATED -> "분석을 준비하고 있어요";
            case EXTRACTING -> "고점 키워드를 찾고 있어요";
            case KEYWORDS_READY -> "마음에 드는 키워드를 골라주세요";
            case GENERATING -> "고점 결과를 만들고 있어요";
            case DONE -> "분석이 완료됐어요";
            case FAILED -> failureMessage(failureCode);
        };
    }

    /**
     * 폴링 간격. 종료 상태에서는 null을 주어 <b>프론트가 폴링을 멈출 근거</b>로 삼는다.
     *
     * <p>{@code KEYWORDS_READY}에서도 간격을 준다. 시안 14는 분석이 계속 진행되는
     * 동안 키워드를 고르는 구조라 폴링을 멈추지 않는다. (API.md C-4)
     */
    public static Long pollAfterMs(AnalysisStatus status) {
        return switch (status) {
            case CREATED, EXTRACTING, KEYWORDS_READY -> POLL_FAST_MS;
            case GENERATING -> POLL_SLOW_MS;
            case DONE, FAILED -> null;
        };
    }

    /** 실패 사유 문구도 {@link ErrorCode}가 소유한다. 코드가 곧 문구다. */
    private static String failureMessage(String failureCode) {
        if (failureCode == null) {
            return ErrorCode.AI_PROVIDER_ERROR.message();
        }
        try {
            return ErrorCode.valueOf(failureCode).message();
        } catch (IllegalArgumentException e) {
            return ErrorCode.AI_PROVIDER_ERROR.message();
        }
    }
}
