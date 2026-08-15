package com.gojeom.profile.repository;

import com.gojeom.profile.entity.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserIdAndIsActiveTrue(UUID userId);

    /** {@code GET /users/me}의 {@code hasProfile} 판정. (API.md C-1) */
    boolean existsByUserIdAndIsActiveTrue(UUID userId);

    /** 계정 삭제 시 <b>비활성 이력까지</b> 사진을 지우기 위해 쓴다. (PRD §10) */
    java.util.List<Profile> findByUserId(UUID userId);
}
