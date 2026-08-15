package com.gojeom.common.exception;

/**
 * 비즈니스 규칙 위반. {@link ErrorCode}가 HTTP 상태와 사용자 문구를 함께 들고 있다.
 *
 * <p>예상 가능한 실패에만 쓴다. 예상 못 한 예외는 그대로 던져서
 * {@link GlobalExceptionHandler}가 500으로 처리하고 스택을 남기게 한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object details() {
        return details;
    }
}
