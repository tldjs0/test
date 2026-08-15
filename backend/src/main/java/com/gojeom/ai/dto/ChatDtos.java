package com.gojeom.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * OpenAI {@code /v1/chat/completions} 요청·응답 매핑. (API.md §7.1)
 *
 * <p>필드명이 {@code snake_case}라 레코드 컴포넌트마다 {@code @JsonProperty}를 붙인다.
 * 응답에는 {@code @JsonIgnoreProperties(ignoreUnknown = true)}를 둔다 —
 * 제공자가 필드를 추가해도 파싱이 깨지지 않아야 한다.
 *
 * <p><b>온도·최대 토큰을 보내지 않는다.</b> 모델 세대마다 지원 파라미터가 달라
 * ({@code max_tokens} vs {@code max_completion_tokens}) 모델을 바꾸면 400이 난다.
 * 필수 파라미터만 보내면 모델 핀을 바꿔도 코드가 그대로 동작한다.
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    // ------------------------------------------------------------------ 요청

    public record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("response_format") ResponseFormat responseFormat) {
    }

    /** {@code content}는 시스템 메시지면 String, 사용자 메시지면 파트 배열이다. */
    public record Message(String role, Object content) {

        public static Message system(String text) {
            return new Message("system", text);
        }

        public static Message user(List<Object> parts) {
            return new Message("user", parts);
        }
    }

    public record TextPart(String type, String text) {

        public static TextPart of(String text) {
            return new TextPart("text", text);
        }
    }

    public record ImagePart(String type, @JsonProperty("image_url") ImageUrl imageUrl) {

        /** presigned GET URL을 그대로 넘긴다. 이미지 바이트는 이 서버를 통과하지 않는다. */
        public static ImagePart of(String url) {
            return new ImagePart("image_url", new ImageUrl(url));
        }
    }

    public record ImageUrl(String url) {
    }

    public record ResponseFormat(String type, @JsonProperty("json_schema") JsonNode jsonSchema) {

        public static ResponseFormat jsonSchema(JsonNode schema) {
            return new ResponseFormat("json_schema", schema);
        }
    }

    // ------------------------------------------------------------------ 응답

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(String model, List<Choice> choices, Usage usage) {

        public Choice firstChoice() {
            return choices == null || choices.isEmpty() ? null : choices.get(0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ResponseMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    /** {@code refusal}이 채워져 오면 모델이 응답을 거부한 것이다. 정상 시나리오로 다룬다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMessage(String content, String refusal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens) {
    }
}
