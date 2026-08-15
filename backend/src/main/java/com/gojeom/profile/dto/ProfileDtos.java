package com.gojeom.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gojeom.common.enums.Category;
import com.gojeom.profile.entity.Inbody;
import com.gojeom.profile.entity.ProfileAnalysisSummary;
import com.gojeom.profile.validation.Priorities;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 프로필 요청·응답 DTO. (API.md §6.3) */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * 시안 05~08의 단일 스크롤 폼이 한 번에 제출된다.
     * 사진·우선순위·키·몸무게가 필수, 수면·인바디가 선택이다.
     */
    public record ProfileCreateRequest(
            @NotBlank(message = "사진을 등록해주세요.")
            String photoKey,

            @Priorities
            List<Category> priorities,

            @NotNull(message = "키를 입력해주세요.")
            @Min(value = 100, message = "키는 100~250cm 범위로 입력해주세요.")
            @Max(value = 250, message = "키는 100~250cm 범위로 입력해주세요.")
            Short heightCm,

            @NotNull(message = "몸무게를 입력해주세요.")
            @DecimalMin(value = "30.0", message = "몸무게는 30~200kg 범위로 입력해주세요.")
            @DecimalMax(value = "200.0", message = "몸무게는 30~200kg 범위로 입력해주세요.")
            BigDecimal weightKg,

            @DecimalMin(value = "0.0", message = "수면 시간은 0~14시간 범위로 입력해주세요.")
            @DecimalMax(value = "14.0", message = "수면 시간은 0~14시간 범위로 입력해주세요.")
            BigDecimal sleepHours,

            Inbody inbody) {
    }

    /** 신체 정보만 수정한다. 사진과 우선순위는 각각 별도 경로로 바꾼다. */
    public record ProfileUpdateRequest(
            @DecimalMin(value = "30.0", message = "몸무게는 30~200kg 범위로 입력해주세요.")
            @DecimalMax(value = "200.0", message = "몸무게는 30~200kg 범위로 입력해주세요.")
            BigDecimal weightKg,

            @DecimalMin(value = "0.0", message = "수면 시간은 0~14시간 범위로 입력해주세요.")
            @DecimalMax(value = "14.0", message = "수면 시간은 0~14시간 범위로 입력해주세요.")
            BigDecimal sleepHours,

            Inbody inbody) {
    }

    public record PrioritiesUpdateRequest(
            @Priorities
            List<Category> priorities) {
    }

    public record ProfileResponse(
            UUID profileId,
            String photoUrl,
            List<Category> priorities,
            short heightCm,
            BigDecimal weightKg,
            BigDecimal sleepHours,
            Inbody inbody,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            ProfileAnalysisSummary analysisSummary,
            OffsetDateTime createdAt) {
    }
}
