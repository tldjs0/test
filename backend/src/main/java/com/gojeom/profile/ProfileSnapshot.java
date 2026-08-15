package com.gojeom.profile;

import com.gojeom.common.enums.Category;
import com.gojeom.profile.entity.Inbody;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 트랜잭션 밖으로 들고 나가는 프로필 값 묶음.
 *
 * <p>엔티티를 그대로 넘기면 트랜잭션 밖에서 지연 로딩이 터진다. AI 호출은
 * 트랜잭션 밖에서 일어나므로 필요한 값만 복사해 나간다. (ARCHITECTURE.md §5.2)
 */
public record ProfileSnapshot(
        UUID profileId,
        String photoKey,
        List<Category> priorities,
        short heightCm,
        BigDecimal weightKg,
        BigDecimal sleepHours,
        Inbody inbody) {
}
