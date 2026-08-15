package com.gojeom.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;

/** 알림 설정 API 계약. (API.md §6.7) */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * {@code GET · PATCH /notifications/settings} 응답.
     *
     * <p>{@code defaultTime}에 {@code @JsonFormat}이 필요하다. 없으면
     * {@code "21:00:00"}으로 나가 계약과 어긋난다. ([AGENTS.md](../../../../../../../../AGENTS.md) N-6)
     */
    public record SettingsResponse(
            boolean enabled,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime defaultTime) {
    }

    /**
     * 부분 수정. 두 필드 모두 선택이며 보내지 않은 항목은 그대로 둔다.
     *
     * <p>{@code defaultTime}은 {@code "21:00"} 형식 문자열로 받는다.
     */
    public record SettingsUpdateRequest(
            Boolean enabled,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime defaultTime) {
    }

    public record DeviceTokenRequest(
            @NotBlank(message = "토큰이 필요해요.")
            String token,

            @NotBlank(message = "플랫폼이 필요해요.")
            @Pattern(regexp = "WEB|IOS|ANDROID", message = "WEB, IOS, ANDROID 중 하나여야 해요.")
            String platform) {
    }
}
