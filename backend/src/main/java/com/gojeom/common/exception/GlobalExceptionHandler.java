package com.gojeom.common.exception;

import com.gojeom.common.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.errorCode();
        // 예상된 실패이므로 스택을 남기지 않는다. 운영 로그가 의미 없이 커진다.
        log.info("business error: {} - {}", code.name(), code.message());
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(code, e.details()));
    }

    /** @Valid 검증 실패 — 어떤 필드가 왜 틀렸는지 details로 내려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR, fields));
    }

    /**
     * 본문 파싱 실패 — 깨진 JSON, 타입 불일치, 빈 본문.
     *
     * <p>클라이언트 잘못이므로 400으로 내린다. 이 핸들러가 없으면 500이 나가서
     * 프론트가 서버 장애로 오인한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.info("malformed request body: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN_RESOURCE.status())
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN_RESOURCE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND));
    }

    /**
     * 예상 못 한 예외.
     *
     * <p>스택을 남기되 사용자에게는 일반 문구만 준다. 내부 메시지를 그대로 노출하지 않는다.
     * AI 호출 실패를 여기로 흘려보내지 말고 서비스에서 AI_PROVIDER_ERROR로 변환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
