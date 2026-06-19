package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AdminEpisodeGeminiIntegrationTest.GeminiIntegrationTestApplication.class)
@ActiveProfiles("local")
class AdminEpisodeGeminiIntegrationTest {

    private static final String GEMINI_API_KEY_PROPERTY = "gemini.api.key";
    private static final List<String> ANSWER_TYPES = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");

    @Autowired
    private AdminEpisodeGeminiService service;

    @Autowired
    private Environment environment;

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void generatesAndValidatesCrimeMysteryDraftWithRealGemini() {
        Assumptions.assumeTrue(
                isConfigured(environment.getProperty(GEMINI_API_KEY_PROPERTY)),
                "Gemini API key is not configured"
        );

        AiEpisodeDraftRequest request = requestWithTenTourApiPlaces();
        AiEpisodePlanResponse plan = service.createAnswerPlan(request);

        assertEquals("CRIME_MYSTERY", plan.getSelectedGenreId());
        assertAnswerKeywordContract(plan.getFinalAnswerKeywords());

        applyPlan(request, plan);
        AiEpisodeDraftResponse response = service.createGeminiDraft(request);
        AiEpisodeDraftResponse.EpisodeDraft draft = response.getDraft();

        assertNotNull(draft);
        assertEquals("범죄 미스터리", draft.getGenre());
        assertEquals(10, draft.getMissions().size());
        assertEquals(1, missionCount(draft, "START"));
        assertEquals(1, finalMissionCount(draft));
        assertEquals(8, investigationMissions(draft).size());
        assertFalse(draft.getMissions().stream().anyMatch(this::usesDestinationHint));

        assertDraftAnswerKeywordContract(draft.getFinalAnswerKeywordItems());
        assertInvestigationTargetDistribution(draft);

        AiEpisodeDraftValidationRequest validationRequest = new AiEpisodeDraftValidationRequest();
        validationRequest.setDraft(draft);
        validationRequest.setSourceInput(request);
        AiEpisodeDraftValidationResponse validation = service.validateDraft(validationRequest);

        assertTrue(validation.isValid(), () -> validation.getFindings().stream()
                .map(finding -> finding.getCode() + ": " + finding.getMessage())
                .collect(Collectors.joining(" / ")));
    }

    private AiEpisodeDraftRequest requestWithTenTourApiPlaces() {
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setArea("서울 정동");
        request.setEra("현재");
        request.setTheme("해가 저문 날 사라진 미술품과 익명 기록 사건");
        request.setTargetAudience("성인 추리 입문자");
        request.setPlayTime("90~120분");
        request.setSelectedGenreId("CRIME_MYSTERY");
        request.setSelectedGenreName("범죄 미스터리");

        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        places.add(place("대한문", "서울 중구 세종대로 99", 37.565804, 126.975146, "START", "궁궐 정문과 개방된 광장"));
        places.add(place("정동길 돌담길", "서울 중구 정동", 37.566258, 126.973766, "ANSWER_HINT", "돌담, 보행 동선, 시간 기록"));
        places.add(place("정동제일교회", "서울 중구 정동길 46", 37.566637, 126.972559, "ANSWER_HINT", "창문 격자와 출입 동선"));
        places.add(place("배재학당 역사박물관", "서울 중구 서소문로11길 19", 37.564815, 126.972420, "ANSWER_HINT", "전시 기록과 연도 표식"));
        places.add(place("서울시립미술관 서소문본관", "서울 중구 덕수궁길 61", 37.564104, 126.973747, "ANSWER_HINT", "전시 안내와 조명 구조"));
        places.add(place("정동극장", "서울 중구 정동길 43", 37.565840, 126.972007, "ANSWER_HINT", "공연 시간표와 출입 기록"));
        places.add(place("이화학당 터", "서울 중구 정동길 26", 37.565055, 126.971380, "ANSWER_HINT", "터 표석과 주변 보행 경로"));
        places.add(place("덕수궁 돌담길 안쪽 구간", "서울 중구 덕수궁길", 37.565410, 126.974120, "ANSWER_HINT", "시야가 열린 보행 구간"));
        places.add(place("서울시립미술관 앞마당", "서울 중구 덕수궁길 61", 37.564010, 126.973780, "ANSWER_HINT", "야외 조형물과 접근 동선"));
        places.add(place("중명전", "서울 중구 정동길 41-11", 37.566289, 126.971856, "FINAL", "벽돌 건축과 역사 전시 공간"));
        request.setPlaces(places);
        return request;
    }

    private AiEpisodeDraftRequest.PlaceInput place(
            String name,
            String address,
            double latitude,
            double longitude,
            String role,
            String motif) {
        AiEpisodeDraftRequest.PlaceInput place = new AiEpisodeDraftRequest.PlaceInput();
        place.setPlaceId("TOURAPI-" + name);
        place.setName(name);
        place.setAddress(address);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        place.setDescription(motif + ". 실제 범죄와 무관한 TourAPI 배경 모티브");
        place.setVisibleElements(List.of(motif));
        place.setNumbers(List.of());
        place.setKeywords(List.of("허구 사건", "추리 단서"));
        place.setAdminMemo("실제 장소 정보는 배경 모티브로만 사용하고 사건과 인물은 모두 허구로 작성한다.");
        place.setRole(role);
        place.setPublicMarkerType("START".equals(role) ? "START" : "ANSWER_HINT");
        place.setArrivalRadius(50d);
        placesQuality(place);
        return place;
    }

