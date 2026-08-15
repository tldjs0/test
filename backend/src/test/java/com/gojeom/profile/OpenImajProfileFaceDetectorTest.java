package com.gojeom.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenImajProfileFaceDetectorTest {

    private final OpenImajProfileFaceDetector detector = new OpenImajProfileFaceDetector();

    @Test
    @DisplayName("디코딩할 수 없는 파일은 얼굴 없음으로 오인하지 않고 형식 오류로 처리한다")
    void 디코딩_실패() {
        assertThatThrownBy(() -> detector.count("not-an-image".getBytes()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("얼굴이 없는 정상 PNG에서는 0명을 반환한다")
    void 얼굴_없는_PNG() throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        assertThat(detector.count(output.toByteArray())).isZero();
    }
}
