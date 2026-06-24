package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class DraftNarrativeGuardrail {
    private DraftNarrativeGuardrail() {
    }

    static boolean shouldRepairSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        String synopsis = trim(draft == null ? "" : draft.getFictionSynopsis());
        String compacted = compact(synopsis);
        if (synopsis.length() < 140) return true;
        if (!containsAny(compacted, "피해자", "숨진", "사망", "발견", "외부침입", "잠겨", "용의자", "세명", "3명")) {
            return true;
        }
        for (AiEpisodeDraftRequest.PlaceInput place : request == null || request.getPlaces() == null ? List.<AiEpisodeDraftRequest.PlaceInput>of() : request.getPlaces()) {
            String placeName = trim(place.getName());
            if (placeName.length() >= 3 && synopsis.contains(placeName)) {
                return true;
            }
        }
        return false;
    }

    static boolean synopsisMentionsAllSuspects(AiEpisodeDraftResponse.EpisodeDraft draft) {
        String synopsis = compact(draft.getFictionSynopsis());
        if (blank(synopsis)) return false;
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        return suspects.stream()
                .filter(Objects::nonNull)
                .allMatch(suspect -> synopsisMentionsSuspect(synopsis, suspect));
    }

    private static boolean synopsisMentionsSuspect(String synopsis, AiEpisodeDraftResponse.SuspectDraft suspect) {
        return containsCompact(synopsis, suspect.getDisplayName())
                || containsCompact(synopsis, suspect.getAlias())
                || containsCompact(synopsis, suspect.getRelationToVictim());
    }

    private static boolean containsCompact(String source, String value) {
        String compacted = compact(value);
        return !blank(compacted) && source.contains(compacted);
    }

    static boolean redactRealPlaceNamesFromStoryFields(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        if (draft == null || request == null || request.getPlaces() == null) return false;
        List<String> placeNames = request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .map(DraftNarrativeGuardrail::trim)
                .filter(name -> name.length() >= 3)
                .distinct()
                .toList();
        if (placeNames.isEmpty()) return false;
        String beforeDraftText = String.join(" ",
                trim(draft.getEpisodeTitle()),
                trim(draft.getSubtitle()),
                trim(draft.getFictionSynopsis()),
                trim(draft.getMissionDescription()),
                trim(draft.getFinalTruthSummary()),
                trim(draft.getActualHistorySummary()));
        boolean changed = containsAnyPlaceName(beforeDraftText, placeNames);
        draft.setEpisodeTitle(redactRealPlaceNames(draft.getEpisodeTitle(), placeNames, "case scene"));
        draft.setSubtitle(redactRealPlaceNames(draft.getSubtitle(), placeNames, "case scene"));
        draft.setFictionSynopsis(redactRealPlaceNames(draft.getFictionSynopsis(), placeNames, "case scene"));
        draft.setMissionDescription(redactRealPlaceNames(draft.getMissionDescription(), placeNames, "investigation point"));
        draft.setFinalTruthSummary(redactRealPlaceNames(draft.getFinalTruthSummary(), placeNames, "case scene"));
        draft.setActualHistorySummary(redactRealPlaceNames(draft.getActualHistorySummary(), placeNames, "final point"));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            String before = String.join(" ",
                    trim(mission.getStoryText()),
                    trim(mission.getQuestionText()),
                    trim(mission.getRewardClue()));
            mission.setStoryText(redactRealPlaceNames(mission.getStoryText(), placeNames, "investigation point"));
            mission.setQuestionText(redactRealPlaceNames(mission.getQuestionText(), placeNames, "investigation point"));
            mission.setRewardClue(redactRealPlaceNames(mission.getRewardClue(), placeNames, "investigation point"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            String before = String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary()));
            evidence.setTitle(redactRealPlaceNames(evidence.getTitle(), placeNames, "case file"));
            evidence.setTextSummary(redactRealPlaceNames(evidence.getTextSummary(), placeNames, "case file"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        return changed;
    }

    static boolean normalizeSuspectVictimReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null || draft.getSuspects() == null) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.SuspectDraft suspect : draft.getSuspects()) {
            if (suspect == null) continue;
            String shortDescription = normalizeVictimReference(suspect.getShortDescription());
            String relationToVictim = normalizeVictimReference(suspect.getRelationToVictim());
            String suspiciousPoint = normalizeVictimReference(suspect.getSuspiciousPoint());
            String alibiSummary = normalizeVictimReference(suspect.getAlibiSummary());
            changed = changed
                    || !Objects.equals(shortDescription, suspect.getShortDescription())
                    || !Objects.equals(relationToVictim, suspect.getRelationToVictim())
                    || !Objects.equals(suspiciousPoint, suspect.getSuspiciousPoint())
                    || !Objects.equals(alibiSummary, suspect.getAlibiSummary());
            suspect.setShortDescription(shortDescription);
            suspect.setRelationToVictim(relationToVictim);
            suspect.setSuspiciousPoint(suspiciousPoint);
            suspect.setAlibiSummary(alibiSummary);
        }
        return changed;
    }

    private static String normalizeVictimReference(String value) {
        if (value == null) return null;
        return value.replace("김준혁", "한태준");
    }

    private static String redactRealPlaceNames(String value, List<String> placeNames, String replacement) {
        if (blank(value)) return value;
        String result = value;
        for (String placeName : placeNames) {
            result = result.replace(placeName, replacement);
        }
        return result;
    }

    private static boolean containsAnyPlaceName(String value, List<String> placeNames) {
        if (blank(value)) return false;
        return placeNames.stream().anyMatch(value::contains);
    }

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(compact(target))) return true;
        }
        return false;
    }
}
