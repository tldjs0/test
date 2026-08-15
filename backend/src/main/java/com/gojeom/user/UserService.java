package com.gojeom.user;

import com.gojeom.analysis.service.AnalysisPurgeService;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.storage.deletion.StorageDeletionService;
import com.gojeom.subscription.entity.Subscription;
import com.gojeom.subscription.repository.SubscriptionRepository;
import com.gojeom.user.dto.UserDtos.MeResponse;
import com.gojeom.user.dto.UserDtos.NicknameUpdateRequest;
import com.gojeom.user.dto.UserDtos.SubscriptionInfo;
import com.gojeom.user.entity.User;
import com.gojeom.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProfileRepository profileRepository;
    private final AnalysisPurgeService analysisPurgeService;
    private final StorageDeletionService storageDeletionService;

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Subscription subscription = subscriptionRepository.findByUserId(userId).orElse(null);

        // 프론트의 최초 진입 라우팅 기준. false면 프로필 등록 화면으로 보낸다. (API.md C-1)
        boolean hasProfile = profileRepository.existsByUserIdAndIsActiveTrue(userId);

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getCreatedAt(),
                hasProfile,
                subscription == null ? 0 : subscription.getAnalysisCredits(),
                toInfo(subscription, now));
    }

    /**
     * 닉네임 변경.
     *
     * <p>가입 흐름이 회원가입 → 닉네임 입력 순서라 이 엔드포인트가 없으면
     * 사용자가 정한 이름이 서버에 남지 않는다. (API.md에 없던 추가)
     */
    @Transactional
    public MeResponse updateNickname(UUID userId, NicknameUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        user.changeNickname(request.nickname().trim());
        return getMe(userId);
    }

    /**
     * 계정 삭제. (PRD §10 · ERD.md §7 · API.md §5 7번)
     *
     * <p><b>계정 행은 soft delete, 이미지는 즉시 하드 삭제.</b> 두 가지를 다르게
     * 다루는 이유가 있다. 계정은 착오 삭제와 정산·감사 대비로 유예를 두지만,
     * 얼굴 사진은 생체정보에 준하므로 유예 없이 지운다. (ERD.md D-5)
     *
     * <p>지우는 객체 — 모든 프로필 사진(비활성 이력 포함) + 참고 사진 + 비교 이미지.
     *
     * <p>삭제 후에는 기존 액세스 토큰이 있어도 조회가 막힌다. 모든 조회가
     * {@code findBy...AndDeletedAtIsNull}을 쓰기 때문이다.
     *
     * <p><b>30일 후 하드 삭제 배치는 아직 없다.</b> (ARCHITECTURE.md §11 {@code AccountPurger})
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 분석과 그에 딸린 이미지를 먼저 정리한다. 목표는 남는다 — 계정이 하드
        // 삭제될 때 users FK CASCADE로 함께 사라진다.
        analysisPurgeService.purgeAll(userId);

        // 프로필 사진은 활성 행만이 아니라 이력 전부를 지운다. 예전 사진이
        // 스토리지에 남으면 삭제 요구를 지킨 것이 아니다.
        profileRepository.findByUserId(userId).stream()
                .map(Profile::getPhotoKey)
                .filter(key -> key != null && !key.isBlank())
                .forEach(storageDeletionService::enqueue);

        user.softDelete(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private SubscriptionInfo toInfo(Subscription subscription, OffsetDateTime now) {
        if (subscription == null) {
            return null;
        }
        return new SubscriptionInfo(
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getExpiresAt(),
                subscription.canAnalyze(now),
                subscription.canCreateRoutine(now));
    }
}
