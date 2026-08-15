package com.gojeom.auth.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.gojeom.common.config.GoogleOAuthProperties;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Google ID 토큰 검증. (TASKS.md D3-6)
 *
 * <p><b>직접 구현하지 않는다.</b> 서명·발급자·audience·만료 검증에 더해
 * JWKS 캐싱과 키 롤오버까지 라이브러리가 처리한다. 손으로 짜면 키가 갱신되는
 * 날 조용히 로그인이 막힌다.
 *
 * <p>검증하는 것: 서명 · {@code iss}(accounts.google.com) · {@code aud}(우리 클라이언트 ID)
 * · {@code exp}. audience 검증이 핵심이다 — 없으면 <b>다른 앱에 발급된 토큰으로도
 * 우리 서비스에 로그인</b>할 수 있다.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleOAuthProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.verifier = properties.isConfigured()
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(properties.clientId()))
                        .build()
                : null;

        if (verifier == null) {
            log.warn("GOOGLE_CLIENT_ID가 설정되지 않았다. Google 로그인은 비활성 상태다. (TASKS.md 0-6)");
        }
    }

    /**
     * @return 검증된 사용자 정보
     * @throws BusinessException 설정 미완이면 {@code INTERNAL_ERROR},
     *                           토큰이 유효하지 않으면 {@code AUTH_INVALID_CREDENTIALS}
     */
    public GoogleAccount verify(String idToken) {
        if (verifier == null) {
            // 설정이 안 된 것은 사용자 잘못이 아니다. 인증 실패로 위장하지 않는다.
            log.error("Google 로그인 요청을 받았으나 GOOGLE_CLIENT_ID가 없다");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            // 토큰 원문을 로그에 남기지 않는다. 그 자체가 자격 증명이다. (규칙 9)
            log.info("Google ID 토큰 검증 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (token == null) {
            // 서명·issuer·audience·만료 중 하나가 어긋났다.
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        GoogleIdToken.Payload payload = token.getPayload();
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            // 미인증 이메일을 받아들이면 "이메일 기준 1계정" 규칙으로 남의 계정을
            // 가로챌 수 있다. (API.md §6.1)
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return new GoogleAccount(
                payload.getSubject(),
                email,
                (String) payload.get("name"));
    }

    /**
     * @param subject Google의 {@code sub}. {@code users.provider_user_id}가 된다
     * @param name    프로필 이름. 없을 수 있다
     */
    public record GoogleAccount(String subject, String email, String name) {
    }
}
