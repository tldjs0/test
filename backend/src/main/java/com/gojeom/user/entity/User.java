package com.gojeom.user.entity;

import com.gojeom.common.entity.BaseTimeEntity;
import com.gojeom.common.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 계정. <b>이메일 기준 1계정이다.</b>
 *
 * <p>이메일로 가입한 뒤 같은 이메일로 Google 로그인하면 새 계정을 만들지 않고
 * 이 계정에 로그인시킨다. 반대로 Google로 먼저 가입하면 {@code passwordHash}가
 * null이라 이메일 로그인은 불가하다. (API.md §6.1)
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** 소셜 가입 계정은 null. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    /** 소셜 제공자의 고유 ID. Google의 {@code sub}. */
    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    /** soft delete. 조회 시 항상 제외한다. (ERD.md D-5) */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private User(String email, String passwordHash, AuthProvider provider,
                 String providerUserId, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
    }

    /** 이메일 회원가입. */
    public static User ofLocal(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, AuthProvider.LOCAL, null, nickname);
    }

    /** Google 회원가입. 비밀번호가 없다. */
    public static User ofGoogle(String email, String providerUserId, String nickname) {
        return new User(email, null, AuthProvider.GOOGLE, providerUserId, nickname);
    }

    /** 비밀번호 로그인이 가능한 계정인지. 소셜 전용 계정이면 false. */
    public boolean canLoginWithPassword() {
        return passwordHash != null;
    }

    /** 홈의 "안녕하세요, {닉네임}님"에 쓰인다. 가입 후 언제든 바꿀 수 있다. */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void softDelete(OffsetDateTime at) {
        this.deletedAt = at;
    }
}
