package com.gojeom;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 공통 베이스.
 *
 * <p>컨테이너를 static으로 두어 테스트 클래스 간에 재사용한다.
 * 클래스마다 새로 띄우면 전체 테스트 시간이 급격히 늘어난다.
 *
 * <p><b>{@code @Tag("integration")}은 상속된다.</b> 이 클래스를 상속하는 테스트는
 * {@code ./gradlew test}에서 자동으로 제외되고 {@code ./gradlew integrationTest}에서만
 * 돈다. Docker가 없는 환경에서 단위 테스트까지 같이 죽는 것을 막기 위해서다.
 * (build.gradle 참조)
 */
@Tag("integration")
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gojeom")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
