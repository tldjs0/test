package com.gojeom.notification.repository;

import com.gojeom.notification.entity.NotificationSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, UUID> {

    Optional<NotificationSetting> findByUserId(UUID userId);
}
