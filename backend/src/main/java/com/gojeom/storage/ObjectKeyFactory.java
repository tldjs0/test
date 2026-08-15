package com.gojeom.storage;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 스토리지 키 생성과 소유권 검증.
 *
 * <p>키에 {@code userId}를 넣는 이유는 정리 편의가 아니라 <b>보안</b>이다.
 * 클라이언트가 임의의 key 문자열을 보내올 수 있으므로, 저장 시점에
 * "이 key가 정말 이 사용자 경로인가"를 다시 확인한다. (ARCHITECTURE.md §8)
 */
@Component
public class ObjectKeyFactory {

    /** 허용 이미지 타입. 이 목록에 없으면 업로드 URL을 발급하지 않는다. */
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/heic", "heic",
            "image/webp", "webp");

    public String create(UploadPurpose purpose, UUID userId, String contentType) {
        String extension = extensionOf(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("contentType", "JPG, PNG, HEIC, WEBP만 올릴 수 있어요."));
        }
        return "%s/%s/%s.%s".formatted(purpose.prefix(), userId, UUID.randomUUID(), extension);
    }

    /**
     * key가 해당 사용자의 지정된 용도 경로인지 확인한다.
     *
     * @throws BusinessException 남의 경로이거나 형식이 어긋나면 {@code FORBIDDEN_RESOURCE}
     */
    public void assertOwned(String key, UploadPurpose purpose, UUID userId) {
        String expectedPrefix = "%s/%s/".formatted(purpose.prefix(), userId);
        if (key == null || !key.startsWith(expectedPrefix) || key.contains("..")) {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
    }

    /** S3 메타데이터의 Content-Type과 발급한 key 확장자가 일치하는지 확인한다. */
    public void assertContentTypeMatches(String key, String contentType) {
        String extension = extensionOf(contentType);
        if (extension == null || key == null || !key.endsWith("." + extension)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("file", "파일 형식이 올바르지 않아요."));
        }
    }

    private String extensionOf(String contentType) {
        return contentType == null
                ? null
                : ALLOWED_IMAGE_TYPES.get(contentType.toLowerCase(Locale.ROOT));
    }
}
