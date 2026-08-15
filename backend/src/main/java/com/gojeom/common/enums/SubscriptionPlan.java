package com.gojeom.common.enums;

/** 요금제. 가입 시 {@link #TRIAL}이 자동 생성된다. (PRD F-12) */
public enum SubscriptionPlan {

    /** 한 달 무료 체험 · 분석권 1회 */
    TRIAL,

    /** 월 8,900원 */
    MONTHLY,

    /** 연 89,000원 */
    YEARLY
}
