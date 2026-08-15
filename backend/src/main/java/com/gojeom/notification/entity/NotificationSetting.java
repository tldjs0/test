package com.gojeom.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사용자별 알림 설정. 사용자당 1행이다. ({@code user_id} UNIQUE · API.md §6.7)
 *
 * <p><b>기본값은 꺼짐이다.</b> 최초 목표 생성 시 동의를 받고 켠다. (PRD F-11)
 * 행이 없는 사용자는 문구상 기본값({@code enabled=false} · {@code 21:00})으로 취급하며,
 * 처음 설정을 바꿀 때 행이 만들어진다. 가입 시점에 미리 만들지 않는다 —
 * 쓰지도 않을 행을 모든 계정에 만들 이유가 없다.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    /** 문서상 기본값. (API.md §6.7 "기본값은 enabled = false") */
    public static final boolean DEFAULT_ENABLED = false;
    public static final LocalTime DEFAULT_TIME = LocalTime.of(21, 0);

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "default_time", nullable = false)
    private LocalTime defaultTime;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private NotificationSetting(UUID userId, boolean enabled, LocalTime defaultTime) {
        this.userId = userId;
        this.enabled = enabled;
        this.defaultTime = defaultTime;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 사용자가 처음 알림 설정을 건드릴 때 만들어진다. */
    public static NotificationSetting of(UUID userId, boolean enabled, LocalTime defaultTime) {
        return new NotificationSetting(userId, enabled, defaultTime);
    }

    /** null인 항목은 그대로 둔다. PATCH는 부분 수정이다. */
    public void update(Boolean enabled, LocalTime defaultTime) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (defaultTime != null) {
            this.defaultTime = defaultTime;
        }
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
