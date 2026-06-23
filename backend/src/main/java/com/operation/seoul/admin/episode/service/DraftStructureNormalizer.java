package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DraftStructureNormalizer {
    private static final String GENRE_NAME = "범죄 미스터리";
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;
    private static final Map<String, String> SLOT_LABELS = FinalAnswerSlots.LABELS;

    private DraftStructureNormalizer() {
    }

    static void normalizeDraft(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : draft.getMaxDeductionQuestions());
        if (!blank(draft.getActualHistorySummary())) {
            draft.setActualHistorySummary(draft.getActualHistorySummary().trim());
        }
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        if (places.size() != 10) return;
        List<AiEpisodeDraftResponse.MissionDraft> source = safeList(draft.getMissions());
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = places.get(i);
            AiEpisodeDraftResponse.MissionDraft mission = i < source.size() ? source.get(i) : new AiEpisodeDraftResponse.MissionDraft();
            boolean start = i == 0;
            boolean finalPlace = i == places.size() - 1;
            mission.setOrder(i + 1);
            mission.setPlaceName(defaultIfBlank(place.getName(), "spot " + (i + 1)));
            mission.setAddress(place.getAddress());
            mission.setLatitude(place.getLatitude());
            mission.setLongitude(place.getLongitude());
            mission.setMarkerType(start ? "START" : finalPlace ? "FINAL" : "ANSWER_HINT");
            mission.setPublicMarkerType(start ? "START" : "ANSWER_HINT");
            mission.setClueRole(start ? "START" : finalPlace ? "FINAL_PLACE" : "ANSWER_HINT");
            mission.setFinalPlace(finalPlace);
            mission.setPuzzleType(defaultIfBlank(mission.getPuzzleType(), "STORY_COMBINATION"));
            mission.setQuestionText(defaultIfBlank(mission.getQuestionText(), "현장 기록과 사건 자료를 비교해 답하세요."));
            mission.setAnswer(defaultIfBlank(mission.getAnswer(), "단서" + (i + 1)));
            mission.setAnswerFormat(defaultIfBlank(mission.getAnswerFormat(), "TEXT"));
            mission.setHints(ensureThreeHints(mission.getHints()));
            if (!start && !finalPlace) {
                String target = defaultTargetKeywordType(i - 1);
                mission.setTargetKeywordType(target);
                mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
                mission.setRewardClueSlotId("ANSWER_CLUE");
                mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
                mission.setSupportsKeywordSlots(List.of(target));
                mission.setRewardClue(blank(mission.getRewardClue()) ? null : mission.getRewardClue().trim());
                if (!blank(mission.getStoryText())) {
                    mission.setStoryText(mission.getStoryText().trim());
                }
            } else if (start) {
                if (!blank(mission.getStoryText())) {
                    mission.setStoryText(mission.getStoryText().trim());
                }
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                if (!blank(mission.getRewardClue())) {
                    mission.setRewardClue(mission.getRewardClue().trim());
                }
                if (!blank(mission.getStoryText())) {
                    mission.setStoryText(mission.getStoryText().trim());
                }
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private static String defaultTargetKeywordType(int investigationIndex) {
        return SLOT_IDS.get(Math.min(3, Math.max(0, investigationIndex / 2)));
    }

    private static List<String> ensureThreeHints(List<String> hints) {
        List<String> result = new ArrayList<>(safeList(hints).stream().filter(value -> !blank(value)).limit(3).toList());
        while (result.size() < 3) result.add("시간, 접근 권한, 물질 흔적을 비교하세요.");
        return result;
    }

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String defaultIfBlank(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
}
