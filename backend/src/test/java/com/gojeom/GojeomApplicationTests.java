package com.gojeom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로딩 확인.
 *
 * <p>Testcontainers로 실제 PostgreSQL을 띄운다. H2는 JSONB를 지원하지 않아
 * profiles.priorities · analysis_results.category_changes 등을 검증할 수 없다.
 * (ARCHITECTURE.md §12)
 */
@SpringBootTest
class GojeomApplicationTests extends IntegrationTestSupport {

    @Test
    void contextLoads() {
    }
}
