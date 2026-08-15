package com.gojeom.storage;

import com.gojeom.common.config.StorageProperties;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * presigned URL 발급과 객체 검증·삭제.
 *
 * <p>업로드 자체는 클라이언트가 스토리지로 직접 보낸다. 다만 업로드 완료 뒤에는
 * 서버 2차 검증을 위해 앞부분 64바이트만 범위 요청한다. (PRD 사진 검증)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final int IMAGE_SIGNATURE_BYTES = 64;

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final StorageProperties properties;
    private final ObjectKeyFactory keyFactory;
    private final ImageContentInspector imageContentInspector;

    /**
     * 업로드용 URL 발급.
     *
     * <p>{@code contentType}을 서명에 포함하므로 클라이언트는 PUT 시 <b>동일한
     * Content-Type 헤더</b>를 보내야 한다. 다르면 서명이 맞지 않아 실패한다.
     */
    public PresignedUpload presignUpload(
            UploadPurpose purpose, UUID userId, String contentType, long contentLength) {

        if (contentLength > properties.maxUploadBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String key = keyFactory.create(purpose, userId, contentType);
        Duration ttl = Duration.ofSeconds(properties.presign().uploadSeconds());

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build());

        return new PresignedUpload(presigned.url().toString(), key, ttl.toSeconds());
    }

    /**
     * 클라이언트가 업로드 완료 후 넘긴 key를 실제 S3 객체와 대조한다.
     *
     * <p>presigned URL 발급 당시의 {@code contentLength}는 클라이언트 신고값일 뿐
     * 실제 PUT 크기를 제한하지 않는다. 따라서 프로필·분석·OCR에서 사용하기 직전에
     * HEAD로 존재 여부·실제 크기·Content-Type을 확인하고, 실제 바이트 시그니처도
     * 대조한다.
     */
    public void validateUploadedImage(String key, UploadPurpose purpose, UUID userId) {
        keyFactory.assertOwned(key, purpose, userId);

        HeadObjectResponse object;
        try {
            object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        Map.of("file", "업로드가 완료되지 않았어요."));
            }
            // key는 개인정보에 준해 로그에 남기지 않는다.
            log.warn("storage metadata check failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (RuntimeException e) {
            log.warn("storage metadata check failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        Long contentLength = object.contentLength();
        if (contentLength == null || contentLength <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("file", "빈 파일은 사용할 수 없어요."));
        }
        if (contentLength > properties.maxUploadBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        keyFactory.assertContentTypeMatches(key, object.contentType());

        byte[] signature;
        try {
            signature = downloadRange(key, "bytes=0-" + (IMAGE_SIGNATURE_BYTES - 1));
        } catch (S3Exception e) {
            log.warn("storage content check failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (RuntimeException e) {
            log.warn("storage content check failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        imageContentInspector.assertMatches(signature, object.contentType());
    }

    /**
     * 조회용 URL 발급. 만료가 짧으므로 클라이언트가 캐시하면 안 된다. (API.md C-3)
     *
     * @return key가 null이면 null
     */
    public String presignDownload(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.presign().downloadSeconds()))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .build());
        return presigned.url().toString();
    }

    /**
     * 객체 바이트를 읽어온다. <b>비교 이미지 생성 전용이다.</b>
     *
     * <p>비교 이미지 편집에서 사용한다. 객체가 검증 이후 교체되더라도 메모리에
     * 10MB를 초과해 올리지 않도록 Range를 10MB+1로 제한하고 초과 여부를 재검사한다.
     *
     * <p><b>DB에 넣지 않는다.</b> 메모리에서 OpenAI로 흘려보내고 버린다.
     */
    public byte[] download(String key) {
        long lastByte = properties.maxUploadBytes();
        byte[] bytes = downloadRange(key, "bytes=0-" + lastByte);
        if (bytes.length > properties.maxUploadBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        return bytes;
    }

    private byte[] downloadRange(String key, String range) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .range(range)
                .build()).asByteArray();
    }

    /** 생성된 비교 이미지를 저장한다. 업로드 경로 중 유일하게 서버가 직접 쓴다. */
    public void upload(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
    }

    /**
     * 객체 삭제.
     *
     * <p>사진 삭제·계정 삭제 시 <b>즉시</b> 지운다. (PRD §10)
     * 실패하면 예외를 던진다. 호출부의 영속 삭제 큐가 실패를 기록하고 재시도한다.
     * 이 메서드를 DB 트랜잭션 안에서 직접 호출하지 않는다.
     */
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    public record PresignedUpload(String uploadUrl, String objectKey, long expiresIn) {
    }
}
