package com.gojeom.notification;

import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.notification.dto.NotificationDtos.DeviceTokenRequest;
import com.gojeom.notification.dto.NotificationDtos.SettingsResponse;
import com.gojeom.notification.dto.NotificationDtos.SettingsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 알림 엔드포인트. (API.md §5 29~31번) */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/settings")
    public ApiResponse<SettingsResponse> getSettings(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(notificationService.getSettings(me.id()));
    }

    @PatchMapping("/settings")
    public ApiResponse<SettingsResponse> updateSettings(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody SettingsUpdateRequest request) {
        return ApiResponse.ok(notificationService.updateSettings(me.id(), request));
    }

    @PostMapping("/device-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> registerDeviceToken(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody DeviceTokenRequest request) {
        notificationService.registerDeviceToken(me.id(), request);
        return ApiResponse.ok();
    }
}
