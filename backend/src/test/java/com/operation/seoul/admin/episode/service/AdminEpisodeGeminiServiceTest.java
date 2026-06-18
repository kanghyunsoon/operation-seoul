package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEpisodeGeminiServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminEpisodeGeminiService service = new AdminEpisodeGeminiService(objectMapper);

    @Test
    void normalizesMissingCaseObjectiveAndAcceptsAchievableHintDistribution() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyApprovedFinalAnswerContract",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());

        assertEquals("실종 사건", draft.getGenre());
        assertEquals("실종 사건", draft.getSelectedGenre());
        assertTrue(draft.getFinalQuestion().contains("관련자"));
        assertTrue(draft.getFinalQuestion().contains("핵심 단서"));
        assertTrue(draft.getFinalQuestion().contains("최종 장소"));
        assertTrue(draft.getFinalAnswer().contains("한서림"));
        assertTrue(draft.getFinalAnswer().contains("지하 연결로"));
        assertTrue(draft.getFinalAnswer().contains("찢어진 수첩"));
        assertEquals("한서림", draft.getFinalAnswers().getRelatedPerson());
        assertEquals("찢어진 수첩", draft.getFinalAnswers().getCoreClue());
        assertEquals("지하 연결로", draft.getFinalAnswers().getFinalLocation());
        assertFalse(draft.getFictionSynopsis().contains("테스트 지점 1"));
        assertTrue(draft.getFictionSynopsis().contains("핵심 단서"));
        assertTrue(draft.getFictionSynopsis().contains("최종 장소"));
        assertFalse(draft.getFictionSynopsis().contains("요원"));
        assertFalse(draft.getFictionSynopsis().contains("시간이 많지 않네"));
        assertFalse(draft.getFictionSynopsis().contains("미션 파일을 확인하고"));
        assertFalse(draft.getFictionSynopsis().contains("한서림"));
        assertFalse(draft.getFictionSynopsis().contains("찢어진 수첩"));
        assertFalse(draft.getFictionSynopsis().contains("지하 연결로"));

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);
        request.setSourceInput(source);
        request.setUseGemini(false);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);
        List<String> codes = result.getFindings().stream()
                .map(AiEpisodeDraftValidationResponse.Finding::getCode)
                .toList();

        assertFalse(codes.contains("STORY_OBJECTIVE_MISMATCH"));
        assertFalse(codes.contains("LOW_ANSWER_HINT_COUNT"));
        assertFalse(codes.contains("LOW_DESTINATION_HINT_COUNT"));
    }

    private AiEpisodeDraftRequest sourceInput() {
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setSelectedGenreId("MISSING_CASE");
        request.setSelectedGenreName("실종 사건");

        AiEpisodeDraftRequest.MissionPolicyInput missionPolicy = new AiEpisodeDraftRequest.MissionPolicyInput();
        missionPolicy.setMissionCount(9);
        missionPolicy.setStartCount(1);
        missionPolicy.setFinalCount(1);
        missionPolicy.setAnswerHintRatio(0.6);
        missionPolicy.setDestinationHintRatio(0.4);
        request.setMissionPolicy(missionPolicy);

        AiEpisodeDraftRequest.PuzzlePolicyInput puzzlePolicy = new AiEpisodeDraftRequest.PuzzlePolicyInput();
        puzzlePolicy.setForbidFinalKeywordAsPuzzleAnswer(true);
        puzzlePolicy.setBlockedGenericAnswers(List.of("기록", "단서", "문서", "물건", "장소", "검수필요"));
        request.setPuzzlePolicy(puzzlePolicy);

        request.setFinalAnswerKeywordItems(List.of(
                keyword("RELATED_PERSON", "관련자", "한서림"),
                keyword("ANSWER_CLUE", "핵심 단서", "찢어진 수첩"),
                keyword("FINAL_DESTINATION", "최종 장소", "지하 연결로")
        ));
        request.setFinalAnswerKeywords(List.of("한서림", "찢어진 수첩", "지하 연결로"));
        AiEpisodeDraftRequest.FinalAnswersInput finalAnswers = new AiEpisodeDraftRequest.FinalAnswersInput();
        finalAnswers.setRelatedPerson("한서림");
        finalAnswers.setCoreClue("찢어진 수첩");
        finalAnswers.setFinalLocation("지하 연결로");
        request.setFinalAnswers(finalAnswers);

        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            AiEpisodeDraftRequest.PlaceInput place = new AiEpisodeDraftRequest.PlaceInput();
            place.setName("테스트 지점 " + (i + 1));
            place.setAddress("서울 테스트로 " + (i + 1));
            place.setLatitude(37.5 + i * 0.001);
            place.setLongitude(126.9 + i * 0.001);
            place.setVisibleElements(List.of("청색 표식 " + (i + 1)));
            place.setNumbers(List.of(String.valueOf(1100 + i)));
            place.setKeywords(List.of("금속 고리 " + (i + 1)));
            place.setRole(role(i));
            place.setPublicMarkerType(i == 8 ? "DESTINATION_HINT" : role(i));
            places.add(place);
        }
        request.setPlaces(places);
        return request;
    }

    private AiEpisodeDraftRequest.AnswerKeywordInput keyword(String slotId, String label, String value) {
        AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
        item.setSlotId(slotId);
        item.setLabel(label);
        item.setKeyword(value);
        item.setAliases(List.of());
        return item;
    }

    private AiEpisodeDraftResponse.EpisodeDraft playableDraft(AiEpisodeDraftRequest source) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < source.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = source.getPlaces().get(i);
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(place.getName())
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(place.getRole())
                    .publicMarkerType(place.getPublicMarkerType())
                    .finalPlace(i == 8)
                    .storyText("서로 다른 관찰 기록을 대조하는 지점입니다.")
                    .puzzleType("STORY_COMBINATION")
                    .questionText("제공된 현장 요소 중 반복된 색과 번호를 확인하세요.")
                    .answer("청색 표식 " + (i + 1))
                    .answerFormat("TEXT")
                    .rewardClue("금속 고리 주변에 같은 방향의 긁힌 흔적이 이어져 있습니다.")
                    .hints(List.of("색을 확인하세요.", "번호를 확인하세요.", "두 요소를 함께 보세요."))
                    .groundRule("visibleElements 기반")
                    .build());
        }

        return AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("연락이 끊긴 기록")
                .subtitle("용산 현장 추적")
                .era("현대")
                .finalAnswerType("HIDDEN_TRUTH")
                .missions(missions)
                .finalAnswerAliases(new ArrayList<>())
                .build();
    }

    private String role(int index) {
        if (index == 0) return "START";
        if (index == 8) return "FINAL";
        return index <= 4 ? "ANSWER_HINT" : "DESTINATION_HINT";
    }
}
