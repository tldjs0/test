package com.gojeom.profile;

import com.gojeom.profile.entity.ProfileAnalysisSummary;
import com.gojeom.profile.dto.ProfileDtos.ProfileCreateRequest;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 저장과 AI 분석 결과 반영의 <b>짧은 트랜잭션</b>만 담당하는 빈.
 *
 * <p>{@link ProfileAnalysisPipeline}과 분리한 이유 — 같은 클래스 안에서 호출하면
 * 프록시를 타지 않아 {@code @Transactional}이 걸리지 않는다. AI 호출 사이사이의
 * DB 접근만 짧게 트랜잭션에 넣으려면 빈이 나뉘어 있어야 한다.
 * (ARCHITECTURE.md §5.2)
 */
@Service
@RequiredArgsConstructor
public class ProfileTxService {

    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 사진 AI 검증이 끝난 뒤 DB 변경만 짧은 트랜잭션으로 처리한다. */
    @Transactional
    public Profile replaceActive(UUID userId, ProfileCreateRequest request) {
        profileRepository.findByUserIdAndIsActiveTrue(userId).ifPresent(Profile::deactivate);

        Profile profile = profileRepository.save(Profile.create(
                userId,
                request.photoKey(),
                List.copyOf(request.priorities()),
                request.heightCm(),
                request.weightKg(),
                request.sleepHours(),
                request.inbody()));
        eventPublisher.publishEvent(new ProfileCreatedEvent(profile.getId()));
        return profile;
    }

    /** 프로필이 이미 비활성화·삭제됐을 수 있으므로 {@code Optional}이다. */
    @Transactional(readOnly = true)
    public Optional<ProfileSnapshot> loadSnapshot(UUID profileId) {
        return profileRepository.findById(profileId)
                .map(p -> new ProfileSnapshot(
                        p.getId(), p.getPhotoKey(), p.getPriorities(),
                        p.getHeightCm(), p.getWeightKg(), p.getSleepHours(), p.getInbody()));
    }

    @Transactional
    public void applySummary(UUID profileId, ProfileAnalysisSummary summary) {
        profileRepository.findById(profileId).ifPresent(p -> p.applyAnalysisSummary(summary));
    }
}
