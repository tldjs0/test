package com.gojeom.analysis.service;

import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.enums.Category;
import com.gojeom.common.enums.KeywordCategory;
import com.gojeom.profile.entity.Inbody;
import com.gojeom.profile.entity.ProfileAnalysisSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 파이프라인이 트랜잭션 밖으로 들고 나가는 값 묶음.
 *
 * <p>엔티티를 그대로 넘기면 트랜잭션 밖에서 지연 로딩이 터진다. AI 호출은 수 초가
 * 걸려 반드시 트랜잭션 밖에서 일어나므로, 필요한 값만 미리 복사해 나간다.
 * (ARCHITECTURE.md §5.2)
 *
 * <p>{@code profileSummary}는 null일 수 있다. 프로필 AI 분석(D2-2)이 아직 안 끝났거나
 * 실패한 경우다. 그때는 요약 없이 진행한다 — 가짜 값을 만들지 않는다.
 */
public record AnalysisContext(
        UUID analysisId,
        UUID userId,
        AnalysisStatus status,
        String inputText,
        List<Category> priorities,
        short heightCm,
        BigDecimal weightKg,
        BigDecimal sleepHours,
        Inbody inbody,
        ProfileAnalysisSummary profileSummary,
        List<String> referenceImageKeys,
        List<SelectedKeyword> selectedKeywords) {

    /** 사용자가 확정한 키워드. 결과 생성 단계에서만 채워진다. */
    public record SelectedKeyword(String label, String reason, KeywordCategory category) {

        /** 프롬프트에 넣을 한 줄 표현. */
        public String toPromptLine() {
            return "- %s (%s) — %s".formatted(label, category.label(), reason);
        }
    }
}