    private void placesQuality(AiEpisodeDraftRequest.PlaceInput place) {
        place.setDataQuality("NORMAL");
        place.setUsablePuzzleSources(List.of("VISIBLE_ELEMENT", "KEYWORD"));
        place.setVerificationNotes(List.of("공개 정보 기반 모티브"));
    }

    private void applyPlan(AiEpisodeDraftRequest request, AiEpisodePlanResponse plan) {
        List<AiEpisodePlanResponse.AnswerKeyword> planItems = plan.getFinalAnswerKeywordItems() != null
                && !plan.getFinalAnswerKeywordItems().isEmpty()
                ? plan.getFinalAnswerKeywordItems()
                : plan.getFinalAnswerKeywords();

        List<AiEpisodeDraftRequest.AnswerKeywordInput> items = planItems.stream()
                .map(item -> {
                    AiEpisodeDraftRequest.AnswerKeywordInput input = new AiEpisodeDraftRequest.AnswerKeywordInput();
                    input.setSlotId(item.getSlotId());
                    input.setLabel(item.getLabel());
                    input.setType(item.getType());
                    input.setDisplayType(item.getDisplayType());
                    input.setKeyword(item.getKeyword());
                    input.setPersonName(item.getPersonName());
                    input.setPersonRole(item.getPersonRole());
                    input.setAliases(item.getAliases());
                    return input;
                })
                .toList();
        request.setFinalAnswerKeywordItems(items);
        request.setFinalAnswerKeywords(items.stream().map(AiEpisodeDraftRequest.AnswerKeywordInput::getKeyword).toList());
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit(plan.getFinalAnswers().getCulprit());
        answers.setWeapon(plan.getFinalAnswers().getWeapon());
        answers.setMotive(plan.getFinalAnswers().getMotive());
        answers.setMethod(plan.getFinalAnswers().getMethod());
        request.setFinalAnswers(answers);
    }

    private void assertAnswerKeywordContract(List<AiEpisodePlanResponse.AnswerKeyword> items) {
        assertNotNull(items);
        assertEquals(4, items.size());
        assertEquals(
                ANSWER_TYPES,
                items.stream().map(AiEpisodePlanResponse.AnswerKeyword::getSlotId).toList()
        );
    }

    private void assertDraftAnswerKeywordContract(List<AiEpisodeDraftResponse.AnswerKeywordItem> items) {
        assertNotNull(items);
        assertEquals(4, items.size());
        assertEquals(
                ANSWER_TYPES,
                items.stream().map(AiEpisodeDraftResponse.AnswerKeywordItem::getType).toList()
        );
    }

    private void assertInvestigationTargetDistribution(AiEpisodeDraftResponse.EpisodeDraft draft) {
        Map<String, Long> targetCounts = investigationMissions(draft).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getTargetKeywordType)
                .filter(this::isConfigured)
                .collect(Collectors.groupingBy(String::toUpperCase, Collectors.counting()));
        for (String slot : ANSWER_TYPES) {
            assertEquals(2L, targetCounts.getOrDefault(slot, 0L), slot + " needs exactly two clues");
        }
    }

    private List<AiEpisodeDraftResponse.MissionDraft> investigationMissions(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft.getMissions().stream()
                .filter(mission -> !"START".equalsIgnoreCase(mission.getMarkerType()))
                .filter(mission -> !"FINAL".equalsIgnoreCase(mission.getMarkerType()))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .toList();
    }

    private long missionCount(AiEpisodeDraftResponse.EpisodeDraft draft, String markerType) {
        return draft.getMissions().stream()
                .filter(mission -> markerType.equalsIgnoreCase(mission.getMarkerType()))
                .count();
    }

    private long finalMissionCount(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft.getMissions().stream()
                .filter(mission -> "FINAL".equalsIgnoreCase(mission.getMarkerType())
                        || Boolean.TRUE.equals(mission.getFinalPlace()))
                .count();
    }

    private boolean usesDestinationHint(AiEpisodeDraftResponse.MissionDraft mission) {
        return "DESTINATION_HINT".equalsIgnoreCase(mission.getMarkerType())
                || "DESTINATION_HINT".equalsIgnoreCase(mission.getClueRole())
                || "DESTINATION_CLUE".equalsIgnoreCase(mission.getRewardClueSlotId())
                || "FINAL_DESTINATION".equalsIgnoreCase(mission.getRewardClueSlotId())
                || "PLACE_HINT".equalsIgnoreCase(mission.getRewardClueSlotId());
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    @SpringBootConfiguration
    static class GeminiIntegrationTestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AdminEpisodeGeminiService adminEpisodeGeminiService(ObjectMapper objectMapper) {
            return new AdminEpisodeGeminiService(objectMapper);
        }
    }
}
