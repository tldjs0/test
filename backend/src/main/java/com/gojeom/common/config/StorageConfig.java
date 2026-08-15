package com.gojeom.common.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트.
 *
 * <p>AWS 정식 엔드포인트일 때는 {@code endpointOverride}를 걸지 않는다. SDK가 리전에서
 * 알아서 만든다. {@code storage.endpoint}는 MinIO·NCP 같은 S3 호환 스토리지로
 * 갈아탈 때를 위해 남겨둔 설정이다.
 */
@Configuration
public class StorageConfig {

    private static final String AWS_HOST_SUFFIX = "amazonaws.com";

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        applyCustomEndpoint(properties, builder::endpointOverride);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        applyCustomEndpoint(properties, builder::endpointOverride);
        return builder.build();
    }

    private StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private void applyCustomEndpoint(StorageProperties properties, java.util.function.Consumer<URI> setter) {
        String endpoint = properties.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        URI uri = URI.create(endpoint);
        if (uri.getHost() != null && uri.getHost().endsWith(AWS_HOST_SUFFIX)) {
            return; // AWS는 SDK 기본값을 쓴다
        }
        setter.accept(uri);
    }
}
