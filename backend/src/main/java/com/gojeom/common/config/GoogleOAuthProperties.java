package com.gojeom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 로그인 설정. (TASKS.md 0-6)
 *
 * <p><b>클라이언트 시크릿이 없다.</b> ID 토큰 검증 방식에서는 필요하지 않다.
 * 프론트가 이 클라이언트 ID로 받은 ID 토큰을 보내면, 백엔드는 같은 값을
 * {@code audience}로 써서 "우리 앱에 발급된 토큰인가"를 확인한다.
 *
 * @param clientId 비어 있으면 Google 로그인 엔드포인트가 비활성화된다
 */
@ConfigurationProperties(prefix = "google")
public record GoogleOAuthProperties(String clientId) {

    /** 콘솔 설정(0-6)이 끝났는지. 웹 클라이언트 ID는 이 접미로 끝난다. */
    public boolean isConfigured() {
        return clientId != null && clientId.endsWith(".apps.googleusercontent.com");
    }
}
