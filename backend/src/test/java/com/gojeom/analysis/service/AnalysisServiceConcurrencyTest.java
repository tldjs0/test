package com.gojeom.analysis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gojeom.analysis.dto.AnalysisDtos.AnalysisCreateRequest;
import com.gojeom.analysis.entity.Analysis;
import com.gojeom.analysis.repository.AnalysisKeywordRepository;
import com.gojeom.analysis.repository.AnalysisReferenceImageRepository;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.storage.StorageService;
import com.gojeom.subscription.entity.Subscription;
import com.gojeom.subscription.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceConcurrencyTest {

    @Mock private ResultAssembler resultAssembler;
    @Mock private AnalysisRepository analysisRepository;
    @Mock private AnalysisKeywordRepository keywordRepository;
    @Mock private AnalysisReferenceImageRepository referenceImageRepository;
    @Mock private AnalysisResultRepository resultRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AnalysisService analysisService;

    @Test
    @DisplayName("진행 중인 분석이 있으면 새 분석과 AI 작업을 만들지 않는다")
    void 진행_중_분석_중복_차단() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = org.mockito.Mockito.mock(Subscription.class);
        when(subscriptionRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(subscription));
        when(subscription.canAnalyze(any(OffsetDateTime.class))).thenReturn(true);
        Analysis active = org.mockito.Mockito.mock(Analysis.class);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(active.getStatus()).thenReturn(com.gojeom.common.enums.AnalysisStatus.KEYWORDS_READY);
        when(analysisRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), anyCollection())).thenReturn(Optional.of(active));

        AnalysisCreateRequest request = new AnalysisCreateRequest(
                "건강하고 자연스러운 분위기를 원해요", List.of(), null);

        assertThatThrownBy(() -> analysisService.create(userId, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.ANALYSIS_INVALID_STATE));

        verify(analysisRepository, never()).save(any(Analysis.class));
        verifyNoInteractions(profileRepository, storageService, eventPublisher);
    }
}
