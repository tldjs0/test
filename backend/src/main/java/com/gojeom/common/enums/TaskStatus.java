package com.gojeom.common.enums;

/**
 * 태스크 수행 상태.
 *
 * <p>{@code MISSED}는 <b>사용자가 고를 수 있는 값이 아니다.</b> 미수행 재배치 로직이
 * 미설계라(PRD O-9) 상태값만 정의해두고 아무도 이 값을 쓰지 않는다.
 * 사용자 액션은 {@code PENDING} ↔ {@code DONE} 토글뿐이다.
 *
 * <p>사용자를 실패로 규정하는 표현을 쓰지 않는다. (PRD F-10 · ERD.md §3.9)
 */
public enum TaskStatus {

    PENDING,
    DONE,

    /** 정의만 되어 있다. 현재 어떤 코드도 이 값으로 전이시키지 않는다. */
    MISSED;

    /** 사용자가 직접 지정할 수 있는 값인지. */
    public boolean isUserSelectable() {
        return this == PENDING || this == DONE;
    }
}
