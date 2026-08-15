package com.gojeom.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageContentInspectorTest {

    private final ImageContentInspector inspector = new ImageContentInspector();

    @Test
    @DisplayName("허용한 네 이미지 형식의 바이트 시그니처를 구분한다")
    void 이미지_형식_감지() {
        assertThat(inspector.detect(bytes(0xFF, 0xD8, 0xFF)))
                .isEqualTo("image/jpeg");
        assertThat(inspector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .isEqualTo("image/png");
        assertThat(inspector.detect("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("image/webp");
        assertThat(inspector.detect(bytes(0, 0, 0, 0, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')))
                .isEqualTo("image/heic");
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 감지하지 않는다")
    void 일반_파일_거부() {
        assertThat(inspector.detect("plain text!!".getBytes(StandardCharsets.US_ASCII))).isNull();
    }

    private byte[] bytes(int... values) {
        byte[] padded = new byte[Math.max(12, values.length)];
        for (int i = 0; i < values.length; i++) {
            padded[i] = (byte) values[i];
        }
        return padded;
    }
}
