package com.gojeom.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gojeom.common.exception.ErrorCode;

/**
 * 모든 응답의 공통 봉투.
 *
 * <pre>
 * 성공: { "success": true,  "data": { ... } }
 * 실패: { "success": false, "error": { "code": "...", "message": "...", "details": ... } }
 * </pre>
 *
 * 프론트는 이 봉투를 인터셉터 한 곳에서만 해제한다. (API.md §1)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return fail(code, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code, Object details) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), code.message(), details));
    }

    /**
     * 실패 응답 본문.
     *
     * <p>{@code message}는 그대로 사용자에게 노출할 수 있는 한국어 문구다.
     * 프론트가 코드별 문구 테이블을 따로 관리하지 않도록 서버가 문구를 소유한다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Object details) {
    }
}
