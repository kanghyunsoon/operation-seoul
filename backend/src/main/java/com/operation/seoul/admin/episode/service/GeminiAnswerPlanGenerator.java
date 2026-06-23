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
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;
    private static final Map<String, String> SLOT_LABELS = FinalAnswerSlots.LABELS;

    private final ObjectMapper objectMapper;
    private final Function<String, String> geminiCaller;

    GeminiAnswerPlanGenerator(ObjectMapper objectMapper, Function<String, String> geminiCaller) {
        this.objectMapper = objectMapper;
        this.geminiCaller = geminiCaller;
    }

    List<AiEpisodePlanResponse.AnswerKeyword> generate(AiEpisodeDraftRequest request) {
        try {
            JsonNode root = parseJson(geminiCaller.apply(GeminiAnswerPlanPromptBuilder.build(request)));
            return sanitizePlanKeywords(root, "GEMINI");
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
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        Map<String, String> sourceTexts = new LinkedHashMap<>();
        if (node != null && node.has("finalAnswerKeywords")) {
            node = node.path("finalAnswerKeywords");
        }
        boolean objectShape = node != null && node.isObject();
        if (objectShape) {
            putObjectAnswer(values, "CULPRIT", splitNameRole(textField(node, "culprit", "CULPRIT")).name());
            putObjectAnswer(values, "WEAPON", textField(node, "weapon", "WEAPON"));
            putObjectAnswer(values, "MOTIVE", textField(node, "motive", "MOTIVE"));
            String methodKeyword = textField(node, "method_keyword", "methodKeyword");
            String methodSentence = textField(node, "method_sentence", "methodSentence", "METHOD");
            putObjectAnswer(values, "METHOD", methodKeyword);
            putObjectAnswer(sourceTexts, "METHOD", methodSentence);
        } else if (node != null && node.isArray()) {
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
            log.warn("Gemini answer plan invalid: missing final answer slot. values={}", values);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_INVALID", "Gemini가 범인, 흉기, 동기, 방법 4개 정답 키워드를 모두 생성하지 못했습니다.");
        }
        List<String> weakSlots = objectShape
                ? weakStructuredSlots(values, sourceTexts)
                : new ArrayList<>(SLOT_IDS.stream()
                        .filter(slot -> FinalAnswerKeywordValidator.weakFinalAnswerKeyword(slot, values.get(slot)))
                        .toList());
        if (objectShape && weakMethodSentence(values.get("WEAPON"), sourceTexts.get("METHOD")) && !weakSlots.contains("METHOD")) {
            weakSlots.add("METHOD");
        }
        if (!weakSlots.isEmpty()) {
            log.warn("Gemini answer plan invalid: weakSlots={} values={}", weakSlots, values);
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
                    .aliases(aliasesFor(slot, value, aliases))
                    .sourceType(sourceType)
                    .sourceText(sourceTexts.getOrDefault(slot, ""))
                    .build());
        }
        return result;
    }

    private List<String> weakStructuredSlots(Map<String, String> values, Map<String, String> sourceTexts) {
        List<String> weakSlots = new ArrayList<>();
        for (String slot : SLOT_IDS) {
            if ("METHOD".equals(slot)) {
                if (weakMethodKeywordLabel(values.get(slot), sourceTexts.get(slot))) {
                    weakSlots.add(slot);
                }
            } else if (FinalAnswerKeywordValidator.weakFinalAnswerKeyword(slot, values.get(slot))) {
                weakSlots.add(slot);
            }
        }
        return weakSlots;
    }

    private void putObjectAnswer(Map<String, String> values, String slot, String value) {
        if (!blank(value)) {
            values.put(slot, trim(value));
        }
    }

    private List<String> aliasesFor(String slot, String value, Map<String, List<String>> aliases) {
        if ("CULPRIT".equals(slot)) return List.of(value);
        return aliases.getOrDefault(slot, List.of());
    }

    private boolean weakMethodSentence(String weaponValue, String methodValue) {
        String weapon = trim(weaponValue);
        String method = trim(methodValue);
        if (blank(method)) return true;
        if (method.length() < 25) return true;
        if (FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", method)) return true;
        return !blank(weapon) && !method.contains(weapon);
    }

    private boolean weakMethodKeywordLabel(String keywordValue, String sentenceValue) {
        String keyword = compact(keywordValue);
        if (blank(keyword)) return true;
        if (keyword.length() < 5) return true;
        if (List.of("함", "넣기", "투여", "주입", "교체", "은폐", "조작", "살해", "독살", "유인", "방치", "사용", "실행", "시도").contains(keyword)) {
            return true;
        }
        if (keyword.endsWith("함") || keyword.contains("하게함") || keyword.contains("되도록함")) {
            return true;
        }
        String sentence = compact(sentenceValue);
        boolean sharesAnchor = !blank(sentence) && keyword.length() >= 2 && sentence.contains(keyword.substring(0, Math.min(2, keyword.length())));
        boolean hasMethodNoun = containsAny(keyword, "접촉", "노출", "개봉", "열람", "서명", "봉인", "오염", "교체", "독살", "중독", "훼손", "은닉");
        return !sharesAnchor && !hasMethodNoun;
    }

    private String textField(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) return "";
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (!field.isMissingNode() && field.isTextual()) {
                String value = trim(field.asText(""));
                if (!blank(value)) return value;
            }
        }
        return "";
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

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(target)) return true;
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
