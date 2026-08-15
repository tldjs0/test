package com.gojeom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 분석 파이프라인 운영값. (ARCHITECTURE.md §5)
 *
 * @param timeoutSeconds    프론트 폴링 상한과 맞춘다. (PRD §8.3)
 * @param sweepAfterMinutes 이 시간 이상 진행 상태에 머문 분석을 FAILED로 전환한다.
 *                          앱 재시작으로 유실된 @Async 작업을 정리하는 안전망이다.
 */
@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(int timeoutSeconds, int sweepAfterMinutes) {
}
