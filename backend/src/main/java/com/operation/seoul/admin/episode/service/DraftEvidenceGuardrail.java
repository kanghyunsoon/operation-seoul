package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class DraftEvidenceGuardrail {
    private DraftEvidenceGuardrail() {
    }

    static boolean hasUsableEvidences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        if (evidences.size() != 8) return false;
        Set<Integer> orders = evidences.stream()
                .map(AiEpisodeDraftResponse.EvidenceDraft::getSourceMissionOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (int order = 2; order <= 9; order++) {
            if (!orders.contains(order)) return false;
        }
        return evidences.stream().allMatch(evidence -> evidence != null && !blank(evidence.getTitle()) && !blank(evidence.getTextSummary()));
    }

    static boolean evidencesLeakFinalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answers = answerValues(draft).stream()
                .filter(value -> !blank(value))
                .map(DraftEvidenceGuardrail::compact)
                .toList();
        if (answers.isEmpty()) return false;
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            String text = compact(String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary())));
            if (answers.stream().anyMatch(answer -> !blank(answer) && text.contains(answer))) {
                return true;
            }
        }
        return false;
    }

    static List<AiEpisodeDraftResponse.EvidenceDraft> canonicalEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return safeList(missions).stream()
                .filter(mission -> mission != null && mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                        .title(mission.getOrder() + "번 조사 증거")
                        .type("STORY_CLUE")
                        .textSummary(mission.getRewardClue())
                        .sourceMissionOrder(mission.getOrder())
                        .build())
                .toList();
    }

    private static List<String> answerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers != null) return List.of(trim(answers.getCulprit()), trim(answers.getWeapon()), trim(answers.getMotive()), trim(answers.getMethod()));
        return draft.getFinalAnswerKeywords() == null ? List.of() : draft.getFinalAnswerKeywords();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
