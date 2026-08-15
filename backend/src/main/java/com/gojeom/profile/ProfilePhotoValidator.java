package com.gojeom.profile;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전용 얼굴 탐지 결과를 API 오류 코드로 변환한다. 외모 평가는 하지 않는다. */
@Component
@RequiredArgsConstructor
public class ProfilePhotoValidator {

    private final ProfileFaceDetector faceDetector;

    public void validate(byte[] imageBytes) {
        int count = faceDetector.count(imageBytes);
        if (count == 0) {
            throw new BusinessException(ErrorCode.IMAGE_NO_FACE);
        }
        if (count > 1) {
            throw new BusinessException(ErrorCode.IMAGE_MULTIPLE_FACES);
        }
    }
}
