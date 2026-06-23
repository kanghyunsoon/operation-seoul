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
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 구체적인 인물, 물건, 동기, 범행 과정이어야 합니다. " + String.join(", ", weakSlots));
        }
    }

    static void repairWeakFinalAnswerKeywords(AiEpisodeDraftRequest request) {
        if (request == null) return;
        Map<String, String> values = approvedAnswers(request);
        String method = values.get("METHOD");
        if (weakFinalAnswerKeyword("METHOD", method)) {
            updateFinalAnswerKeyword(request, "METHOD", concreteMethodFor(values.get("WEAPON"), values.get("MOTIVE"), method));
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
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getValue());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    static boolean weakFinalAnswerKeyword(String slot, String value) {
        return FinalAnswerKeywordValidator.weakFinalAnswerKeyword(slot, value);
    }

    static NameRole splitNameRole(String value) {
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

    private static String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getSourceText());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private static String concreteMethodFor(String weapon, String motive, String currentMethod) {
        String text = compact(String.join(" ", trim(weapon), trim(motive), trim(currentMethod)));
        if (containsAny(text, "붓펜", "잉크", "서명", "원작", "위작", "감정")) {
            return "독성 잉크가 든 붓펜으로 감정 확인 서명란을 오염시킴";
        }
        if (containsAny(text, "분사", "스프레이")) {
            return "분사병에 마취 성분을 넣어 피해자에게 분사";
        }
        if (containsAny(text, "컵", "잔", "음료", "보온병", "마시")) {
            return "컵 가장자리에 수면제를 묻혀 피해자가 마시게 함";
        }
        if (containsAny(text, "약", "캡슐", "고산병", "복용")) {
            return "약 캡슐에 진정제를 섞어 피해자에게 복용시킴";
        }
        if (containsAny(text, "봉투", "문서", "분말")) {
            return "문서 봉투 접착면에 독성 분말을 묻혀 피해자가 만지게 함";
        }
        return "현장 준비물에 독성 성분을 묻혀 피해자에게 접촉시킴";
    }

    private static void updateFinalAnswerKeyword(AiEpisodeDraftRequest request, String slot, String value) {
        if (request.getFinalAnswers() == null) {
            request.setFinalAnswers(new AiEpisodeDraftRequest.FinalAnswersInput());
        }
        if ("CULPRIT".equals(slot)) request.getFinalAnswers().setCulprit(value);
        if ("WEAPON".equals(slot)) request.getFinalAnswers().setWeapon(value);
        if ("MOTIVE".equals(slot)) request.getFinalAnswers().setMotive(value);
        if ("METHOD".equals(slot)) request.getFinalAnswers().setMethod(value);
        if (request.getFinalAnswerKeywordItems() == null) return;
        for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
            String itemSlot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
            if (slot.equals(itemSlot)) {
                item.setKeyword(value);
                if ("CULPRIT".equals(slot)) item.setPersonName(value);
            }
        }
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

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(target)) return true;
        }
        return false;
    }

    record NameRole(String name, String role) {
    }
}
