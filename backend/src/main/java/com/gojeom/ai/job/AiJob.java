package com.gojeom.ai.job;

import com.gojeom.ai.AiStage;
import com.gojeom.common.entity.BaseCreatedEntity;
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
 * OpenAI 호출 1건 = 1행. 비용·지연·실패 추적용이다. (ERD.md §3.10)
 *
 * <p><b>프롬프트 원문과 사용자 사진은 저장하지 않는다.</b> (PRD §9 · AGENTS.md 규칙 9)
 * 토큰 수·지연시간·에러코드만 남긴다. 이 테이블을 열어도 사용자가 무엇을 입력했는지
 * 알 수 없어야 한다.
 *
 * <p>재시도가 일어나면 시도마다 한 행이 쌓인다. 실패 행이 몇 개인지가 곧
 * 제공자 안정성 지표가 된다.
 */
@Entity
@Table(name = "ai_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** {@code PROFILE_ANALYSIS}·{@code INBODY_OCR}은 분석에 속하지 않아 null이다. */
    @Column(name = "analysis_id")
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private AiStage stage;

    /** 설정에 핀 고정한 값이 아니라 <b>제공자가 실제로 쓴 모델</b>을 기록한다. */
    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AiJobStatus status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    private AiJob(UUID analysisId, AiStage stage, String model, AiJobStatus status,
                  Integer inputTokens, Integer outputTokens, Integer latencyMs, String errorCode) {
        this.analysisId = analysisId;
        this.stage = stage;
        this.model = model;
        this.status = status;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
    }

    public static AiJob done(UUID analysisId, AiStage stage, String model,
                             Integer inputTokens, Integer outputTokens, int latencyMs) {
        return new AiJob(analysisId, stage, model, AiJobStatus.DONE,
                inputTokens, outputTokens, latencyMs, null);
    }

    public static AiJob failed(UUID analysisId, AiStage stage, String model,
                               int latencyMs, String errorCode) {
        return new AiJob(analysisId, stage, model, AiJobStatus.FAILED,
                null, null, latencyMs, errorCode);
    }
}
