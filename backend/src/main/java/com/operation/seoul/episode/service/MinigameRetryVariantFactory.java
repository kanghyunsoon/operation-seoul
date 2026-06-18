package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class MinigameRetryVariantFactory {
    private static final List<String> DIRECTIONS = List.of("UP", "RIGHT", "DOWN", "LEFT");

    private final ObjectMapper objectMapper;

    public Map<String, Object> variantInteraction(Map<String, Object> interaction, int retryVariant) {
        JsonNode source = objectMapper.valueToTree(interaction);
        ObjectNode variant = variantInteraction(source, retryVariant);
        return objectMapper.convertValue(variant, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public ObjectNode variantInteraction(JsonNode interaction, int retryVariant) {
        ObjectNode copy = interaction == null || !interaction.isObject()
                ? objectMapper.createObjectNode()
                : ((ObjectNode) interaction).deepCopy();
        int variant = Math.max(0, retryVariant);
        copy.put("retryVariant", variant);
        if (variant <= 0) {
            return copy;
        }
        String type = copy.path("type").asText("");
        ObjectNode config = copy.path("config").isObject()
                ? ((ObjectNode) copy.path("config")).deepCopy()
                : objectMapper.createObjectNode();
        int seed = seed(copy, variant);

        switch (type) {
            case "DIRECTION_SEQUENCE" -> config.set("sequence", directionSequence(config.path("sequence"), variant));
            case "PATTERN_LOCK" -> config.set("nodes", patternNodes(config.path("nodes"), seed));
            case "MEMORY_CARD" -> config.set("cards", shuffledTextArray(config.path("cards"), seed, List.of("기록", "봉투", "시간", "표식", "동선", "흔적")));
            case "NUMBER_LOCK" -> config.put("solutionDigits", digitString(length(config.path("solutionDigits").asText(""), 4), seed));
            case "RAPID_TAP" -> config.put("target", 12 + Math.floorMod(seed, 18));
            case "UP_DOWN_TIMER" -> config.put("solution", boundedNumber(config.path("min").asInt(1), config.path("max").asInt(100), seed));
            case "NUMBER_BASEBALL" -> config.put("solution", uniqueDigitString(Math.max(3, config.path("digits").asInt(3)), seed));
            case "NUMBER_SEQUENCE_TAP" -> {
                int skip = 2 + Math.floorMod(seed, 7);
                int doubled = 1 + Math.floorMod(seed / 7, 9);
                if (doubled == skip) {
                    doubled = doubled == 9 ? 1 : doubled + 1;
                }
                config.put("skipNumber", skip);
                config.put("doubleNumber", doubled);
                config.set("skipNumbers", intArray(List.of(skip)));
                config.set("doubleNumbers", intArray(List.of(doubled)));
            }
            case "COLOR_STROOP" -> config.set("items", stroopItems(config.path("items"), seed));
            case "LEFT_RIGHT_SORT" -> config.set("targets", swappedSortTargets(config.path("targets")));
            case "WORD_COMPOSE" -> {
                String current = copy.path("localSolution").asText("단서");
                String next = rotatedText(current, variant);
                copy.put("localSolution", next);
                config.set("tiles", shuffledCharacters(next, seed));
            }
            default -> {
            }
        }
        copy.set("config", config);
        return copy;
    }

    private int seed(JsonNode interaction, int retryVariant) {
        return Math.abs((interaction.path("type").asText("") + "|" + interaction.path("basis").asText("") + "|" + retryVariant).hashCode());
    }

    private ArrayNode directionSequence(JsonNode original, int retryVariant) {
        int length = original != null && original.isArray() && original.size() >= 3 ? original.size() : 4;
        List<String> originalValues = new ArrayList<>();
        if (original != null && original.isArray()) {
            original.forEach(item -> originalValues.add(item.asText("")));
        }
        List<String> values = new ArrayList<>();
        int variantCode = Math.max(1, retryVariant);
        for (int i = 0; i < length; i++) {
            String source = i < originalValues.size() ? originalValues.get(i) : DIRECTIONS.get(i % DIRECTIONS.size());
            int sourceIndex = Math.max(0, DIRECTIONS.indexOf(source));
            int shift = variantCode % DIRECTIONS.size();
            variantCode /= DIRECTIONS.size();
            values.add(DIRECTIONS.get(Math.floorMod(sourceIndex + shift, DIRECTIONS.size())));
        }
        return textArray(values);
    }

    private ArrayNode patternNodes(JsonNode original, int seed) {
        int length = original != null && original.isArray() && original.size() >= 4 ? original.size() : 7;
        List<Integer> nodes = new ArrayList<>();
        int cursor = Math.floorMod(seed, 9);
        for (int i = 0; nodes.size() < Math.min(length, 9) && i < 24; i++) {
            int node = Math.floorMod(cursor + i * 4 + i / 2, 9);
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
        }
        for (int i = 0; nodes.size() < Math.min(length, 9); i++) {
            if (!nodes.contains(i)) {
                nodes.add(i);
            }
        }
        return intArray(nodes);
    }

    private ArrayNode shuffledTextArray(JsonNode original, int seed, List<String> fallback) {
        List<String> values = new ArrayList<>();
        if (original != null && original.isArray()) {
            original.forEach(item -> {
                if (!item.asText("").isBlank()) {
                    values.add(item.asText(""));
                }
            });
        }
        if (values.isEmpty()) {
            values.addAll(fallback);
        }
        Collections.shuffle(values, new Random(seed));
        return textArray(values);
    }

    private ArrayNode shuffledCharacters(String value, int seed) {
        List<String> chars = new ArrayList<>(value.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(text -> !text.isBlank())
                .toList());
        Collections.shuffle(chars, new Random(seed));
        return textArray(chars);
    }

    private ArrayNode stroopItems(JsonNode original, int seed) {
        List<Map<String, String>> colors = List.of(
                Map.of("key", "RED", "label", "빨강", "hex", "#ef4444"),
                Map.of("key", "BLUE", "label", "파랑", "hex", "#3b82f6"),
                Map.of("key", "GREEN", "label", "초록", "hex", "#22c55e"),
                Map.of("key", "YELLOW", "label", "노랑", "hex", "#eab308")
        );
        int rounds = original != null && original.isArray() && original.size() > 0 ? original.size() : 10;
        ArrayNode items = objectMapper.createArrayNode();
        for (int i = 0; i < rounds; i++) {
            Map<String, String> text = colors.get(Math.floorMod(seed + i, colors.size()));
            Map<String, String> color = colors.get(Math.floorMod(seed + i + 2, colors.size()));
            ObjectNode item = objectMapper.createObjectNode();
            item.put("text", text.get("label"));
            item.put("textColorKey", color.get("key"));
            item.put("textColorHex", color.get("hex"));
            items.add(item);
        }
        return items;
    }

    private ArrayNode swappedSortTargets(JsonNode original) {
        ArrayNode targets = objectMapper.createArrayNode();
        if (original != null && original.isArray() && original.size() > 0) {
            original.forEach(item -> {
                ObjectNode copy = item.isObject() ? ((ObjectNode) item).deepCopy() : objectMapper.createObjectNode();
                String side = copy.path("correctSide").asText("LEFT");
                copy.put("correctSide", "LEFT".equals(side) ? "RIGHT" : "LEFT");
                targets.add(copy);
            });
            return targets;
        }
        ObjectNode cat = objectMapper.createObjectNode();
        cat.put("key", "CAT");
        cat.put("label", "고양이");
        cat.put("emoji", "🐱");
        cat.put("correctSide", "RIGHT");
        ObjectNode dog = objectMapper.createObjectNode();
        dog.put("key", "DOG");
        dog.put("label", "강아지");
        dog.put("emoji", "🐶");
        dog.put("correctSide", "LEFT");
        targets.add(cat);
        targets.add(dog);
        return targets;
    }

    private int boundedNumber(int min, int max, int seed) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + Math.floorMod(seed, Math.max(1, high - low + 1));
    }

    private String digitString(int length, int seed) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(Math.floorMod(seed / (i + 1) + i * 7, 10));
        }
        return builder.toString();
    }

    private String uniqueDigitString(int length, int seed) {
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; digits.size() < Math.min(length, 10) && i < 30; i++) {
            int digit = Math.floorMod(seed + i * 3, 10);
            if (!digits.contains(digit)) {
                digits.add(digit);
            }
        }
        return digits.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining());
    }

    private String rotatedText(String value, int retryVariant) {
        if (value == null || value.isBlank() || value.length() < 2) {
            return "단서" + retryVariant;
        }
        int amount = Math.floorMod(retryVariant, value.length());
        return value.substring(amount) + value.substring(0, amount);
    }

    private int length(String value, int fallback) {
        return value == null || value.isBlank() ? fallback : value.length();
    }

    private boolean sameTextArray(JsonNode original, List<String> values) {
        if (original == null || !original.isArray() || original.size() != values.size()) {
            return false;
        }
        List<String> source = new ArrayList<>();
        original.forEach(item -> source.add(item.asText("")));
        return source.equals(values);
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private ArrayNode intArray(List<Integer> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }
}
