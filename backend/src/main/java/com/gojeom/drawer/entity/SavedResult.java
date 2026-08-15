package com.gojeom.drawer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 서랍에 저장한 결과. (ERD.md §3.8)
 *
 * <p><b>자동 저장은 없다.</b> 사용자가 "서랍에 결과 저장하기"를 눌렀을 때만
 * 이 행이 생긴다. (PRD R-4)
 *
 * <p>결과당 최대 1행이다({@code ux_saved_result} 유니크 인덱스). 같은 결과를 두 번
 * 저장하려 하면 DB가 막는다.
 *
 * <p>테이블에 {@code created_at}이 없어 베이스 엔티티를 상속하지 않는다.
 * {@code savedAt}이 그 역할을 한다.
 */
@Entity
@Table(name = "saved_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedResult {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "analysis_result_id", nullable = false)
    private UUID analysisResultId;

    @Column(name = "saved_at", nullable = false)
    private OffsetDateTime savedAt;

    private SavedResult(UUID userId, UUID analysisResultId, OffsetDateTime savedAt) {
        this.userId = userId;
        this.analysisResultId = analysisResultId;
        this.savedAt = savedAt;
    }

    public static SavedResult of(UUID userId, UUID analysisResultId) {
        return new SavedResult(userId, analysisResultId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }
}
