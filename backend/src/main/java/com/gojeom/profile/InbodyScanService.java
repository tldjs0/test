package com.gojeom.profile;

import com.gojeom.ai.AiException;
import com.gojeom.ai.OpenAiClient;
import com.gojeom.ai.dto.AiPayloads.InbodyOcrPayload;
import com.gojeom.ai.prompt.InbodyOcrPrompt;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.dto.InbodyScanDtos.InbodyScanRequest;
import com.gojeom.profile.dto.InbodyScanDtos.InbodyScanResponse;
import com.gojeom.profile.dto.InbodyScanDtos.ScanConfidence;
import com.gojeom.profile.entity.Inbody;
import com.gojeom.storage.ObjectKeyFactory;
import com.gojeom.storage.StorageService;
import com.gojeom.storage.UploadPurpose;
import com.gojeom.storage.deletion.StorageDeletionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 인바디 서류 스캔. 시안 08의 "카메라로 서류 스켄하기". (PRD F-03 · API.md §6.3)
 *
 * <p><b>이 응답은 저장되지 않는다.</b> 입력 폼을 채우는 용도일 뿐이며, 사용자가
 * 값을 확인·수정한 뒤 {@code POST /profiles} 또는 {@code PATCH /profiles/me}로
 * 저장해야 반영된다. (PRD G-8)
 *
 * <p><b>{@code @Transactional}이 없다.</b> DB를 건드리지 않는 데다 AI 호출이
 * 수 초 걸리므로 커넥션을 잡을 이유가 전혀 없다. (ARCHITECTURE.md L-4)
 *
 * <p>가드레일 후검증도 걸지 않는다 — 출력이 숫자 6개뿐이라 점수 표현이나
 * 금지어가 나올 자리가 없다. 대신 "추측 금지"를 프롬프트로 강하게 건다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InbodyScanService {

    private final OpenAiClient openAiClient;
    private final InbodyOcrPrompt prompt;
    private final StorageService storageService;
    private final ObjectKeyFactory objectKeyFactory;
    private final StorageDeletionService storageDeletionService;

    public InbodyScanResponse scan(UUID userId, InbodyScanRequest request) {
        String documentKey = request.documentKey();

        // 삭제 큐에 넣기 전에 소유권부터 확인한다. 그렇지 않으면 공격자가 남의 key를
        // 보내 그 객체를 삭제하게 만들 수 있다. (ARCHITECTURE.md §8)
        objectKeyFactory.assertOwned(documentKey, UploadPurpose.INBODY_DOCUMENT, userId);

        try {
            storageService.validateUploadedImage(
                    documentKey, UploadPurpose.INBODY_DOCUMENT, userId);

            String documentUrl = storageService.presignDownload(documentKey);

            InbodyOcrPayload payload;
            try {
                payload = openAiClient.complete(prompt.build(documentUrl));
            } catch (AiException e) {
                // key·URL을 로그에 남기지 않는다. (AGENTS.md 규칙 9)
                log.info("인바디 스캔 실패 code={}", e.errorCode().name());
                // 사용자에게는 "직접 입력해주세요"가 맞다. 분석권은 차감하지 않는다.
                throw new BusinessException(ErrorCode.INBODY_SCAN_FAILED);
            }

            if (payload.recognizedCount() == 0) {
                // 인바디 결과지가 아니거나 전혀 읽히지 않았다. (PRD §8.3)
                throw new BusinessException(ErrorCode.INBODY_SCAN_FAILED);
            }

            return new InbodyScanResponse(
                    new Inbody(payload.bodyWaterL(), payload.proteinKg(), payload.mineralKg(),
                            payload.bodyFatKg(), payload.skeletalMuscleKg(), payload.bmi()),
                    ScanConfidence.of(payload.recognizedCount()),
                    payload.unrecognized());
        } finally {
            // OCR 원본은 응답 성공 여부와 관계없이 더 이상 필요하지 않다.
            // DB에 key를 남기지 않는 기능이므로 영구 잔존하지 않게 삭제 큐에 기록한다.
            storageDeletionService.enqueue(documentKey);
        }
    }
}
