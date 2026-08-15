package com.gojeom.common.enums;

/**
 * 피부 · 체형 · 건강 3종.
 *
 * <p>우선순위(profiles.priorities), 루틴 카테고리, 결과의 categoryChanges가
 * 이 하나의 enum을 공유한다.
 *
 * <p>키워드 분류는 얼굴형이 필요해 {@link KeywordCategory}(4종)를 따로 쓴다.
 * 여기에 FACE를 추가하지 말 것 — 우선순위와 루틴에는 얼굴형이 없다.
 *
 * @see <a href="../../../../../../../../PRD.md">PRD F-02</a>
 */
public enum Category {

    SKIN("피부"),
    BODY("체형"),
    HEALTH("건강");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
