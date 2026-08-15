package com.gojeom.profile.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;

/**
 * 인바디 6종. 시안 08의 선택 정보 영역과 1:1 대응한다.
 *
 * <p>각 항목은 개별 선택이라 일부만 채워도 저장된다. 나머지는 null이다.
 *
 * <p><b>{@code bmi}는 무단위다.</b> 시안에 `kg`으로 표기된 것은 오류이므로
 * UI에서 단위 접미를 붙이지 않는다. (ERD.md §5.2)
 */
public record Inbody(
        BigDecimal bodyWaterL,
        BigDecimal proteinKg,
        BigDecimal mineralKg,
        BigDecimal bodyFatKg,
        BigDecimal skeletalMuscleKg,
        BigDecimal bmi) {

    public static Inbody empty() {
        return new Inbody(null, null, null, null, null, null);
    }

    /**
     * 하나도 입력하지 않았으면 아예 저장하지 않는다.
     *
     * <p>{@code @JsonIgnore}가 없으면 Jackson이 이 메서드를 {@code empty} 속성으로
     * 보고 JSONB에 함께 저장한다. 파생값이 원본 데이터에 섞이면 안 된다.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return bodyWaterL == null && proteinKg == null && mineralKg == null
                && bodyFatKg == null && skeletalMuscleKg == null && bmi == null;
    }
}
