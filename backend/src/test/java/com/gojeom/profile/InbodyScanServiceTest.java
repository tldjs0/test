package com.gojeom.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gojeom.ai.AiException;
import com.gojeom.ai.OpenAiClient;
import com.gojeom.ai.OpenAiRequest;
import com.gojeom.ai.dto.AiPayloads.InbodyOcrPayload;
import com.gojeom.ai.prompt.InbodyOcrPrompt;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.dto.InbodyScanDtos.InbodyScanRequest;
import com.gojeom.profile.dto.InbodyScanDtos.ScanConfidence;
import com.gojeom.storage.ObjectKeyFactory;
import com.gojeom.storage.StorageService;
import com.gojeom.storage.UploadPurpose;
import com.gojeom.storage.deletion.StorageDeletionService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InbodyScanServiceTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final InbodyOcrPrompt prompt = mock(InbodyOcrPrompt.class);
    private final StorageService storageService = mock(StorageService.class);
    private final StorageDeletionService storageDeletionService = mock(StorageDeletionService.class);
    private final ObjectKeyFactory objectKeyFactory = new ObjectKeyFactory();

    private InbodyScanService service;
    private UUID userId;
    private String documentKey;
    private OpenAiRequest<InbodyOcrPayload> aiRequest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new InbodyScanService(
                openAiClient, prompt, storageService, objectKeyFactory, storageDeletionService);
        userId = UUID.randomUUID();
        documentKey = "inbody/%s/document.jpg".formatted(userId);
        aiRequest = mock(OpenAiRequest.class);

        String documentUrl = "https://storage.example/inbody-document";
        when(storageService.presignDownload(documentKey)).thenReturn(documentUrl);
        when(prompt.build(documentUrl)).thenReturn(aiRequest);
    }

    @Test
    @DisplayName("OCR 성공 후 인바디 원본을 삭제 큐에 넣는다")
    void 성공해도_원본_삭제() {
        when(openAiClient.complete(aiRequest)).thenReturn(fullPayload());

        var response = service.scan(userId, new InbodyScanRequest(documentKey));

        assertThat(response.confidence()).isEqualTo(ScanConfidence.HIGH);
        verify(storageDeletionService).enqueue(documentKey);
    }

    @Test
    @DisplayName("AI 호출 실패 시에도 인바디 원본을 삭제 큐에 넣는다")
    void AI_실패해도_원본_삭제() {
        when(openAiClient.complete(aiRequest)).thenThrow(
                new AiException(ErrorCode.AI_PROVIDER_ERROR, false, "provider error", null));

        assertThatThrownBy(() -> service.scan(userId, new InbodyScanRequest(documentKey)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INBODY_SCAN_FAILED));

        verify(storageDeletionService).enqueue(documentKey);
    }

    @Test
    @DisplayName("아무 항목도 인식하지 못해도 인바디 원본을 삭제 큐에 넣는다")
    void 인식_실패해도_원본_삭제() {
        when(openAiClient.complete(aiRequest)).thenReturn(
                new InbodyOcrPayload(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.scan(userId, new InbodyScanRequest(documentKey)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INBODY_SCAN_FAILED));

        verify(storageDeletionService).enqueue(documentKey);
    }

    @Test
    @DisplayName("서버 검증에서 거부된 소유 객체도 삭제 큐에 넣는다")
    void 검증_실패_원본_삭제() {
        BusinessException failure = new BusinessException(ErrorCode.VALIDATION_ERROR);
        org.mockito.Mockito.doThrow(failure).when(storageService)
                .validateUploadedImage(documentKey, UploadPurpose.INBODY_DOCUMENT, userId);

        assertThatThrownBy(() -> service.scan(userId, new InbodyScanRequest(documentKey)))
                .isSameAs(failure);

        verify(storageDeletionService).enqueue(documentKey);
    }

    @Test
    @DisplayName("남의 key는 삭제 큐에 넣지 않는다")
    void 타인_원본은_삭제하지_않음() {
        UUID otherUserId = UUID.randomUUID();
        String otherKey = "inbody/%s/document.jpg".formatted(otherUserId);

        assertThatThrownBy(() -> service.scan(userId, new InbodyScanRequest(otherKey)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN_RESOURCE));

        verify(storageDeletionService, never()).enqueue(otherKey);
        verify(storageService, never()).validateUploadedImage(
                otherKey, UploadPurpose.INBODY_DOCUMENT, userId);
    }

    private InbodyOcrPayload fullPayload() {
        return new InbodyOcrPayload(
                new BigDecimal("32.5"),
                new BigDecimal("8.7"),
                new BigDecimal("3.1"),
                new BigDecimal("14.2"),
                new BigDecimal("24.1"),
                new BigDecimal("19.5"));
    }
}
