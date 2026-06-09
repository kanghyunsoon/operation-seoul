package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MinigameProofValidator {
    private final ObjectMapper objectMapper;

    public boolean validate(String rewardPayload, String submittedAnswer) {
        if (rewardPayload == null || rewardPayload.isBlank() || submittedAnswer == null || !submittedAnswer.startsWith("MG|")) {
            return false;
        }
        String[] parts = submittedAnswer.split("\\|", 3);
        if (parts.length < 3 || parts[2].length() > 500) {
            return false;
        }
        try {
            JsonNode interaction = objectMapper.readTree(rewardPayload).path("interaction");
            String type = interaction.path("type").asText("");
            if (!type.equals(parts[1])) {
                return false;
            }
            JsonNode config = interaction.path("config");
            String proof = parts[2];
            String localSolution = interaction.path("localSolution").asText("");
            return switch (type) {
                case "NUMBER_LOCK" -> proof.equals(config.path("solutionDigits").asText(""));
                case "WORD_COMPOSE" -> normalize(proof).equals(normalize(localSolution));
                case "COLOR_CODE" -> proof.equals(joinTextArray(config.path("solution"), ","));
                case "MEMORY_CARD" -> "MATCHED".equals(proof);
                case "PATTERN_LOCK" -> proof.equals(joinIntArray(config.path("nodes"), ","));
                case "SWITCH_TOGGLE" -> proof.equals(joinBooleanStates(config.path("targetStates")));
                case "RAPID_TAP" -> parseInt(proof) >= config.path("target").asInt(7);
                case "DIRECTION_SEQUENCE" -> proof.equals(joinTextArray(config.path("sequence"), ","));
                case "SHADOW_FIND" -> parseInt(proof) == config.path("targetIndex").asInt(-1);
                case "SLIDE_PUZZLE" -> proof.equals(joinTextArray(config.path("tiles"), ""));
                default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    private String joinTextArray(JsonNode node, String delimiter) {
        if (node == null || !node.isArray()) return "";
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText("")));
        return String.join(delimiter, values);
    }

    private String joinIntArray(JsonNode node, String delimiter) {
        if (node == null || !node.isArray()) return "";
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(String.valueOf(item.asInt())));
        return String.join(delimiter, values);
    }

    private String joinBooleanStates(JsonNode node) {
        if (node == null || !node.isArray()) return "";
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asBoolean(false) ? "1" : "0"));
        return String.join(",", values);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
