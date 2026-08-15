package com.gojeom.profile.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 우선순위 배열 검증. <b>정확히 3개, 중복 없이, 3종 전부</b>.
 *
 * <p>시안 06은 피부·체형·건강 3종에 1·2·3순위를 모두 매기는 방식이다.
 * 하나만 고르거나 일부만 채우는 UI가 아니다. (PRD F-02)
 */
@Documented
@Constraint(validatedBy = PrioritiesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Priorities {

    String message() default "피부·체형·건강 세 가지의 순위를 모두 정해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
