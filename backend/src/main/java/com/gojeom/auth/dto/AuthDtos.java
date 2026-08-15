package com.gojeom.auth.dto;

import com.gojeom.common.enums.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 인증 도메인 요청·응답 DTO. (API.md §6.1) */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식을 확인해주세요.")
            @Size(max = 255)
            String email,

            @NotBlank(message = "비밀번호를 입력해주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 해요.")
            String password,

            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = 20, message = "닉네임은 20자 이내로 입력해주세요.")
            String nickname) {
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식을 확인해주세요.")
            String email,

            @NotBlank(message = "비밀번호를 입력해주세요.")
            String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    /**
     * Google 로그인. 프론트가 Google에서 받은 <b>ID 토큰</b>을 그대로 보낸다.
     *
     * <p>응답은 {@code POST /auth/login}과 동일한 {@link TokenResponse}다.
     * 프론트가 로그인 방식에 따라 분기하지 않게 하기 위해서다. (API.md §6.1)
     */
    public record GoogleLoginRequest(
            @NotBlank(message = "ID 토큰이 필요해요.")
            String idToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserSummary user) {
    }

    public record UserSummary(
            UUID id,
            String email,
            String nickname,
            AuthProvider provider) {
    }
}
