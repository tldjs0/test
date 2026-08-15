package com.gojeom.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfilePhotoValidatorTest {

    private final ProfileFaceDetector faceDetector = mock(ProfileFaceDetector.class);
    private final ProfilePhotoValidator validator = new ProfilePhotoValidator(faceDetector);
    private final byte[] image = new byte[]{1};

    @Test
    @DisplayName("얼굴이 한 명이면 통과한다")
    void 얼굴_한_명() {
        when(faceDetector.count(image)).thenReturn(1);

        assertThatCode(() -> validator.validate(image)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("얼굴이 없으면 IMAGE_NO_FACE다")
    void 얼굴_없음() {
        when(faceDetector.count(image)).thenReturn(0);

        assertError(ErrorCode.IMAGE_NO_FACE);
    }

    @Test
    @DisplayName("얼굴이 여러 명이면 IMAGE_MULTIPLE_FACES다")
    void 얼굴_여러_명() {
        when(faceDetector.count(image)).thenReturn(2);

        assertError(ErrorCode.IMAGE_MULTIPLE_FACES);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
