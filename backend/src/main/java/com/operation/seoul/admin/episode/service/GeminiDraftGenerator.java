package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.global.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.function.Function;

final class GeminiDraftGenerator {
    private final ObjectMapper objectMapper;
    private final Function<String, String> geminiCaller;

    GeminiDraftGenerator(ObjectMapper objectMapper, Function<String, String> geminiCaller) {
        this.objectMapper = objectMapper;
        this.geminiCaller = geminiCaller;
    }

    AiEpisodeDraftResponse.EpisodeDraft generate(AiEpisodeDraftRequest request) {
        try {
            JsonNode root = parseJson(geminiCaller.apply(GeminiDraftPromptBuilder.build(request)));
            return objectMapper.treeToValue(draftJsonNode(root), AiEpisodeDraftResponse.EpisodeDraft.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(stripJsonFence(value));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode draftJsonNode(JsonNode root) {
        if (root != null && root.has("draft") && root.path("draft").isObject()) {
            return root.path("draft");
        }
        if (root != null && root.has("data") && root.path("data").has("draft") && root.path("data").path("draft").isObject()) {
            return root.path("data").path("draft");
        }
        return root;
    }

    private String stripJsonFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return text.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return text;
    }
}
