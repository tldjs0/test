package com.gojeom.common.enums;

/** 목표 상태. 서랍의 "현재 진행중인 목표" 섹션이 {@code ACTIVE}로 판정한다. (ERD.md §3.8) */
public enum RoutineStatus {

    ACTIVE,
    COMPLETED,
    CANCELED
}
