package com.operation.seoul.admin.episode.service;

import java.util.List;
import java.util.Locale;

final class AiEpisodeObjectivePolicy {
    private AiEpisodeObjectivePolicy() {
    }

    static boolean finalQuestionNamesEverySlot(String question, List<String> labels) {
        return labels == null || labels.stream().allMatch(label -> textContains(question, label));
    }

    private static boolean textContains(String text, String target) {
        return !blank(text) && !blank(target) && compact(text).contains(compact(target));
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
