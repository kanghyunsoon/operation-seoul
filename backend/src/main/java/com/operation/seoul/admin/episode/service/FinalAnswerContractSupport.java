package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.global.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class FinalAnswerContractSupport {
    private FinalAnswerContractSupport() {
    }

    static void normalizeFinalAnswerKeywordItems(AiEpisodeDraftRequest request) {
        if (request == null || (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty())) return;
        if (request.getFinalAnswers() == null) return;
        Map<String, String> fromFinalAnswers = new LinkedHashMap<>();
        putIfNotBlank(fromFinalAnswers, "CULPRIT", request.getFinalAnswers().getCulprit());
        putIfNotBlank(fromFinalAnswers, "WEAPON", request.getFinalAnswers().getWeapon());
        putIfNotBlank(fromFinalAnswers, "MOTIVE", request.getFinalAnswers().getMotive());
        putIfNotBlank(fromFinalAnswers, "METHOD", request.getFinalAnswers().getMethod());
        if (FinalAnswerSlots.IDS.stream().anyMatch(slot -> blank(fromFinalAnswers.get(slot)))) return;
        List<AiEpisodeDraftRequest.AnswerKeywordInput> items = new ArrayList<>();
        for (String slot : FinalAnswerSlots.IDS) {
            AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
            item.setSlotId(slot);
            item.setType(slot);
            item.setLabel(FinalAnswerSlots.LABELS.get(slot));
            item.setDisplayType(FinalAnswerSlots.LABELS.get(slot));
            item.setKeyword(fromFinalAnswers.get(slot));
            items.add(item);
        }
        request.setFinalAnswerKeywordItems(items);
    }

    static void validateFinalAnswerContract(AiEpisodeDraftRequest request) {
        Map<String, String> values = approvedAnswers(request);
        if (FinalAnswerSlots.IDS.stream().anyMatch(slot -> blank(values.get(slot)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.");
        }
        List<String> weakSlots = FinalAnswerSlots.IDS.stream()
                .filter(slot -> weakFinalAnswerKeyword(slot, values.get(slot)))
                .toList();
        if (!weakSlots.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 구체적인 인물, 물건, 동기, 범행 과정이어야 합니다: " + String.join(", ", weakSlots));
        }
    }

    static Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        FinalAnswerSlots.IDS.forEach(slot -> result.put(slot, ""));
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
                if (FinalAnswerSlots.IDS.contains(slot)) putIfNotBlank(result, slot, answerKeywordValue(item));
            }
        }
        if (request != null && request.getFinalAnswers() != null) {
            putIfNotBlank(result, "CULPRIT", request.getFinalAnswers().getCulprit());
            putIfNotBlank(result, "WEAPON", request.getFinalAnswers().getWeapon());
            putIfNotBlank(result, "MOTIVE", request.getFinalAnswers().getMotive());
            putIfNotBlank(result, "METHOD", request.getFinalAnswers().getMethod());
        }
        return result;
    }

    static String answerKeywordItemValue(AiEpisodeDraftResponse.AnswerKeywordItem item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName())
                ? item.getPersonName()
                : defaultIfBlank(item.getKeyword(), item.getValue());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    static boolean weakFinalAnswerKeyword(String slot, String value) {
        return FinalAnswerKeywordValidator.weakFinalAnswerKeyword(slot, value);
    }

    static NameRole splitNameRole(String value) {
        String text = trim(value);
        if (blank(text)) return new NameRole("", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*([^()\\s]{2,20})\\s*\\(([^)]+)\\)\\s*$")
                .matcher(text);
        if (matcher.matches()) {
            return new NameRole(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new NameRole(text, "");
    }

    private static String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName())
                ? item.getPersonName()
                : defaultIfBlank(item.getKeyword(), item.getSourceText());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private static void putIfNotBlank(Map<String, String> values, String key, String value) {
        if (!blank(value)) values.put(key, value.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    record NameRole(String name, String role) {
    }
}
