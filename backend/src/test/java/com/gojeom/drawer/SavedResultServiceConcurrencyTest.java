package com.gojeom.drawer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.repository.AnalysisResultRepository;
import com.gojeom.analysis.service.AnalysisService;
import com.gojeom.analysis.service.ResultAssembler;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
import com.gojeom.drawer.entity.SavedResult;
import com.gojeom.drawer.repository.SavedResultRepository;
import com.gojeom.routine.repository.RoutineRepository;
import com.gojeom.routine.repository.RoutineTaskRepository;
import com.gojeom.storage.StorageService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SavedResultServiceConcurrencyTest {

    @Mock private SavedResultRepository savedResultRepository;
    @Mock private AnalysisResultRepository analysisResultRepository;
    @Mock private RoutineRepository routineRepository;
    @Mock private RoutineTaskRepository routineTaskRepository;
    @Mock private AnalysisService analysisService;
    @Mock private ResultAssembler resultAssembler;
    @Mock private StorageService storageService;

    @InjectMocks private SavedResultService savedResultService;

    @Test
    @DisplayName("동시 서랍 저장의 결과 유니크 충돌은 409 오류로 변환한다")
    void 동시_서랍_저장_중복_변환() {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        AnalysisResult result = org.mockito.Mockito.mock(AnalysisResult.class);
        when(result.getId()).thenReturn(resultId);
        when(analysisService.requireCompletedResult(userId, analysisId)).thenReturn(result);
        when(savedResultRepository.existsByAnalysisResultId(resultId)).thenReturn(false);
        when(savedResultRepository.saveAndFlush(any(SavedResult.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> savedResultService.save(userId, analysisId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_INVALID_STATE);
                    assertThat(exception.details()).isEqualTo(
                            java.util.Map.of("savedResult", "이미 서랍에 저장된 결과예요."));
                });
    }
}
