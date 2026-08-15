package com.gojeom.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gojeom.auth.jwt.JwtProvider.ParsedToken;
import com.gojeom.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtProvider, userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("활성 사용자의 Access Token은 인증된다")
    void 활성_사용자_인증() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(new ParsedToken(userId, "user@example.com"));
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(userRepository).existsByIdAndDeletedAtIsNull(userId);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 만료 전 Access Token도 인증되지 않는다")
    void 탈퇴_사용자_차단() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(new ParsedToken(userId, "deleted@example.com"));
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(false);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userRepository).existsByIdAndDeletedAtIsNull(userId);
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        return request;
    }
}
