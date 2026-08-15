package com.gojeom.ai;

import com.gojeom.common.exception.ErrorCode;

/**
 * AI 호출 실패. {@link ErrorCode}를 함께 들고 다녀서 파이프라인이 분석의
 * {@code failure_code}로 그대로 옮길 수 있게 한다.
 *
 * <p>어떤 코드로 실패했는지가 <b>분석권 차감 여부</b>를 가른다. (PRD O-7)
 * {@code ANALYSIS_TIMEOUT}·{@code CONTENT_POLICY_BLOCKED}는 미차감이다.
 *
 * <p>{@link #retryable()}은 에러 코드에서 유추하지 않고 <b>발생 지점이 직접</b>
 * 정한다. 같은 {@code AI_PROVIDER_ERROR}라도 429는 다시 부르면 되지만
 * 400(요청 오류)은 몇 번을 불러도 같은 답이 온다.
 */
public class AiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;

    public AiException(ErrorCode errorCode, boolean retryable, String detail) {
        this(errorCode, retryable, detail, null);
    }

    public AiException(ErrorCode errorCode, boolean retryable, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
