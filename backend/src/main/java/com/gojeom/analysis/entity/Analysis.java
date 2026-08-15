package com.gojeom.analysis.entity;

import com.gojeom.common.entity.BaseTimeEntity;
import com.gojeom.common.enums.AnalysisStatus;
import com.gojeom.common.exception.BusinessException;
import com.gojeom.common.exception.ErrorCode;
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
 * 고점 분석 1건. 상태 기계의 주체다. (ERD.md §3.4)
 *
 * <p>상태 전이를 메서드로만 열어둔 이유 — setter를 두면 어디서든 아무 상태로
 * 건너뛸 수 있다. 비동기 파이프라인과 사용자 액션이 같은 행을 만지므로,
 * <b>허용된 전이인지 엔티티가 직접 확인</b>하게 한다.
 */
@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analysis extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 분석 시점의 프로필. 프로필은 이력이므로 나중에 새 프로필이 생겨도
     * 이 분석은 그때 값을 계속 가리킨다. (ERD.md §3.3)
     */
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "input_text", nullable = false)
    private String inputText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    /** 실패 사유. {@link ErrorCode} 이름을 그대로 넣는다. */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    /** "새로 분석하기"로 만들어진 분석이 원본을 가리킨다. (PRD R-1) */
    @Column(name = "retried_from")
    private UUID retriedFrom;

    private Analysis(UUID userId, UUID profileId, String inputText, UUID retriedFrom) {
        this.userId = userId;
        this.profileId = profileId;
        this.inputText = inputText;
        this.retriedFrom = retriedFrom;
        this.status = AnalysisStatus.CREATED;
    }

    public static Analysis create(UUID userId, UUID profileId, String inputText, UUID retriedFrom) {
        return new Analysis(userId, profileId, inputText, retriedFrom);
    }

    // ------------------------------------------------------------ 상태 전이

    public void markExtracting() {
        require(AnalysisStatus.CREATED);
        this.status = AnalysisStatus.EXTRACTING;
    }

    public void markKeywordsReady() {
        require(AnalysisStatus.EXTRACTING);
        this.status = AnalysisStatus.KEYWORDS_READY;
    }

    public void markGenerating() {
        require(AnalysisStatus.KEYWORDS_READY);
        this.status = AnalysisStatus.GENERATING;
    }

    public void markDone() {
        require(AnalysisStatus.GENERATING);
        this.status = AnalysisStatus.DONE;
    }

    /**
     * 실패 처리.
     *
     * <p>이미 끝난 분석은 건드리지 않는다. 좀비 정리와 파이프라인 실패가 겹칠 때
     * 완료된 결과를 실패로 덮어쓰는 사고를 막는다.
     *
     * @return 실제로 상태가 바뀌었으면 true
     */
    public boolean markFailed(ErrorCode code) {
        if (status.isTerminal()) {
            return false;
        }
        this.status = AnalysisStatus.FAILED;
        this.failureCode = code.name();
        return true;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    /**
     * 사용자 액션으로 인한 전이는 {@code 409 ANALYSIS_INVALID_STATE}로 내려간다.
     * 프론트가 화면을 잘못 띄운 것이지 서버 장애가 아니다.
     */
    private void require(AnalysisStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.ANALYSIS_INVALID_STATE);
        }
    }
}
