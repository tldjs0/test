package com.gojeom.user.dto;

import com.gojeom.common.enums.AuthProvider;
import com.gojeom.common.enums.SubscriptionPlan;
import com.gojeom.common.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /**
     * {@code GET /users/me} 응답. <b>프론트의 최초 진입 라우팅 기준이다.</b>
     *
     * <p>{@code hasProfile}이 false면 프로필 등록 화면으로, true면 홈으로 보낸다.
     * (API.md C-1)
     */
    public record MeResponse(
            UUID id,
            String email,
            String nickname,
            AuthProvider provider,
            OffsetDateTime joinedAt,
            boolean hasProfile,
            int analysisCredits,
            SubscriptionInfo subscription) {
    }

    public record SubscriptionInfo(
            SubscriptionPlan plan,
            SubscriptionStatus status,
            OffsetDateTime expiresAt,
            boolean canAnalyze,
            boolean canCreateRoutine) {
    }

    /**
     * {@code PATCH /users/me} — 닉네임 변경.
     *
     * <p><b>API.md에 없던 엔드포인트다.</b> 프론트의 가입 흐름이
     * 회원가입 → 닉네임 입력(시안 04 "이름 설정") 순서라, 닉네임을 나중에
     * 저장할 수단이 없으면 사용자가 입력한 이름이 기기 안에만 남는다.
     * 홈의 "안녕하세요, {닉네임}님"이 재설치하면 사라진다.
     *
     * <p>제약은 회원가입과 동일하게 맞춘다 — {@code users.nickname}은 VARCHAR(20)이다.
     */
    public record NicknameUpdateRequest(
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(max = 20, message = "닉네임은 20자 이내로 입력해주세요.")
            String nickname) {
    }
}
