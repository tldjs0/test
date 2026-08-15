package com.gojeom.notification;

import com.gojeom.notification.dto.NotificationDtos.DeviceTokenRequest;
import com.gojeom.notification.dto.NotificationDtos.SettingsResponse;
import com.gojeom.notification.dto.NotificationDtos.SettingsUpdateRequest;
import com.gojeom.notification.entity.DeviceToken;
import com.gojeom.notification.entity.NotificationSetting;
import com.gojeom.notification.repository.DeviceTokenRepository;
import com.gojeom.notification.repository.NotificationSettingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정. (API.md §6.7 · PRD F-11)
 *
 * <p><b>발송 기능은 없다.</b> 전송 수단(FCM vs Web Push)이 미정이라
 * (ARCHITECTURE.md B-5) 설정과 기기 토큰을 보관하는 데까지만 만든다.
 * {@code NotificationScheduler}는 수단이 정해진 뒤에 붙인다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSettingRepository settingRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    /** 행이 없으면 문서상 기본값. 조회만으로 행을 만들지 않는다. */
    @Transactional(readOnly = true)
    public SettingsResponse getSettings(UUID userId) {
        return settingRepository.findByUserId(userId)
                .map(s -> new SettingsResponse(s.isEnabled(), s.getDefaultTime()))
                .orElseGet(() -> new SettingsResponse(
                        NotificationSetting.DEFAULT_ENABLED, NotificationSetting.DEFAULT_TIME));
    }

    /**
     * 부분 수정. 행이 없으면 이때 만든다.
     *
     * <p>가입 시점에 미리 만들지 않는 이유 — 알림을 쓰지 않는 계정에도 행이 생긴다.
     * 기본값이 "꺼짐"이라 행이 없는 것과 구분되지 않는다. (PRD F-11)
     */
    @Transactional
    public SettingsResponse updateSettings(UUID userId, SettingsUpdateRequest request) {
        NotificationSetting setting = settingRepository.findByUserId(userId)
                .orElseGet(() -> settingRepository.save(NotificationSetting.of(
                        userId, NotificationSetting.DEFAULT_ENABLED, NotificationSetting.DEFAULT_TIME)));

        setting.update(request.enabled(), request.defaultTime());
        return new SettingsResponse(setting.isEnabled(), setting.getDefaultTime());
    }

    /**
     * 기기 토큰 등록.
     *
     * <p>같은 토큰이 이미 있으면 새 행을 만들지 않고 <b>소유자를 옮긴다.</b>
     * 토큰이 전역 UNIQUE라 그냥 저장하면 제약 위반으로 500이 나고, 무엇보다
     * 기기를 넘겨받은 계정에 이전 사용자의 알림이 가면 안 된다.
     */
    @Transactional
    public void registerDeviceToken(UUID userId, DeviceTokenRequest request) {
        deviceTokenRepository.findByToken(request.token())
                .ifPresentOrElse(
                        existing -> existing.reassignTo(userId, request.platform()),
                        () -> deviceTokenRepository.save(
                                DeviceToken.of(userId, request.token(), request.platform())));
    }
}
