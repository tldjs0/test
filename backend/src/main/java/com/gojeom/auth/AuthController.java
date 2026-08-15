package com.gojeom.auth;

import com.gojeom.auth.dto.AuthDtos.GoogleLoginRequest;
import com.gojeom.auth.dto.AuthDtos.LoginRequest;
import com.gojeom.auth.dto.AuthDtos.RefreshRequest;
import com.gojeom.auth.dto.AuthDtos.SignupRequest;
import com.gojeom.auth.dto.AuthDtos.TokenResponse;
import com.gojeom.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * Google 로그인. 응답 형식은 {@code /auth/login}과 <b>동일하다.</b>
     * 프론트가 로그인 방식에 따라 분기하지 않는다. (API.md §6.1)
     */
    @PostMapping("/oauth/google")
    public ApiResponse<TokenResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.googleLogin(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * 로그아웃.
     *
     * <p>서버가 무상태라 토큰을 폐기할 곳이 없다. 클라이언트가 토큰을 지우는 것으로
     * 끝나며, 이 엔드포인트는 그 시점을 서버가 알 수 있게 하는 용도다.
     * 토큰 블랙리스트는 이번 범위 밖이다.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
