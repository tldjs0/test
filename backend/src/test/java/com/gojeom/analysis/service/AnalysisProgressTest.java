package com.gojeom.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 폴링 응답 매핑. 프론트의 폴링 중단 조건이 여기 달려 있다. (API.md C-4) */
class AnalysisProgressTest {

    @DisplayName("모든 상태에 진행률·문구가 있다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(AnalysisStatus.class)
    void 모든_상태를_다룬다(AnalysisStatus status) {
        assertThat(AnalysisProgress.percent(status)).isBetween(0, 100);
        assertThat(AnalysisProgress.message(status, null)).isNotBlank();
    }

    @Test
    @DisplayName("진행 구간이 단조 증가한다")
    void 진행률이_역행하지_않는다() {
        assertThat(AnalysisProgress.percent(AnalysisStatus.CREATED))
                .isLessThan(AnalysisProgress.percent(AnalysisStatus.EXTRACTING));
        assertThat(AnalysisProgress.percent(AnalysisStatus.EXTRACTING))
                .isLessThanOrEqualTo(50);
        assertThat(AnalysisProgress.percent(AnalysisStatus.GENERATING))
                .isBetween(50, 99);
        assertThat(AnalysisProgress.percent(AnalysisStatus.DONE)).isEqualTo(100);
    }

    @Test
    @DisplayName("KEYWORDS_READY에서는 폴링을 멈추지 않는다 (API.md C-4)")
    void 키워드_준비_상태에서_폴링을_유지한다() {
        assertThat(AnalysisProgress.pollAfterMs(AnalysisStatus.KEYWORDS_READY)).isNotNull();
    }

    @DisplayName("종료 상태에서만 pollAfterMs가 null이다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(AnalysisStatus.class)
    void 종료_상태에서만_폴링을_멈춘다(AnalysisStatus status) {
        assertThat(AnalysisProgress.pollAfterMs(status) == null).isEqualTo(status.isTerminal());
    }

    @Test
    @DisplayName("실패 문구는 ErrorCode가 소유한다")
    void 실패_문구는_에러코드에서_온다() {
        assertThat(AnalysisProgress.message(AnalysisStatus.FAILED, ErrorCode.ANALYSIS_TIMEOUT.name()))
                .isEqualTo(ErrorCode.ANALYSIS_TIMEOUT.message());
        assertThat(AnalysisProgress.message(AnalysisStatus.FAILED, "존재하지_않는_코드"))
                .isEqualTo(ErrorCode.AI_PROVIDER_ERROR.message());
    }

    @Test
    @DisplayName("좀비 정리 대상은 사용자 대기 상태를 포함하지 않는다")
    void 키워드_선택_중인_분석은_정리_대상이_아니다() {
        // KEYWORDS_READY에 시간 제한을 걸면 키워드를 고르는 사용자의 분석이 죽는다.
        assertThat(AnalysisStatus.KEYWORDS_READY.isInProgress()).isFalse();
        assertThat(AnalysisStatus.CREATED.isInProgress()).isTrue();
        assertThat(AnalysisStatus.EXTRACTING.isInProgress()).isTrue();
        assertThat(AnalysisStatus.GENERATING.isInProgress()).isTrue();
    }
}
