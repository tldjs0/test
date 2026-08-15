package com.gojeom.profile.validation;

import com.gojeom.common.enums.Category;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.EnumSet;
import java.util.List;

public class PrioritiesValidator implements ConstraintValidator<Priorities, List<Category>> {

    @Override
    public boolean isValid(List<Category> value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        // 순서가 곧 순위이므로 개수와 중복 여부가 곧 유효성이다.
        // 3종을 모두 담으면서 길이가 3이면 자동으로 중복이 없다.
        return value.size() == Category.values().length
                && EnumSet.copyOf(value).size() == value.size();
    }
}
