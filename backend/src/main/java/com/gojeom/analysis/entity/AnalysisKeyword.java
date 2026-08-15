package com.gojeom.analysis.entity;

import com.gojeom.common.enums.KeywordCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * AI가 제안한 키워드 후보. (ERD.md §3.6)
 *
 * <p>{@code category}는 <b>4종</b>({@link KeywordCategory})이다. 얼굴 키워드를
 * 담아야 하기 때문이며, 우선순위·루틴의 3종({@link com.gojeom.common.enums.Category})과
 * 합치지 않는다. (V2 마이그레이션)
 *
 * <p>{@code selected}는 기본 false다. <b>AI가 기본 선택 상태를 만들지 않는다.</b>
 * 최종 확정은 사용자가 한다. (PRD F-06 · 서비스 원칙 "사용자 선택 우선")
 */
@Entity
@Table(name = "analysis_keywords")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisKeyword {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private KeywordCategory category;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    private AnalysisKeyword(UUID analysisId, String label, String reason,
                            KeywordCategory category, short displayOrder) {
        this.analysisId = analysisId;
        this.label = label;
        this.reason = reason;
        this.category = category;
        this.displayOrder = displayOrder;
        this.selected = false;
    }

    public static AnalysisKeyword of(UUID analysisId, String label, String reason,
                                     KeywordCategory category, int displayOrder) {
        return new AnalysisKeyword(analysisId, label, reason, category, (short) displayOrder);
    }

    public void select() {
        this.selected = true;
    }
}
