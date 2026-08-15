package com.gojeom.common.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * created_at / updated_at 자동 관리. (ERD.md D-3)
 *
 * <p>기본 {@code DateTimeProvider}는 {@code LocalDateTime}을 공급해
 * {@code OffsetDateTime} 필드에 넣지 못한다. UTC 기준 {@code OffsetDateTime}을
 * 직접 공급해 "UTC 저장 / KST 표시" 규칙을 코드로 못박는다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = JpaConfig.AUDITING_DATE_TIME_PROVIDER)
public class JpaConfig {

    public static final String AUDITING_DATE_TIME_PROVIDER = "auditingDateTimeProvider";

    @Bean(AUDITING_DATE_TIME_PROVIDER)
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
