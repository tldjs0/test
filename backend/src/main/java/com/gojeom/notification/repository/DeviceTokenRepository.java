package com.gojeom.notification.repository;

import com.gojeom.notification.entity.DeviceToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    /** 토큰이 전역 UNIQUE라 재등록 판정에 쓴다. */
    Optional<DeviceToken> findByToken(String token);
}
