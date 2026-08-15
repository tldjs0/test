package com.gojeom.profile.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gojeom.common.enums.Category;
import com.gojeom.profile.dto.ProfileDtos.ProfileResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 프로필 응답 직렬화가 API.md 계약과 맞는지 검증한다. */
class ProfileDtosSerializationTest {

    /** 운영의 {@code default-property-inclusion: non_null} 설정을 재현한다. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    @DisplayName("AI 분석 전에도 analysisSummary 키가 null로 남는다")
    void 분석_요약의_null_키가_남는다() throws Exception {
        ProfileResponse response = new ProfileResponse(
                UUID.randomUUID(),
                "https://example.com/profile.jpg",
                List.of(Category.SKIN, Category.BODY, Category.HEALTH),
                (short) 165,
                new BigDecimal("55.0"),
                null,
                null,
                null,
                OffsetDateTime.now());

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"analysisSummary\":null");
    }
}
