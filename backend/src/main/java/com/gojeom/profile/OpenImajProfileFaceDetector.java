package com.gojeom.profile;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.openimaj.image.FImage;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.processing.face.detection.HaarCascadeDetector;
import org.springframework.stereotype.Component;

/** OpenIMAJ에 내장된 frontal-face cascade로 얼굴 후보를 센다. */
@Component
public class OpenImajProfileFaceDetector implements ProfileFaceDetector {

    private static final int MIN_FACE_PIXELS = 40;
    private static final int DETECTION_MAX_SIDE = 1600;

    private final HaarCascadeDetector detector;

    public OpenImajProfileFaceDetector() {
        this.detector = HaarCascadeDetector.BuiltInCascade.frontalface_alt2.load();
        this.detector.setMinSize(MIN_FACE_PIXELS);
    }

    @Override
    public int count(byte[] imageBytes) {
        FImage image = decodeForDetection(imageBytes);

        // OpenIMAJ detector 내부 객체는 불변 계약이 명시돼 있지 않아 동시 접근을 막는다.
        synchronized (detector) {
            return detector.detectFaces(image).size();
        }
    }

    private FImage decodeForDetection(byte[] imageBytes) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(imageBytes);
             ImageInputStream input = ImageIO.createImageInputStream(bytes)) {
            if (input == null) {
                throw unreadableImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadableImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int sample = Math.max(1,
                        (Math.max(width, height) + DETECTION_MAX_SIDE - 1) / DETECTION_MAX_SIDE);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage buffered = reader.read(0, param);
                return ImageUtilities.createFImage(buffered);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw unreadableImage();
        }
    }

    private BusinessException unreadableImage() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                Map.of("file", "서버에서 읽을 수 없는 이미지 형식이에요."));
    }
}
