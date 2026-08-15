package com.gojeom.analysis.entity;

import com.gojeom.common.entity.BaseCreatedEntity;
import com.gojeom.common.enums.ImageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 결과지. 화면 블록과 1:1 대응한다. (ERD.md §3.7 · API.md §6.4)
 *
 * <p>분석당 1행이다({@code ux_results_analysis} 유니크 인덱스).
 *
 * <p>JSONB 컬럼에는 {@code @JdbcTypeCode(SqlTypes.JSON)}을 반드시 붙인다.
 * 빠뜨리면 {@code ddl-auto: validate}가 기동을 막는다.
 *
 * <p><b>면책 문구는 여기 저장하지 않는다.</b> 고정 문구라 응답 상수로 내려준다.
 * (ERD.md §3.7)
 */
@Entity
@Table(name = "analysis_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "summary", nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keep_points", nullable = false)
    private List<String> keepPoints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emphasize_points", nullable = false)
    private List<String> emphasizePoints;

    /** 퍼센트가 아니라 짧은 텍스트 배열이다. 게이지 UI를 만들지 않는다. (ERD.md §3.7) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_intensity", nullable = false)
    private List<String> changeIntensity;

    /** 3건. {@code profiles.priorities} 순서로 정렬해 저장한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_changes", nullable = false)
    private List<CategoryChange> categoryChanges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "daily_cares", nullable = false)
    private List<DailyCare> dailyCares;

    @Column(name = "comparison_image_key", length = 512)
    private String comparisonImageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_status", nullable = false, length = 20)
    private ImageStatus imageStatus;

    private AnalysisResult(UUID analysisId, String title, String summary, List<String> keepPoints,
                           List<String> emphasizePoints, List<String> changeIntensity,
                           List<CategoryChange> categoryChanges, List<DailyCare> dailyCares,
                           ImageStatus imageStatus) {
        this.analysisId = analysisId;
        this.title = title;
        this.summary = summary;
        this.keepPoints = keepPoints;
        this.emphasizePoints = emphasizePoints;
        this.changeIntensity = changeIntensity;
        this.categoryChanges = categoryChanges;
        this.dailyCares = dailyCares;
        this.imageStatus = imageStatus;
    }

    /**
     * 비교 이미지 저장 위치. (ERD.md §8)
     *
     * <p><b>고점 이미지 한 장만 저장한다.</b> "현재" 쪽은 사용자가 이미 올린 프로필
     * 사진을 그대로 쓰므로 복제할 이유가 없다. 그래서 컬럼 하나
     * ({@code comparison_image_key})로 충분하다.
     */
    public static String peakImageKey(UUID userId, UUID resultId) {
        return "results/%s/%s/peak.png".formatted(userId, resultId);
    }

    /** 생성 성공. 실패로 정리됐다가 뒤늦게 도착해도 성공이 사실이므로 덮어쓴다. */
    public void applyComparisonImage(String key) {
        this.comparisonImageKey = key;
        this.imageStatus = ImageStatus.DONE;
    }

    /**
     * 생성 실패. <b>텍스트 결과는 그대로 남는다.</b>
     *
     * <p>이미 성공한 건은 건드리지 않는다. 좀비 정리와 실제 완료가 겹칠 때
     * 멀쩡한 이미지를 실패로 덮어쓰지 않기 위해서다.
     */
    public boolean markImageFailed() {
        if (imageStatus == ImageStatus.DONE) {
            return false;
        }
        this.imageStatus = ImageStatus.FAILED;
        return true;
    }

    public static AnalysisResult create(UUID analysisId, String title, String summary,
                                        List<String> keepPoints, List<String> emphasizePoints,
                                        List<String> changeIntensity,
                                        List<CategoryChange> categoryChanges,
                                        List<DailyCare> dailyCares, ImageStatus imageStatus) {
        return new AnalysisResult(analysisId, title, summary, keepPoints, emphasizePoints,
                changeIntensity, categoryChanges, dailyCares, imageStatus);
    }
}
