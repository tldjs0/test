package com.gojeom.analysis.entity;

import com.gojeom.common.enums.Category;

/**
 * 결과지 ④ "이렇게 바꾸면 가까워져요" 1건. (ERD.md §5.5)
 *
 * <p>정확히 3건이며 {@code SKIN}·{@code BODY}·{@code HEALTH} 각 1건이다.
 * 배열 순서는 {@code profiles.priorities} 순서를 따른다.
 *
 * <p>{@code category}는 <b>3종</b>이다. 얼굴형은 여기 오지 않는다.
 */
public record CategoryChange(Category category, String description) {
}
