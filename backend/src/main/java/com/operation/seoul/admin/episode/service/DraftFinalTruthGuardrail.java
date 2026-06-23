package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.stream.Stream;

final class DraftFinalTruthGuardrail {
    private DraftFinalTruthGuardrail() {
    }

    static boolean explainsAnswers(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            String culprit,
            String weapon,
            String motive,
            String method) {
        String truth = compact(draft.getFinalTruthSummary());
        return Stream.of(culprit, weapon, motive, method)
                .allMatch(value -> !blank(value) && truth.contains(compact(value)));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }
}
