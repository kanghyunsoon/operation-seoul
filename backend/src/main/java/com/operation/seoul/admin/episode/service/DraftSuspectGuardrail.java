package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DraftSuspectGuardrail {
    private DraftSuspectGuardrail() {
    }

    static boolean hasUsableSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        boolean hasCulprit = false;
        Set<String> names = new LinkedHashSet<>();
        for (AiEpisodeDraftResponse.SuspectDraft suspect : suspects) {
            if (suspect == null || blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                return false;
            }
            if (!names.add(compact(suspect.getDisplayName()))) {
                return false;
            }
            hasCulprit = hasCulprit || compact(trim(suspect.getDisplayName())).equals(compact(culprit));
        }
        return hasCulprit;
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
