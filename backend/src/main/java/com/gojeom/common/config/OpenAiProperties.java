package com.gojeom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 설정. (ARCHITECTURE.md §6.1)
 *
 * <p><b>모델 ID를 코드에 하드코딩하지 않는다.</b> 여기서 핀 고정하고,
 * 실제 사용한 값은 호출마다 {@code ai_jobs.model}에 기록한다.
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        Model model,
        Timeout timeout,
        int maxRetries) {

    public record Model(String text, String image) {
    }

    public record Timeout(int connectSeconds, int readSeconds) {
    }
}
