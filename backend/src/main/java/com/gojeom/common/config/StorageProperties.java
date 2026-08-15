package com.gojeom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 호환 오브젝트 스토리지. (ARCHITECTURE.md §8)
 *
 * <p>얼굴 사진은 생체정보에 준하므로 리전은 국내로 둔다. (PRD §10)
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Presign presign,
        long maxUploadBytes) {

    public record Presign(int uploadSeconds, int downloadSeconds) {
    }
}
