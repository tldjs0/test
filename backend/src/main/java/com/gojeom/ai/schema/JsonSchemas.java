package com.gojeom.ai.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Structured Outputs 스키마 상수. (API.md §7.2~7.4)
 *
 * <p>모든 텍스트 단계는 {@code strict: true}로 받는다. <b>자유 서술을 정규식으로
 * 파싱하지 않는다.</b> 스키마 위반은 파싱 실패가 아니라 재시도 대상이다.
 * (AGENTS.md 규칙 13 · ARCHITECTURE.md §6.2)
 *
 * <p>원문을 JSON 문자열로 둔 이유 — API.md의 스키마와 <b>문자 단위로 대조</b>할 수
 * 있어야 계약이 갈라지지 않는다. 자바 빌더로 조립하면 대조가 불가능해진다.
 *
 * <p>strict 모드 요구사항: 모든 객체에 {@code additionalProperties: false}, 모든
 * 프로퍼티가 {@code required}에 포함. 두 조건을 어기면 400이 난다.
 */
@Component
public class JsonSchemas {

    /**
     * 스키마의 {@code maxLength}와 짝을 이루는 상수.
     *
     * <p>{@link com.gojeom.ai.guardrail.TruncationDetector}가 "이 길이에 정확히 닿았다"를
     * 판정하는 데 쓴다. 스키마 본문을 고치면 여기도 함께 고쳐야 한다.
     */
    public static final int SHORT_LABEL_MAX = 30;
    public static final int FACE_IMPRESSION_MAX = 30;
    public static final int BODY_RANGE_MAX = 40;
    public static final int HEALTH_NOTE_MAX = 60;

    /** API.md §7.2 */
    private static final String KEYWORD_EXTRACTION = """
            {
              "name": "keyword_extraction",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false, "required": ["keywords"],
                "properties": {
                  "keywords": {
                    "type": "array", "minItems": 5, "maxItems": 8,
                    "items": {
                      "type": "object", "additionalProperties": false,
                      "required": ["label", "reason", "category"],
                      "properties": {
                        "label":    { "type": "string", "maxLength": 40 },
                        "reason":   { "type": "string", "maxLength": 120 },
                        "category": { "type": "string", "enum": ["SKIN", "FACE", "BODY", "HEALTH"] }
                      }
                    }
                  }
                }
              }
            }
            """;

    /** API.md §7.3 */
    private static final String RESULT_GENERATION = """
            {
              "name": "peak_result",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false,
                "required": ["title", "summary", "keepPoints", "emphasizePoints",
                             "changeIntensity", "categoryChanges", "dailyCares"],
                "properties": {
                  "title":   { "type": "string", "maxLength": 60 },
                  "summary": { "type": "string", "maxLength": 120 },
                  "keepPoints":      { "type": "array", "minItems": 1, "maxItems": 3, "items": { "type": "string", "maxLength": 30 } },
                  "emphasizePoints": { "type": "array", "minItems": 1, "maxItems": 3, "items": { "type": "string", "maxLength": 30 } },
                  "changeIntensity": { "type": "array", "minItems": 1, "maxItems": 3, "items": { "type": "string", "maxLength": 30 } },
                  "categoryChanges": {
                    "type": "array", "minItems": 3, "maxItems": 3,
                    "items": {
                      "type": "object", "additionalProperties": false,
                      "required": ["category", "description"],
                      "properties": {
                        "category":    { "type": "string", "enum": ["SKIN", "BODY", "HEALTH"] },
                        "description": { "type": "string", "maxLength": 200 }
                      }
                    }
                  },
                  "dailyCares": {
                    "type": "array", "minItems": 3, "maxItems": 3,
                    "items": {
                      "type": "object", "additionalProperties": false,
                      "required": ["title", "description"],
                      "properties": {
                        "title":       { "type": "string", "maxLength": 30 },
                        "description": { "type": "string", "maxLength": 150 }
                      }
                    }
                  }
                }
              }
            }
            """;

    /**
     * 프로필 분석 — <b>API.md에 없던 스키마다.</b>
     *
     * <p>ERD.md §5.3의 {@code profiles.analysis_summary} 구조에서 역으로 만들었다.
     * {@code modelVersion}·{@code analyzedAt}은 서버가 채우므로 AI에게 요구하지 않는다.
     *
     * <p><b>점수·등급 필드를 두지 않는다.</b> 스키마에 없으면 모델이 만들 수 없다.
     * 가드레일의 1차 방어선이다. (PRD G-1)
     */
    private static final String PROFILE_ANALYSIS = """
            {
              "name": "profile_analysis",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false,
                "required": ["faceImpression", "bodyRange", "healthNotes"],
                "properties": {
                  "faceImpression": { "type": "array", "minItems": 1, "maxItems": 4, "items": { "type": "string", "maxLength": 30 } },
                  "bodyRange":      { "type": "string", "maxLength": 40 },
                  "healthNotes":    { "type": "array", "minItems": 1, "maxItems": 4, "items": { "type": "string", "maxLength": 60 } }
                }
              }
            }
            """;

