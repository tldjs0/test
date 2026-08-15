package com.gojeom.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로필 커밋 이후에 AI 분석을 건다.
 *
 * <p>{@code @TransactionalEventListener}와 {@code @Async}를 한 메서드에 겹치지 않는다.
 * 여기서는 커밋 시점만 잡고, 실제 비동기 전환은 {@link ProfileAnalysisPipeline}의
 * {@code @Async}가 담당한다. 빈이 나뉘어 있어야 프록시를 탄다.
 */
@Component
@RequiredArgsConstructor
public class ProfileEventListener {

    private final ProfileAnalysisPipeline pipeline;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileCreated(ProfileCreatedEvent event) {
        pipeline.run(event.profileId());
    }
}
