package com.gojeom.common.enums;

/**
 * 로그인 수단.
 *
 * <p>{@code GOOGLE}은 스키마와 API 계약에 이미 반영되어 있고 구현만 마지막에 붙인다.
 * (TASKS.md D3-6)
 */
public enum AuthProvider {

    /** 이메일 + 비밀번호 */
    LOCAL,

    /** Google 계정. {@code passwordHash}가 null이다. */
    GOOGLE
}
