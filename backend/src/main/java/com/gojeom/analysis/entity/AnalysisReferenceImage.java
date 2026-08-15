package com.gojeom.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 고점 참고 사진. 여러 장 첨부할 수 있다. (ERD.md §3.5)
 *
 * <p>0행이면 텍스트 전용 분석이다.
 *
 * <p>키에 {@code analysisId}가 들어가지 않는다 — presigned URL 발급 시점에는 분석이
 * 아직 없기 때문이다. 어떤 분석에 속하는지는 이 행이 기록한다. (ARCHITECTURE.md §8)
 *
 * <p>{@code created_at} 컬럼이 없어 베이스 엔티티를 상속하지 않는다.
 */
@Entity
@Table(name = "analysis_reference_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisReferenceImage {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "image_key", nullable = false, length = 512)
    private String imageKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    private AnalysisReferenceImage(UUID analysisId, String imageKey, short displayOrder) {
        this.analysisId = analysisId;
        this.imageKey = imageKey;
        this.displayOrder = displayOrder;
    }

    public static AnalysisReferenceImage of(UUID analysisId, String imageKey, int displayOrder) {
        return new AnalysisReferenceImage(analysisId, imageKey, (short) displayOrder);
    }
}
