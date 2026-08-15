package com.gojeom.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 푸시 발송 대상 기기. (API.md §6.7)
 *
 * <p>{@code token}이 전역 UNIQUE다. 같은 기기가 다른 계정으로 로그인하면 토큰의
 * 주인이 바뀌어야 하므로, 재등록 시 새 행을 만들지 않고 소유자를 옮긴다.
 *
 * <p><b>발송은 아직 없다.</b> 전송 수단(FCM vs Web Push)이 미정이라
 * (ARCHITECTURE.md B-5 · API.md A-6) 토큰을 받아 보관만 한다.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    private DeviceToken(UUID userId, String token, String platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }

    public static DeviceToken of(UUID userId, String token, String platform) {
        return new DeviceToken(userId, token, platform);
    }

    /** 기기가 다른 계정으로 로그인한 경우. */
    public void reassignTo(UUID userId, String platform) {
        this.userId = userId;
        this.platform = platform;
    }
}
