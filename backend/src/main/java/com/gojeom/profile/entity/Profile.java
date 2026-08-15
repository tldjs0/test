package com.gojeom.profile.entity;

import com.gojeom.common.entity.BaseTimeEntity;
import com.gojeom.common.enums.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 현재 프로필. 사진·우선순위·신체 정보를 담는다.
 *
 * <p>사용자당 {@code isActive = true}는 최대 1행이다. 사진을 다시 등록하면 기존 행을
 * 비활성화하고 새 행을 만든다. 과거 분석은 그 시점 프로필을 참조하므로 결과 재현이
 * 가능하다. (ERD.md §3.3)
 */
@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 사진 삭제 시 null이 된다. 사진이 없으면 신규 분석을 시작할 수 없다. (PRD §10) */
    @Column(name = "photo_key", length = 512)
    private String photoKey;

    /**
     * <b>배열 순서가 곧 1·2·3순위다.</b> 정렬을 바꾸면 의미가 달라진다.
     * (ERD.md §5.1)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "priorities", nullable = false)
    private List<Category> priorities;

    /** 입력 화면 미설계로 현재 null이다. (PRD O-2) */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "height_cm", nullable = false)
    private short heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "sleep_hours", precision = 3, scale = 1)
    private BigDecimal sleepHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inbody")
    private Inbody inbody;

    /** D2-2에서 AI가 채운다. 그전까지는 null이며 가짜 값을 넣지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_summary")
    private ProfileAnalysisSummary analysisSummary;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    private Profile(UUID userId, String photoKey, List<Category> priorities,
                    short heightCm, BigDecimal weightKg, BigDecimal sleepHours, Inbody inbody) {
        this.userId = userId;
        this.photoKey = photoKey;
        this.priorities = priorities;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.sleepHours = sleepHours;
        this.inbody = inbody;
        this.isActive = true;
    }

    public static Profile create(UUID userId, String photoKey, List<Category> priorities,
                                 short heightCm, BigDecimal weightKg,
                                 BigDecimal sleepHours, Inbody inbody) {
        return new Profile(userId, photoKey, priorities, heightCm, weightKg, sleepHours,
                inbody == null || inbody.isEmpty() ? null : inbody);
    }

    public void deactivate() {
        this.isActive = false;
    }

    /** 사진은 그대로 두고 신체 정보만 갱신한다. */
    public void updateBody(BigDecimal weightKg, BigDecimal sleepHours, Inbody inbody) {
        if (weightKg != null) {
            this.weightKg = weightKg;
        }
        this.sleepHours = sleepHours;
        this.inbody = inbody == null || inbody.isEmpty() ? null : inbody;
    }

    /** 프로필 화면의 "우선 순위 변경". 기존 분석 결과는 재생성하지 않는다. */
    public void changePriorities(List<Category> priorities) {
        this.priorities = priorities;
    }

    public void applyAnalysisSummary(ProfileAnalysisSummary summary) {
        this.analysisSummary = summary;
    }

    public void removePhoto() {
        this.photoKey = null;
    }
}