    /**
     * 목표 생성 — 경로 A. API.md에 없던 스키마다.
     *
     * <p>API.md §7.1은 {@code ROUTINE_GENERATION} 단계만 정의하고 스키마는 비워뒀다.
     * ERD.md §3.9의 {@code routine_tasks} 컬럼과 API.md §6.6의 Task Card 표기
     * ({@code title / timing · duration_label · amount_label})에서 역으로 만들었다.
     *
     * <p>{@code timing}만 필수다. 분량이 없는 태스크("잠들기 전 스트레칭")가 있어
     * {@code durationLabel}·{@code amountLabel}은 null을 허용한다 — DB도 nullable이다.
     * 억지로 채우게 하면 "1회" 같은 의미 없는 값이 화면에 붙는다.
     */
    private static final String ROUTINE_FROM_ANALYSIS = """
            {
              "name": "routine_from_analysis",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false,
                "required": ["title", "tasks"],
                "properties": {
                  "title": { "type": "string", "maxLength": 60 },
                  "tasks": {
                    "type": "array", "minItems": 4, "maxItems": 6,
                    "items": {
                      "type": "object", "additionalProperties": false,
                      "required": ["category", "title", "timing", "durationLabel", "amountLabel"],
                      "properties": {
                        "category":      { "type": "string", "enum": ["SKIN", "BODY", "HEALTH"] },
                        "title":         { "type": "string", "maxLength": 30 },
                        "timing":        { "type": "string", "maxLength": 20 },
                        "durationLabel": { "type": ["string", "null"], "maxLength": 12 },
                        "amountLabel":   { "type": ["string", "null"], "maxLength": 12 }
                      }
                    }
                  }
                }
              }
            }
            """;

    /**
     * 목표 생성 — 경로 B. 카테고리당 목표 1개, 최대 3개를 <b>한 번의 호출로</b> 만든다.
     *
     * <p>카테고리마다 따로 부르면 3개 선택 시 호출이 3번이라 응답이 15초에 육박한다.
     * 한 번에 받으면 모델이 카테고리 간 중복 태스크도 피할 수 있다.
     */
    private static final String ROUTINE_STANDALONE = """
            {
              "name": "routine_standalone",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false,
                "required": ["routines"],
                "properties": {
                  "routines": {
                    "type": "array", "minItems": 1, "maxItems": 3,
                    "items": {
                      "type": "object", "additionalProperties": false,
                      "required": ["category", "title", "tasks"],
                      "properties": {
                        "category": { "type": "string", "enum": ["SKIN", "BODY", "HEALTH"] },
                        "title":    { "type": "string", "maxLength": 60 },
                        "tasks": {
                          "type": "array", "minItems": 4, "maxItems": 6,
                          "items": {
                            "type": "object", "additionalProperties": false,
                            "required": ["category", "title", "timing", "durationLabel", "amountLabel"],
                            "properties": {
                              "category":      { "type": "string", "enum": ["SKIN", "BODY", "HEALTH"] },
                              "title":         { "type": "string", "maxLength": 30 },
                              "timing":        { "type": "string", "maxLength": 20 },
                              "durationLabel": { "type": ["string", "null"], "maxLength": 12 },
                              "amountLabel":   { "type": ["string", "null"], "maxLength": 12 }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    /**
     * 인바디 서류 OCR. (API.md §7.4)
     *
     * <p><b>모든 항목이 nullable이다.</b> 읽지 못한 값을 추측해 채우면 사용자가
     * 남의 몸 수치를 자기 것으로 저장하게 된다. 스키마에서 null을 허용해야
     * 모델이 "모르겠다"고 말할 수 있다. (PRD G-8)
     */
    private static final String INBODY_OCR = """
            {
              "name": "inbody_ocr",
              "strict": true,
              "schema": {
                "type": "object", "additionalProperties": false,
                "required": ["bodyWaterL", "proteinKg", "mineralKg", "bodyFatKg", "skeletalMuscleKg", "bmi"],
                "properties": {
                  "bodyWaterL":       { "type": ["number", "null"] },
                  "proteinKg":        { "type": ["number", "null"] },
                  "mineralKg":        { "type": ["number", "null"] },
                  "bodyFatKg":        { "type": ["number", "null"] },
                  "skeletalMuscleKg": { "type": ["number", "null"] },
                  "bmi":              { "type": ["number", "null"] }
                }
              }
            }
            """;

    private final JsonNode keywordExtraction;
    private final JsonNode resultGeneration;
    private final JsonNode profileAnalysis;
    private final JsonNode routineFromAnalysis;
    private final JsonNode routineStandalone;
    private final JsonNode inbodyOcr;

    /**
     * 스키마를 <b>기동 시점에</b> 파싱한다. 오타가 있으면 첫 AI 호출이 아니라
     * 애플리케이션 기동에서 즉시 드러난다.
     */
    public JsonSchemas(ObjectMapper objectMapper) {
        this.keywordExtraction = parse(objectMapper, KEYWORD_EXTRACTION);
        this.resultGeneration = parse(objectMapper, RESULT_GENERATION);
        this.profileAnalysis = parse(objectMapper, PROFILE_ANALYSIS);
        this.routineFromAnalysis = parse(objectMapper, ROUTINE_FROM_ANALYSIS);
        this.routineStandalone = parse(objectMapper, ROUTINE_STANDALONE);
        this.inbodyOcr = parse(objectMapper, INBODY_OCR);
    }

    private JsonNode parse(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("JSON Schema 상수가 유효한 JSON이 아니다", e);
        }
    }

    public JsonNode keywordExtraction() {
        return keywordExtraction;
    }

    public JsonNode resultGeneration() {
        return resultGeneration;
    }

    public JsonNode profileAnalysis() {
        return profileAnalysis;
    }

    public JsonNode routineFromAnalysis() {
        return routineFromAnalysis;
    }

    public JsonNode routineStandalone() {
        return routineStandalone;
    }

    public JsonNode inbodyOcr() {
        return inbodyOcr;
    }
}
