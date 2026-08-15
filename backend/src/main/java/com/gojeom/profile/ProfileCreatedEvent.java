package com.gojeom.profile;

import java.util.UUID;

/**
 * 프로필이 저장됐다. {@code AFTER_COMMIT}에서 받아 프로필 AI 분석을 시작한다.
 *
 * <p>커밋 전에 시작하면 비동기 스레드가 <b>아직 없는 행</b>을 조회한다.
 * (ARCHITECTURE.md §5.2)
 */
public record ProfileCreatedEvent(UUID profileId) {
}
