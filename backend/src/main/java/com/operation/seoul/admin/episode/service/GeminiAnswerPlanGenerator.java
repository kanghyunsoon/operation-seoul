package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Slf4j
final class GeminiAnswerPlanGenerator {
    private static final List<String> SLOT_IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    private static final Map<String, String> SLOT_LABELS = Map.of(
            "CULPRIT", "범인",
            "WEAPON", "흉기",
            "MOTIVE", "동기",
            "METHOD", "방법"
    );

    private final ObjectMapper objectMapper;
    private final Function<String, String> geminiCaller;

    GeminiAnswerPlanGenerator(ObjectMapper objectMapper, Function<String, String> geminiCaller) {
        this.objectMapper = objectMapper;
        this.geminiCaller = geminiCaller;
    }

    List<AiEpisodePlanResponse.AnswerKeyword> generate(AiEpisodeDraftRequest request) {
        try {
            JsonNode root = parseJson(geminiCaller.apply(GeminiAnswerPlanPromptBuilder.build(request)));
            JsonNode keywords = root.has("finalAnswerKeywords") ? root.path("finalAnswerKeywords") : root;
            return sanitizePlanKeywords(keywords, "GEMINI");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini answer plan generation failed. reason={}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_FAILED", "Gemini 최종 정답 키워드 생성에 실패했습니다. 서버 템플릿으로 대체하지 않습니다.");
        }
    }

    List<AiEpisodePlanResponse.AnswerKeyword> sanitizePlanKeywords(JsonNode node) {
        return sanitizePlanKeywords(node, "");
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> sanitizePlanKeywords(JsonNode node, String sourceType) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node != null && node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String slot = normalize(defaultIfBlank(item.path("slotId").asText(""), item.path("type").asText("")));
                if (!SLOT_IDS.contains(slot) && i < SLOT_IDS.size()) slot = SLOT_IDS.get(i);
                String keyword = trim(item.path("keyword").asText(""));
                if ("CULPRIT".equals(slot)) keyword = splitNameRole(keyword).name();
                if (SLOT_IDS.contains(slot) && !blank(keyword)) values.put(slot, keyword);
            }
        }
        if (!SLOT_IDS.stream().allMatch(slot -> !blank(values.get(slot)))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_INVALID", "Gemini가 범인, 흉기, 동기, 방법 4개 정답 키워드를 모두 생성하지 못했습니다.");
        }
        List<String> weakSlots = SLOT_IDS.stream()
                .filter(slot -> FinalAnswerKeywordValidator.weakFinalAnswerKeyword(slot, values.get(slot)))
                .toList();
        if (!weakSlots.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_INVALID", "Gemini가 구체적인 최종 정답 키워드를 생성하지 못했습니다: " + String.join(", ", weakSlots));
        }
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        for (String slot : SLOT_IDS) {
            String value = values.get(slot);
            result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                    .slotId(slot)
                    .type(slot)
                    .label(SLOT_LABELS.get(slot))
                    .displayType(SLOT_LABELS.get(slot))
                    .keyword(value)
                    .personName("CULPRIT".equals(slot) ? value : "")
                    .aliases("CULPRIT".equals(slot) ? List.of(value) : List.of())
                    .sourceType(sourceType)
                    .build());
        }
        return result;
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(stripJsonFence(value));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_PARSE_FAILED", "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private String stripJsonFence(String value) {
        String text = trim(value);
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return text.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return text;
    }

    private NameRole splitNameRole(String value) {
        String text = trim(value);
        if (blank(text)) return new NameRole("", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*([가-힣]{2,4})\\s*\\(([^)]+)\\)\\s*$")
                .matcher(text);
        if (matcher.matches()) {
            return new NameRole(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new NameRole(text, "");
    }

    private record NameRole(String name, String role) {}

    private String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
