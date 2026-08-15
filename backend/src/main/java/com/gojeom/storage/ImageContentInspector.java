package com.gojeom.storage;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 파일명과 Content-Type을 위장한 업로드를 실제 바이트 시그니처로 차단한다. */
@Component
public class ImageContentInspector {

    public void assertMatches(byte[] bytes, String declaredContentType) {
        String detected = detect(bytes);
        String normalized = declaredContentType == null
                ? null
                : declaredContentType.toLowerCase(Locale.ROOT);
        if (!java.util.Objects.equals(normalized, detected)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("file", "실제 이미지 형식이 파일 정보와 달라요."));
        }
    }

    String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return "image/webp";
        }
        if (ascii(bytes, 4, 4).equals("ftyp") && isHeic(bytes)) {
            return "image/heic";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (Byte.toUnsignedInt(bytes[i]) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private String ascii(byte[] bytes, int offset, int length) {
        if (bytes.length < offset + length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private boolean isHeic(byte[] bytes) {
        if (isHeicSpecificBrand(ascii(bytes, 8, 4))) {
            return true;
        }
        // mif1/msf1은 범용 컨테이너 브랜드라 compatible brand에 HEIC가 있어야 한다.
        for (int offset = 16; offset + 4 <= Math.min(bytes.length, 64); offset += 4) {
            if (isHeicSpecificBrand(ascii(bytes, offset, 4))) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeicSpecificBrand(String brand) {
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx" -> true;
            default -> false;
        };
    }
}
