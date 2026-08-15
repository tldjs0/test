package com.gojeom.analysis.service;

import com.gojeom.ai.AiException;
import com.gojeom.ai.image.ImageEditService;
import com.gojeom.ai.prompt.ImageGenerationPrompt;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.service.AnalysisTxService.ImageContext;
import com.gojeom.common.config.AsyncConfig;
import com.gojeom.storage.StorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 비교 이미지 생성 파이프라인. (ARCHITECTURE.md §6.5 · PRD §8.1 [5])
 *
 * <p><b>만드는 것은 합성 이미지다.</b> 사용자 본인 사진을 기준으로 두고, 참고
 * 사진(추구미)의 <b>헤어스타일과 피부</b>를 입힌다. 얼굴 생김새는 사용자 것을
 * 그대로 지킨다. 경계는 {@link ImageGenerationPrompt} 참조.
 *
 * <p><b>참고 사진이 없으면 실행하지 않는다.</b> 합성할 대상이 없기 때문이다.
 * 그때 결과는 {@code image_status = SKIPPED}로 남는다.
 *
 * <p><b>텍스트 파이프라인과 완전히 분리되어 있다.</b> 실측 35초로 텍스트(2~4초)보다
 * 훨씬 느리고 실패율도 높다. 같은 풀을 쓰면 이미지 몇 건이 스레드를 붙잡아
 * 뒤따르는 분석 요청이 밀린다. 그래서 {@code imageExecutor}를 따로 쓴다. (§5.3)
 *
 * <p>이 단계가 실패해도 <b>분석은 이미 {@code DONE}이고 분석권도 차감됐다.</b>
 * 텍스트 결과만으로 결과 화면이 성립해야 한다는 것이 설계 전제다. (PRD §8.3)
 *
 * <p>트랜잭션이 없다. DB 접근은 {@link AnalysisTxService}가 짧게 잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagePipeline {

    /**
     * OpenAI에 함께 보내는 참고 사진 상한.
     *
     * <p>업로드는 5장까지 받지만 합성에는 앞의 2장만 쓴다. 장수가 늘수록 입력
     * 토큰과 지연이 늘고, <b>서로 다른 헤어스타일이 여러 장 들어오면 모델이
     * 무엇을 입힐지 흐려진다.</b> 참고는 적을수록 결과가 선명하다.
     */
    private static final int MAX_REFERENCES = 2;

    private final AnalysisTxService analysisTx;
    private final ImageEditService imageEditService;
    private final StorageService storageService;

    @Async(AsyncConfig.IMAGE_EXECUTOR)
    public void run(UUID analysisId) {
        ImageContext context = analysisTx.loadImageContext(analysisId).orElse(null);
        if (context == null) {
            log.info("비교 이미지 건너뜀 — 결과 또는 사진이 없다");
            analysisTx.markImageFailed(analysisId);
            return;
        }

        try {
            byte[] userPhoto = storageService.download(context.photoKey());

            List<byte[]> references = new ArrayList<>();
            for (String key : context.referenceKeys().stream().limit(MAX_REFERENCES).toList()) {
                references.add(storageService.download(key));
            }

            byte[] png = imageEditService.generate(
                    analysisId,
                    ImageGenerationPrompt.build(context.keywordLabels(), references.size()),
                    userPhoto,
                    references);

            String key = AnalysisResult.peakImageKey(context.userId(), context.resultId());
            storageService.upload(key, png, "image/png");
            analysisTx.markImageDone(analysisId, key);

        } catch (AiException e) {
            // 정책 거부는 정상 시나리오다. 예외로 취급하지 않는다. (§6.5)
            log.info("비교 이미지 생성 실패 code={}", e.errorCode().name());
            analysisTx.markImageFailed(analysisId);
        } catch (RuntimeException e) {
            log.error("비교 이미지 생성 중 예상 못 한 오류", e);
            analysisTx.markImageFailed(analysisId);
        }
    }
}
