package com.gojeom.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gojeom.auth.dto.AuthDtos.SignupRequest;
import com.gojeom.auth.jwt.JwtProvider;
import com.gojeom.auth.oauth.GoogleTokenVerifier;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.subscription.entity.Subscription;
import com.gojeom.subscription.repository.SubscriptionRepository;
import com.gojeom.user.entity.User;
import com.gojeom.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceConcurrencyTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private GoogleTokenVerifier googleTokenVerifier;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("동시 회원가입의 이메일 유니크 충돌은 409 오류로 변환한다")
    void 동시_회원가입_중복_변환() {
        SignupRequest request = new SignupRequest("same@example.com", "password1", "고점");
        when(userRepository.existsByEmailAndDeletedAtIsNull("same@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED));

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
