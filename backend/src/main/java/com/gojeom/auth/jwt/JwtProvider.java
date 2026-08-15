package com.gojeom.auth.jwt;

import com.gojeom.common.config.JwtProperties;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급과 검증. Access 30분 / Refresh 14일. (API.md §2)
 *
 * <p>토큰 종류를 {@code typ} 클레임으로 구분한다. Refresh 토큰으로 API를 호출하거나
 * Access 토큰으로 갱신을 시도하는 것을 막기 위해서다.
 */
@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtProvider(JwtProperties properties) {
        // HS256은 최소 256비트 키를 요구한다. 짧으면 여기서 즉시 실패한다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(properties.accessMinutes());
        this.refreshTtl = Duration.ofDays(properties.refreshDays());
    }

    public String createAccessToken(UUID userId, String email) {
        return build(userId, TYPE_ACCESS, email, accessTtl);
    }

    public String createRefreshToken(UUID userId) {
        return build(userId, TYPE_REFRESH, null, refreshTtl);
    }

    /** Access 토큰을 검증하고 사용자 정보를 꺼낸다. */
    public ParsedToken parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** Refresh 토큰을 검증하고 사용자 ID를 꺼낸다. */
    public ParsedToken parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private String build(UUID userId, String type, String email, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
        if (email != null) {
            builder.claim(CLAIM_EMAIL, email);
        }
        return builder.compact();
    }

    private ParsedToken parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                // access 자리에 refresh를 넣는 등 종류가 어긋난 경우
                throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
            }
            return new ParsedToken(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class));

        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·형식 오류도 같은 코드로 내린다.
            // 무엇이 틀렸는지 알려주면 공격자에게 힌트가 된다.
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
    }

    public record ParsedToken(UUID userId, String email) {
    }
}
