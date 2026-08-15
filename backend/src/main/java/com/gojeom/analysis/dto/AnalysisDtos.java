package com.gojeom.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.ImageStatus;
import com.gojeom.common.enums.KeywordCategory;
import com.gojeom.common.enums.ResultViewState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 고점 분석 API 계약. (API.md §6.4)
 *
 * <p>응답 레코드 일부에 {@code @JsonInclude(ALWAYS)}가 붙어 있다.
 * 전역 설정이 {@code non_null}이라 그냥 두면 <b>null 필드가 응답에서 사라진다.</b>
 * API.md가 {@code "failureCode": null}처럼 null을 명시한 자리는 키가 남아 있어야
 * 프론트가 "아직 없음"과 "필드 자체가 없음"을 구분할 필요가 없다.
 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    // ------------------------------------------------------------ POST /analyses

    /**
     * @param referenceImageKeys 생략 가능. 첨부하면 키워드 추출이 분위기 요소를 함께 읽는다
     */
    public record AnalysisCreateRequest(
            @NotBlank(message = "고점을 입력해주세요.")
            @Size(min = 10, max = 500, message = "10자 이상 500자 이하로 적어주세요.")
            String inputText,

            // 상한은 계약에 없지만 서버가 건다. 무제한이면 한 요청이 토큰과 지연을
            // 얼마든지 끌어올릴 수 있다. 시안의 썸네일 스트립도 이 정도면 충분하다.
            @Size(max = 5, message = "참고 사진은 5장까지 첨부할 수 있어요.")
            List<String> referenceImageKeys,

            /** "새로 분석하기"로 만든 분석이면 원본 분석 ID. (PRD R-1) */
            UUID retriedFrom) {

        public List<String> safeReferenceImageKeys() {
            return referenceImageKeys == null ? List.of() : referenceImageKeys;
        }
    }

    /** {@code 202}. 작업을 등록했다는 뜻이지 완료가 아니다. */
    public record AnalysisAcceptedResponse(UUID analysisId, AnalysisStatus status, long pollAfterMs) {
    }

    // ------------------------------------------------------------ GET /analyses/{id}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AnalysisStatusResponse(
            UUID analysisId,
            AnalysisStatus status,
            ImageStatus imageStatus,
            int progress,
            String message,
            String failureCode,
            Long pollAfterMs) {
    }

    // ------------------------------------------------------------ 키워드

    public record KeywordListResponse(
            UUID analysisId,
            int minSelect,
            int maxSelect,
            List<KeywordItem> keywords) {
    }

    public record KeywordItem(
            UUID id,
            String label,
            String reason,
            KeywordCategory category,
            short displayOrder) {
    }

    public record KeywordSelectionRequest(
            @NotEmpty(message = "키워드를 1개 이상 골라주세요.")
            @Size(min = 1, max = 4, message = "키워드는 1~4개까지 고를 수 있어요.")
            List<UUID> keywordIds) {
    }

    // ------------------------------------------------------------ 결과지

    /** 키 순서가 결과 화면 블록 순서와 같다. (API.md §6.4) */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ResultResponse(
            UUID resultId,
            UUID analysisId,
            ResultViewState viewState,
            String title,
            OffsetDateTime analyzedAt,
            ComparisonImage comparisonImage,
            Overview overview,
            List<CategoryChangeView> categoryChanges,
            List<DailyCareView> dailyCares,
            boolean saved,
            String disclaimer) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ComparisonImage(ImageStatus status, String currentUrl, String peakUrl) {

        public static ComparisonImage skipped() {
            return new ComparisonImage(ImageStatus.SKIPPED, null, null);
        }
    }

    public record Overview(
            String summary,
            List<OverviewKeyword> keywords,
            List<String> keepPoints,
            List<String> emphasizePoints,
            List<String> changeIntensity) {
    }

    public record OverviewKeyword(UUID id, String label, boolean selected) {
    }

    public record CategoryChangeView(Category category, String description) {
    }

    public record DailyCareView(String title, String description) {
    }
}
