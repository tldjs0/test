package com.gojeom.auth;

import com.gojeom.auth.dto.AuthDtos.GoogleLoginRequest;
import com.gojeom.auth.dto.AuthDtos.LoginRequest;
import com.gojeom.auth.dto.AuthDtos.SignupRequest;
import com.gojeom.auth.dto.AuthDtos.TokenResponse;
import com.gojeom.auth.dto.AuthDtos.UserSummary;
import com.gojeom.auth.jwt.JwtProvider;
import com.gojeom.auth.oauth.GoogleTokenVerifier;
import com.gojeom.common.enums.AuthProvider;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.subscription.entity.Subscription;
import com.gojeom.subscription.repository.SubscriptionRepository;
import com.gojeom.user.entity.User;
import com.gojeom.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** {@code users.nickname}이 VARCHAR(20)이다. */
    private static final int NICKNAME_MAX = 20;

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GoogleTokenVerifier googleTokenVerifier;

    /**
     * 이메일 회원가입.
     *
     * <p>가입과 동시에 무료 체험 구독을 발급한다. 두 작업이 같은 트랜잭션에 있어야
     * "계정은 있는데 구독이 없는" 상태가 생기지 않는다. (PRD F-12)
     */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }

        User user;
        try {
            // exists 검사는 빠른 실패용일 뿐 동시 요청을 직렬화하지 못한다.
            // 즉시 flush해 ux_users_email_active 충돌을 이 메서드 안에서 409로 변환한다.
            user = userRepository.saveAndFlush(User.ofLocal(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.nickname().trim()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }

        subscriptionRepository.save(
                Subscription.startTrial(user.getId(), OffsetDateTime.now(ZoneOffset.UTC)));

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalize(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 소셜 전용 계정은 비밀번호가 없다. 존재 여부를 노출하지 않도록 같은 오류로 응답한다.
        if (!user.canLoginWithPassword()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    /** Refresh 토큰으로 재발급. 토큰 종류가 어긋나면 JwtProvider가 걸러낸다. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        JwtProvider.ParsedToken parsed = jwtProvider.parseRefreshToken(refreshToken);

        User user = userRepository.findByIdAndDeletedAtIsNull(parsed.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED));

        return issueTokens(user);
    }

    /**
     * Google 로그인. (API.md §6.1 · TASKS.md D3-6)
     *
     * <p><b>이메일 기준 1계정이다.</b> 아래 순서로 찾고, 없으면 만든다.
     *
     * <ol>
     *   <li>{@code (provider=GOOGLE, providerUserId=sub)} — 기존 Google 계정</li>
     *   <li>{@code email} — <b>같은 이메일의 기존 계정에 로그인</b></li>
     *   <li>없음 — 신규 생성 ({@code passwordHash=null})</li>
     * </ol>
     *
     * <p>2단계가 "같은 이메일이면 같은 계정" 방침을 구현한다. 이메일로 먼저
     * 가입한 사람이 Google로 로그인해도 계정이 갈라지지 않는다. 이때
     * {@code provider}를 {@code GOOGLE}로 바꾸지 않는다 — 비밀번호가 이미 있고,
     * 바꾸면 그 사람이 이메일 로그인을 못 하게 된다.
     */
    @Transactional
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleAccount account = googleTokenVerifier.verify(request.idToken());
        String email = normalize(account.email());

        User user = userRepository
                .findByProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider.GOOGLE, account.subject())
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(email))
                .orElseGet(() -> createGoogleUser(email, account));

        return issueTokens(user);
    }

    /** 신규 Google 계정. 이메일 가입과 마찬가지로 무료 체험을 함께 발급한다. */
    private User createGoogleUser(String email, GoogleTokenVerifier.GoogleAccount account) {
        User user = userRepository.save(
                User.ofGoogle(email, account.subject(), nicknameFrom(account, email)));

        subscriptionRepository.save(
                Subscription.startTrial(user.getId(), OffsetDateTime.now(ZoneOffset.UTC)));
        return user;
    }

    /**
     * 닉네임. Google 프로필 이름을 쓰되 없으면 이메일 아이디 부분으로 대신한다.
     *
     * <p>{@code nickname}은 {@code VARCHAR(20)}이라 반드시 잘라야 한다.
     * 자르지 않으면 이름이 긴 계정에서 저장이 실패한다.
     */
    private String nicknameFrom(GoogleTokenVerifier.GoogleAccount account, String email) {
        String base = account.name() != null && !account.name().isBlank()
                ? account.name().trim()
                : email.substring(0, email.indexOf('@'));
        return base.length() > NICKNAME_MAX ? base.substring(0, NICKNAME_MAX) : base;
    }

    private TokenResponse issueTokens(User user) {
        return new TokenResponse(
                jwtProvider.createAccessToken(user.getId(), user.getEmail()),
                jwtProvider.createRefreshToken(user.getId()),
                jwtProvider.accessTtlSeconds(),
                new UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getProvider()));
    }

    /** 대소문자 차이로 같은 사람이 두 계정을 만드는 것을 막는다. */
    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
