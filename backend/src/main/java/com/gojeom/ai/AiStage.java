package com.gojeom.ai;

/**
 * AI 호출 단계. {@code ai_jobs.stage}에 그대로 기록된다. (ERD.md §3.10 · PRD §8.1)
 *
 * <p>{@code PROFILE_ANALYSIS}와 {@code INBODY_OCR}은 분석에 속하지 않으므로
 * {@code ai_jobs.analysis_id}가 NULL이다.
 */
public enum AiStage {

    PROFILE_ANALYSIS,
    INBODY_OCR,
    KEYWORD_EXTRACTION,
    RESULT_GENERATION,
    IMAGE_GENERATION,
    ROUTINE_GENERATION
}
