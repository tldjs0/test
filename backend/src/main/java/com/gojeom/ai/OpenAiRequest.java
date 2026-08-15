package com.gojeom.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.gojeom.ai.dto.ChatDtos.ImagePart;
import com.gojeom.ai.dto.ChatDtos.TextPart;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 호출 1건의 입력. 프롬프트 클래스가 만들고 {@link OpenAiClient}가 실행한다.
 *
 * @param stage        {@code ai_jobs.stage}로 기록된다
 * @param analysisId   분석에 속하지 않는 단계는 null
 * @param system       시스템 프롬프트 (가드레일 포함)
 * @param userParts    텍스트·이미지 파트 배열
 * @param schema       {@code response_format.json_schema}에 그대로 들어간다
 * @param responseType 파싱할 레코드 타입
 */
public record OpenAiRequest<T>(
        AiStage stage,
        UUID analysisId,
        String system,
        List<Object> userParts,
        JsonNode schema,
        Class<T> responseType) {

    public static <T> Builder<T> builder(AiStage stage, Class<T> responseType) {
        return new Builder<>(stage, responseType);
    }

    /** 같은 요청을 시스템 프롬프트만 바꿔 다시 만든다. 가드레일 재생성에 쓴다. */
    public OpenAiRequest<T> withSystem(String newSystem) {
        return new OpenAiRequest<>(stage, analysisId, newSystem, userParts, schema, responseType);
    }

    public static final class Builder<T> {

        private final AiStage stage;
        private final Class<T> responseType;
        private final List<Object> parts = new ArrayList<>();
        private UUID analysisId;
        private String system;
        private JsonNode schema;

        private Builder(AiStage stage, Class<T> responseType) {
            this.stage = stage;
            this.responseType = responseType;
        }

        public Builder<T> analysisId(UUID analysisId) {
            this.analysisId = analysisId;
            return this;
        }

        public Builder<T> system(String system) {
            this.system = system;
            return this;
        }

        public Builder<T> schema(JsonNode schema) {
            this.schema = schema;
            return this;
        }

        public Builder<T> text(String text) {
            parts.add(TextPart.of(text));
            return this;
        }

        /** presigned GET URL. null·blank는 조용히 건너뛴다 (사진이 없는 경우). */
        public Builder<T> image(String presignedUrl) {
            if (presignedUrl != null && !presignedUrl.isBlank()) {
                parts.add(ImagePart.of(presignedUrl));
            }
            return this;
        }

        public OpenAiRequest<T> build() {
            return new OpenAiRequest<>(stage, analysisId, system, List.copyOf(parts), schema, responseType);
        }
    }
}
