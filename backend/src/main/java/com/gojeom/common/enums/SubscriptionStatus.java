package com.gojeom.common.enums;

/**
 * 구독 상태.
 *
 * <p>만료·해지 후에도 저장된 결과와 목표는 <b>열람 가능</b>하다.
 * 신규 분석과 목표 생성만 차단한다. (PRD F-12)
 */
public enum SubscriptionStatus {

    ACTIVE,
    EXPIRED,
    CANCELED
}
