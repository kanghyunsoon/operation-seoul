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
        draft.setActualHistorySummary(defaultIfBlank(draft.getActualHistorySummary(), "이 지역의 문화적 배경과 장소의 분위기를 바탕으로 사건의 모티브를 구성했습니다."));
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
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), investigationStoryText(i + 1, target)));
            } else if (start) {
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "사건 파일의 첫 장을 열고 피해자, 용의자, 사건 시간대를 확인합니다."));
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "조사 미션 8개 완료 시 자동 공개"));
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "모든 조사 단서를 대조한 뒤 범인, 흉기, 동기, 방법을 최종 입력합니다."));
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private static String investigationStoryText(int order, String target) {
        return switch (order) {
            case 2 -> "사건 발생 시간대의 출입 기록과 내부 동선을 대조해 실제 접근 가능했던 인물을 좁힙니다.";
            case 3 -> "알리바이 기록, CCTV 공백, 휴대폰 위치 기록을 비교해 용의자들의 진술이 맞는지 확인합니다.";
            case 4 -> "피해자 주변에서 나온 물질 흔적을 분석해 사망 원인과 연결되는 물증을 확인합니다.";
            case 5 -> "현장에 남은 물건의 상태와 보관 위치를 대조해 어떤 도구가 범행에 쓰였는지 추적합니다.";
            case 6 -> "피해자와 용의자들 사이의 계약, 장부, 문서 기록을 확인해 사건의 이해관계를 찾습니다.";
            case 7 -> "사건 직전의 메시지, 통화, 목격 진술을 비교해 누가 가장 강한 압박을 받았는지 판단합니다.";
            case 8 -> "피해자가 반복하던 행동과 사건 당일 준비물의 변화를 대조해 범행 순서를 복원합니다.";
            case 9 -> "마지막 조작 흔적과 접근 순서를 맞춰 범행 방법이 어떻게 실행됐는지 확정합니다.";
            default -> "수집한 사건 기록을 대조해 " + SLOT_LABELS.getOrDefault(target, "정답") + " 판단에 필요한 단서를 확인합니다.";
        };
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