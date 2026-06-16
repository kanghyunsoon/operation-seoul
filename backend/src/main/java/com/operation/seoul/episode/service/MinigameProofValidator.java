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
                case "MEMORY_CARD" -> "MATCHED".equals(proof);
                case "PATTERN_LOCK" -> proof.equals(joinIntArray(config.path("nodes"), ","));
                case "RAPID_TAP" -> parseInt(proof) == config.path("target").asInt(7);
                case "DIRECTION_SEQUENCE" -> proof.equals(joinTextArray(config.path("sequence"), ","));
                case "UP_DOWN_TIMER" -> parseInt(proof) == config.path("solution").asInt(Integer.MIN_VALUE);
                case "NUMBER_BASEBALL" -> proof.equals(config.path("solution").asText(""));
                case "NUMBER_SEQUENCE_TAP" -> proof.equals(expectedNumberTapProof(config));
                case "COLOR_STROOP", "LEFT_RIGHT_SORT" -> resultProofPasses(proof, config);
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

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private String expectedNumberTapProof(JsonNode config) {
        JsonNode sequence = config.path("sequence");
        if (!sequence.isArray()) return "";
        List<Integer> skipNumbers = intRuleList(config.path("skipNumbers"), config.path("skipNumber").asInt(Integer.MIN_VALUE));
        List<Integer> doubleNumbers = intRuleList(config.path("doubleNumbers"), config.path("doubleNumber").asInt(Integer.MIN_VALUE));
        List<String> values = new ArrayList<>();
        sequence.forEach(item -> {
            int value = item.asInt();
            if (skipNumbers.contains(value)) {
                return;
            }
            values.add(String.valueOf(value));
            if (doubleNumbers.contains(value)) {
                values.add(String.valueOf(value));
            }
        });
        return String.join(",", values);
    }

    private List<Integer> intRuleList(JsonNode arrayNode, int fallback) {
        List<Integer> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> values.add(item.asInt(Integer.MIN_VALUE)));
        } else if (fallback != Integer.MIN_VALUE) {
            values.add(fallback);
        }
        return values.stream().filter(value -> value != Integer.MIN_VALUE).distinct().limit(2).toList();
    }

    private boolean resultProofPasses(String proof, JsonNode config) {
        try {
            JsonNode result = objectMapper.readTree(proof);
            int correctCount = result.path("correctCount").asInt(Integer.MIN_VALUE);
            int passCorrectCount = config.path("passCorrectCount").asInt(Integer.MAX_VALUE);
            int totalRounds = result.path("totalRounds").asInt(0);
            int wrongCount = result.path("wrongCount").asInt(0);
            int elapsedMillis = result.path("elapsedMillis").asInt(0);
            return correctCount >= passCorrectCount
                    && totalRounds > 0
                    && wrongCount >= 0
                    && elapsedMillis >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
