package com.gojeom.user.repository;

import com.gojeom.common.enums.AuthProvider;
import com.gojeom.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /** 유효한 Access Token이라도 탈퇴 계정이면 보호 API 접근을 차단한다. */
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /** Google 로그인 1단계 조회. (API.md §6.1 계정 식별 규칙) */
    Optional<User> findByProviderAndProviderUserIdAndDeletedAtIsNull(
            AuthProvider provider, String providerUserId);
}
