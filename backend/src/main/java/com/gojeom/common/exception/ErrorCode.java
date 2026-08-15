package com.gojeom.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API.md §4의 에러 코드 정본.
 *
 * <p><b>message는 그대로 사용자에게 노출되는 문구다.</b> 프론트가 코드별 문구를
 * 따로 관리하지 않도록 서버가 소유하며, 문구 수정은 배포 한 번으로 끝난다.
 * 코드는 프론트가 UI 형태(모달/토스트/인라인)를 고르는 데만 쓴다.
 *
 * <p>{@link #consumesCredit()}가 false인 코드는 분석권을 차감하지 않는다. (PRD O-7)
 */
public enum ErrorCode {

    // --- 공통 ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요."),

    // --- 인증 · 인가 ---
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었어요."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요."),
    AUTH_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    FORBIDDEN_RESOURCE(HttpStatus.FORBIDDEN, "접근할 수 없는 항목이에요."),

    // --- 프로필 ---
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "필수 항목에 동의해주세요."),
    PROFILE_UNDERAGE(HttpStatus.FORBIDDEN, "만 14세 이상만 이용할 수 있어요."),
    PROFILE_REQUIRED(HttpStatus.CONFLICT, "프로필을 먼저 등록해주세요."),

    // --- 업로드 · 이미지 ---
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "10MB 이하 사진을 올려주세요."),
    IMAGE_NO_FACE(HttpStatus.UNPROCESSABLE_ENTITY, "얼굴이 인식되지 않았어요. 정면을 향한 밝은 사진을 올려주세요."),
    IMAGE_MULTIPLE_FACES(HttpStatus.UNPROCESSABLE_ENTITY, "한 사람만 나온 사진을 올려주세요."),
    IMAGE_LOW_QUALITY(HttpStatus.UNPROCESSABLE_ENTITY, "사진이 흐리거나 어두워요. 다시 촬영해주세요."),

    // --- 분석 ---
    ANALYSIS_INVALID_STATE(HttpStatus.CONFLICT, "지금은 진행할 수 없는 단계예요."),
    ANALYSIS_NO_KEYWORD(HttpStatus.UNPROCESSABLE_ENTITY, "조금 더 구체적으로 적어주세요."),
    CONTENT_POLICY_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "분석할 수 없는 내용이 포함되어 있어요.", false),
    AI_PROVIDER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "분석 중 문제가 생겼어요. 다시 시도해주세요."),
    ANALYSIS_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "분석이 지연되고 있어요. 다시 시도해주세요.", false),

    // --- 인바디 ---
    INBODY_SCAN_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "서류를 읽지 못했어요. 직접 입력해주세요.", false),

    // --- 구독 ---
    NO_ANALYSIS_CREDIT(HttpStatus.PAYMENT_REQUIRED, "분석권을 모두 사용했어요.", false);

    private final HttpStatus status;
    private final String message;
    private final boolean consumesCredit;

    ErrorCode(HttpStatus status, String message) {
        this(status, message, true);
    }

    ErrorCode(HttpStatus status, String message, boolean consumesCredit) {
        this.status = status;
        this.message = message;
        this.consumesCredit = consumesCredit;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** false면 이 사유로 실패했을 때 분석권을 차감하지 않는다. */
    public boolean consumesCredit() {
        return consumesCredit;
    }
}
