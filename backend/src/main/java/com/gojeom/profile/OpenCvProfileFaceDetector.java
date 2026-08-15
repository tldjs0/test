package com.gojeom.profile;

import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.stereotype.Component;

/** OpenCV frontal-face cascade로 프로필 사진의 얼굴 후보를 센다. */
@Component
public class OpenCvProfileFaceDetector implements ProfileFaceDetector {

    private static final int MIN_FACE_PIXELS = 40;
    private static final int DETECTION_MAX_SIDE = 1600;
    private static final String CASCADE_RESOURCE =
            "/org/openimaj/image/objectdetection/haar/haarcascade_frontalface_alt2.xml";

    private final CascadeClassifier detector;

    public OpenCvProfileFaceDetector() {
        OpenCV.loadLocally();
        this.detector = loadCascade();
    }

    @Override
    public int count(byte[] imageBytes) {
        BufferedImage buffered = decodeForDetection(imageBytes);
        Mat grayscale = toGrayscale(buffered);
        MatOfRect faces = new MatOfRect();
        try {
            Imgproc.equalizeHist(grayscale, grayscale);
            synchronized (detector) {
                detector.detectMultiScale(
                        grayscale,
                        faces,
                        1.1,
                        3,
                        0,
                        new Size(MIN_FACE_PIXELS, MIN_FACE_PIXELS),
                        new Size());
            }
            return faces.toArray().length;
        } finally {
            faces.release();
            grayscale.release();
        }
    }

    private BufferedImage decodeForDetection(byte[] imageBytes) {
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
                if (buffered == null) {
                    throw unreadableImage();
                }
                return buffered;
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

    private Mat toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] pixels = new byte[width * height];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int green = (rgb >>> 8) & 0xff;
                int blue = rgb & 0xff;
                pixels[offset++] = (byte) ((77 * red + 150 * green + 29 * blue) >>> 8);
            }
        }

        Mat grayscale = new Mat(height, width, CvType.CV_8UC1);
        grayscale.put(0, 0, pixels);
        return grayscale;
    }

    private CascadeClassifier loadCascade() {
        try (InputStream cascade = OpenCvProfileFaceDetector.class.getResourceAsStream(CASCADE_RESOURCE)) {
            if (cascade == null) {
                throw new IllegalStateException("얼굴 감지 cascade 리소스를 찾을 수 없습니다.");
            }
            Path extracted = Files.createTempFile("gojeom-frontal-face-", ".xml");
            Files.copy(cascade, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();

            CascadeClassifier classifier = new CascadeClassifier(extracted.toString());
            if (classifier.empty()) {
                throw new IllegalStateException("얼굴 감지 cascade를 초기화할 수 없습니다.");
            }
            return classifier;
        } catch (IOException exception) {
            throw new IllegalStateException("얼굴 감지 cascade를 준비할 수 없습니다.", exception);
        }
    }

    private BusinessException unreadableImage() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                Map.of("file", "서버에서 읽을 수 없는 이미지 형식이에요."));
    }
}
