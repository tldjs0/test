package com.gojeom.common.enums;

/**
 * AI가 추출한 고점 키워드의 분류. **4종이다.**
 *
 * <p>{@link Category}(3종)와 의도적으로 분리했다. 우선순위·루틴·결과 카테고리는
 * 피부·체형·건강 3종이지만, 키워드에는 얼굴 관련 라벨("다이아몬드형", "또렷한 눈매")이
 * 다수 나온다. 3종만 두면 AI가 이런 키워드를 HEALTH로 잘못 분류한다.
 * (2026-08-13 AI 스파이크에서 확인)
 *
 * <p>{@code FACE}는 여기서만 쓴다. 우선순위나 루틴 카테고리로 넘기지 않는다.
 */
public enum KeywordCategory {

    SKIN("피부"),
    FACE("얼굴형"),
    BODY("체형"),
    HEALTH("건강");

    private final String label;

    KeywordCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
