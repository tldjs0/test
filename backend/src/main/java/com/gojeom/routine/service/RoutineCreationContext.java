package com.gojeom.routine.service;

import com.gojeom.common.enums.Category;
import java.util.List;
import java.util.UUID;

/**
 * 목표 생성에 필요한 값 묶음. 트랜잭션 밖으로 나간다.
 *
 * <p>AI 호출이 4~6초 걸리므로 그 사이 DB 커넥션을 잡고 있으면 안 된다.
 * 엔티티가 아니라 값만 복사해 나간다. (ARCHITECTURE.md L-4 · §5.2)
 *
 * @param resultDigest 경로 A에서만 채워진다. 결과지를 프롬프트용 텍스트로 펼친 것
 */
public record RoutineCreationContext(
        UUID analysisResultId,
        List<Category> priorities,
        String profileFacts,
        String resultDigest) {
}
