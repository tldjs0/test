package com.gojeom.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gojeom.common.config.StorageProperties;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class StorageServiceValidationTest {

    private final S3Client s3Client = mock(S3Client.class);
    private StorageService storageService;
    private UUID userId;
    private String key;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                null, "ap-northeast-2", "bucket", "access", "secret",
                new StorageProperties.Presign(300, 600), 10 * 1024 * 1024L);
        storageService = new StorageService(
                mock(S3Presigner.class), s3Client, properties,
                new ObjectKeyFactory(), new ImageContentInspector());
        userId = UUID.randomUUID();
        key = "profiles/%s/photo.jpg".formatted(userId);
    }

    @Test
    @DisplayName("실제 S3 객체가 존재하고 크기와 Content-Type이 맞으면 통과한다")
    void 정상_객체() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1024L)
                        .contentType("image/jpeg")
                        .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), jpegBytes()));

        assertThatCode(() -> storageService.validateUploadedImage(
                key, UploadPurpose.PROFILE_PHOTO, userId)).doesNotThrowAnyException();

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().range()).isEqualTo("bytes=0-63");
    }

    @Test
    @DisplayName("실제 객체가 10MB를 넘으면 FILE_TOO_LARGE다")
    void 실제_크기_초과() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(10 * 1024 * 1024L + 1)
                        .contentType("image/jpeg")
                        .build());

        assertThatThrownBy(() -> storageService.validateUploadedImage(
                key, UploadPurpose.PROFILE_PHOTO, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("key 확장자와 S3 Content-Type이 다르면 거부한다")
    void 파일_형식_불일치() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1024L)
                        .contentType("image/png")
                        .build());

        assertThatThrownBy(() -> storageService.validateUploadedImage(
                key, UploadPurpose.PROFILE_PHOTO, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("S3에 객체가 없으면 업로드 미완료로 거부한다")
    void 객체_없음() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThatThrownBy(() -> storageService.validateUploadedImage(
                key, UploadPurpose.PROFILE_PHOTO, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("JPEG로 위장한 일반 파일은 실제 바이트 검사에서 거부한다")
    void 위장_파일() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(12L)
                        .contentType("image/jpeg")
                        .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), "not-an-image".getBytes()));

        assertThatThrownBy(() -> storageService.validateUploadedImage(
                key, UploadPurpose.PROFILE_PHOTO, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("비교 이미지용 다운로드도 최대 크기보다 한 바이트까지만 요청한다")
    void 다운로드_범위_제한() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), jpegBytes()));

        storageService.download(key);

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().range()).isEqualTo("bytes=0-10485760");
    }

    @Test
    @DisplayName("범위 응답이 최대 크기보다 크면 다운로드를 거부한다")
    void 다운로드_크기_재검사() {
        StorageProperties smallLimit = new StorageProperties(
                null, "ap-northeast-2", "bucket", "access", "secret",
                new StorageProperties.Presign(300, 600), 8L);
        StorageService smallStorage = new StorageService(
                mock(S3Presigner.class), s3Client, smallLimit,
                new ObjectKeyFactory(), new ImageContentInspector());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), new byte[9]));

        assertThatThrownBy(() -> smallStorage.download(key))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().range()).isEqualTo("bytes=0-8");
    }

    private byte[] jpegBytes() {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
