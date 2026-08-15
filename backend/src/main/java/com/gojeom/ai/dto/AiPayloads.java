package com.gojeom.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.KeywordCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI가 스키마대로 돌려주는 본문. ({@link com.gojeom.ai.schema.JsonSchemas}와 1:1)
 *
 * <p><b>파생 메서드에는 반드시 {@code @JsonIgnore}를 붙인다.</b> Jackson이
 * {@code isXxx()}/{@code getXxx()}를 JSON 속성으로 보기 때문이다. D1-7에서
 * {@code Inbody.isEmpty()}가 JSONB에 {@code "empty": false}로 저장된 적이 있다.
 * 이 레코드들은 JSONB로 저장될 값을 품고 있어 같은 사고가 재발하기 쉽다.
 */
public final class AiPayloads {

    private AiPayloads() {
    }

    // ------------------------------------------------------ KEYWORD_EXTRACTION

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeywordExtraction(List<ExtractedKeyword> keywords) {
    }

    /** {@code category}는 4종이다. 얼굴 키워드를 담아야 하므로 {@link Category}가 아니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedKeyword(String label, String reason, KeywordCategory category) {
    }

    // ------------------------------------------------------ RESULT_GENERATION

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeakResult(
            String title,
            String summary,
            List<String> keepPoints,
            List<String> emphasizePoints,
            List<String> changeIntensity,
            List<CategoryChangePayload> categoryChanges,
            List<DailyCarePayload> dailyCares) {

        /** 가드레일 후검증 대상 텍스트. 사용자에게 노출되는 문자열만 모은다. */
        @JsonIgnore
        public String userFacingText() {
            StringBuilder sb = new StringBuilder();
            sb.append(title).append('\n').append(summary).append('\n');
            keepPoints.forEach(s -> sb.append(s).append('\n'));
            emphasizePoints.forEach(s -> sb.append(s).append('\n'));
            changeIntensity.forEach(s -> sb.append(s).append('\n'));
            categoryChanges.forEach(c -> sb.append(c.description()).append('\n'));
            dailyCares.forEach(c -> sb.append(c.title()).append('\n').append(c.description()).append('\n'));
            return sb.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryChangePayload(Category category, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyCarePayload(String title, String description) {
    }

    // ------------------------------------------------------ ROUTINE_GENERATION

    /** 경로 A — 여러 카테고리에 걸친 목표 1개. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoutinePlan(String title, List<PlannedTask> tasks) {

        @JsonIgnore
        public String userFacingText() {
            return title + '\n' + PlannedTask.joinText(tasks);
        }
    }

    /** 경로 B — 카테고리당 목표 1개, 최대 3개. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandalonePlan(List<PlannedRoutine> routines) {

        @JsonIgnore
        public String userFacingText() {
            return routines.stream()
                    .map(r -> r.title() + '\n' + PlannedTask.joinText(r.tasks()))
                    .reduce((a, b) -> a + '\n' + b)
                    .orElse("");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedRoutine(Category category, String title, List<PlannedTask> tasks) {
    }

    /**
     * 태스크 1건.
     *
     * <p>{@code durationLabel}·{@code amountLabel}은 null일 수 있다. 분량 개념이 없는
     * 태스크가 있고, 억지로 채우면 "1회" 같은 값이 화면에 붙는다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedTask(
            Category category,
            String title,
            String timing,
            String durationLabel,
            String amountLabel) {

        @JsonIgnore
        static String joinText(List<PlannedTask> tasks) {
            StringBuilder sb = new StringBuilder();
            for (PlannedTask t : tasks) {
                sb.append(t.title).append('\n').append(t.timing).append('\n');
                if (t.durationLabel != null) {
                    sb.append(t.durationLabel).append('\n');
                }
                if (t.amountLabel != null) {
                    sb.append(t.amountLabel).append('\n');
                }
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------ INBODY_OCR

    /**
     * 인바디 서류에서 읽어낸 6종. <b>읽지 못한 항목은 null이다.</b> (PRD G-8)
     *
     * <p>{@code BigDecimal}로 받아 프로필 저장 형식({@code Inbody})과 그대로 맞춘다.
     * {@code double}로 받으면 소수 표현이 어긋난다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InbodyOcrPayload(
            BigDecimal bodyWaterL,
            BigDecimal proteinKg,
            BigDecimal mineralKg,
            BigDecimal bodyFatKg,
            BigDecimal skeletalMuscleKg,
            BigDecimal bmi) {

        /** 읽지 못한 항목 이름. 프론트가 빈 칸으로 두고 직접 입력을 유도한다. */
        @JsonIgnore
        public List<String> unrecognized() {
            List<String> missing = new ArrayList<>();
            if (bodyWaterL == null) {
                missing.add("bodyWaterL");
            }
            if (proteinKg == null) {
                missing.add("proteinKg");
            }
            if (mineralKg == null) {
                missing.add("mineralKg");
            }
            if (bodyFatKg == null) {
                missing.add("bodyFatKg");
            }
            if (skeletalMuscleKg == null) {
                missing.add("skeletalMuscleKg");
            }
            if (bmi == null) {
                missing.add("bmi");
            }
            return missing;
        }

        @JsonIgnore
        public int recognizedCount() {
            return TOTAL_FIELDS - unrecognized().size();
        }

        private static final int TOTAL_FIELDS = 6;
    }

    // ------------------------------------------------------ PROFILE_ANALYSIS

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileAnalysisPayload(
            List<String> faceImpression,
            String bodyRange,
            List<String> healthNotes) {

        @JsonIgnore
        public String userFacingText() {
            return String.join("\n", faceImpression) + '\n' + bodyRange + '\n'
                    + String.join("\n", healthNotes);
        }
    }
}
