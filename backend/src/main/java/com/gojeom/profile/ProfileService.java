package com.gojeom.profile;

import com.gojeom.common.enums.Category;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.dto.ProfileDtos.PrioritiesUpdateRequest;
import com.gojeom.profile.dto.ProfileDtos.ProfileCreateRequest;
import com.gojeom.profile.dto.ProfileDtos.ProfileResponse;
import com.gojeom.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.storage.StorageService;
import com.gojeom.storage.UploadPurpose;
import com.gojeom.storage.deletion.StorageDeletionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final StorageService storageService;
    private final StorageDeletionService storageDeletionService;
    private final ProfileTxService profileTxService;
    private final ProfilePhotoValidator profilePhotoValidator;

    /**
     * 프로필 등록.
     *
     * <p>기존 활성 프로필이 있으면 비활성화하고 새 행을 만든다. 갱신이 아니라 이력이다.
     * 과거 분석이 그 시점 프로필을 계속 가리킬 수 있어야 하기 때문이다. (ERD.md §3.3)
     *
     * <p>커밋 후 프로필 AI 분석이 비동기로 돌아 {@code analysisSummary}를 채운다.
     * 응답 시점에는 아직 null이다 — 프론트는 필요하면 {@code GET /profiles/me}로
     * 다시 읽는다. (D2-2)
     */
    public ProfileResponse create(UUID userId, ProfileCreateRequest request) {
        // 클라이언트가 남의 경로 key를 보낼 수 있으므로 저장 전에 다시 확인한다.
        storageService.validateUploadedImage(
                request.photoKey(), UploadPurpose.PROFILE_PHOTO, userId);

        // 스토리지 I/O와 CPU 얼굴 탐지는 DB 트랜잭션을 열기 전에 끝낸다.
        profilePhotoValidator.validate(storageService.download(request.photoKey()));

        Profile profile = profileTxService.replaceActive(userId, request);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getActive(UUID userId) {
        return toResponse(findActive(userId));
    }

    @Transactional
    public ProfileResponse updateBody(UUID userId, ProfileUpdateRequest request) {
        Profile profile = findActive(userId);
        profile.updateBody(request.weightKg(), request.sleepHours(), request.inbody());
        return toResponse(profile);
    }

    /** 시안 11의 "우선 순위 변경". 이후 새 분석부터 적용된다. */
    @Transactional
    public ProfileResponse updatePriorities(UUID userId, PrioritiesUpdateRequest request) {
        Profile profile = findActive(userId);
        profile.changePriorities(List.copyOf(request.priorities()));
        return toResponse(profile);
    }

    /**
     * 사진 삭제. 스토리지 객체를 <b>즉시</b> 지운다. (PRD §10)
     *
     * <p>사진이 없으면 신규 분석을 시작할 수 없다.
     */
    @Transactional
    public void deletePhoto(UUID userId) {
        Profile profile = findActive(userId);
        String key = profile.getPhotoKey();
        profile.removePhoto();
        storageDeletionService.enqueue(key);
    }

    private Profile findActive(UUID userId) {
        return profileRepository.findByUserIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_REQUIRED));
    }

    private ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                storageService.presignDownload(profile.getPhotoKey()),
                profile.getPriorities(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getSleepHours(),
                profile.getInbody(),
                profile.getAnalysisSummary(),
                profile.getCreatedAt());
    }
}
