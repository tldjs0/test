package com.gojeom.analysis.service;

import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.common.config.AnalysisProperties;
import com.gojeom.common.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좀비 분석 정리. (ARCHITECTURE.md §5.4 · D2-7)
 *
 * <p><b>애플리케이션이 재시작되면 진행 중이던 {@code @Async} 작업은 사라진다.</b>
 * DB에는 {@code EXTRACTING}인 행만 남고, 클라이언트는 영영 오지 않을 응답을
 * 60초 동안 기다리다 타임아웃 화면을 본다. 이 스케줄러가 그 행들을 정리한다.
 *
 * <p>{@code ANALYSIS_TIMEOUT}은 <b>분석권 미차감</b> 코드다. 결과를 받지 못했으므로
 * 사용자가 값을 치를 이유가 없다. (PRD §8.3 · ARCHITECTURE.md §7)
 *
 * <p><b>MVP 전제 — 단일 인스턴스.</b> 여러 대로 늘리면 인스턴스마다 같은 행을
 * 집으므로 ShedLock 같은 잠금이 필요하다. (ARCHITECTURE.md §11 · B-1)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisSweeper {

    /**
     * 비교 이미지 생성 상한.
     *
     * <p>분석 본체(3분)와 따로 둔다. 이미지는 실측 35초라 텍스트 단계보다 훨씬 느려,
     * 같은 기준을 쓰면 정상 생성 중인 건을 실패로 돌릴 수 있다.
     */
    private static final int IMAGE_TIMEOUT_MINUTES = 8;

    private final AnalysisRepository analysisRepository;
    private final AnalysisResultRepository resultRepository;
    private final AnalysisProperties properties;

    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void sweepStale() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime threshold = now.minusMinutes(properties.sweepAfterMinutes());

        int swept = analysisRepository.failStale(threshold, now, ErrorCode.ANALYSIS_TIMEOUT.name());
        if (swept > 0) {
            log.warn("좀비 분석 {}건을 ANALYSIS_TIMEOUT으로 정리했다", swept);
        }

        int images = resultRepository.failStaleImages(now.minusMinutes(IMAGE_TIMEOUT_MINUTES));
        if (images > 0) {
            log.warn("멈춘 비교 이미지 {}건을 FAILED로 정리했다", images);
        }
    }
}
