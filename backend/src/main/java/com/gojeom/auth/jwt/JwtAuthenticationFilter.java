package com.gojeom.auth.jwt;

import com.gojeom.common.security.UserPrincipal;
import com.gojeom.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer ...}를 검증해 {@link UserPrincipal}을 주입한다.
 *
 * <p>토큰이 없거나 잘못되면 여기서 401을 던지지 않고 그냥 통과시킨다.
 * 인증이 필요한 경로인지 판단하는 것은 {@code SecurityConfig}의 역할이고,
 * 최종 401 응답은 {@code authenticationEntryPoint}가 만든다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtProvider.ParsedToken parsed = jwtProvider.parseAccessToken(token);
                // JWT가 아직 만료되지 않았더라도 탈퇴한 계정이면 인증하지 않는다.
                // 계정 삭제는 soft delete라 토큰 서명만 검사하면 access TTL 동안
                // 프로필·분석·서랍 API에 계속 접근할 수 있다.
                if (!userRepository.existsByIdAndDeletedAtIsNull(parsed.userId())) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                UserPrincipal principal = new UserPrincipal(parsed.userId(), parsed.email());

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (RuntimeException e) {
                // 만료·위조 토큰. 인증하지 않은 채로 진행시키면
                // 보호된 경로에서 entryPoint가 401 AUTH_TOKEN_EXPIRED를 내린다.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
