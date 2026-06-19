package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.domain.AdminEpisodeProgressStats;
import com.operation.seoul.admin.episode.dto.AdminEpisodeDetailResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftSaveRequest;
import com.operation.seoul.admin.episode.repository.AdminEpisodeRepository;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.domain.EpisodePartnerReward;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.MissionSpot;
import com.operation.seoul.episode.domain.Puzzle;
import com.operation.seoul.episode.domain.PuzzleHint;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.location.service.OperationAreaResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminEpisodeServiceAiDraftSaveTest {

    private static final List<String> ANSWER_TYPES = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    private static final List<String> ANSWER_VALUES = List.of("윤서진", "복제 출입증", "보복 심리", "출입 기록 바꿔치기");
    private static final List<String> FORBIDDEN_PLACE_HINT_TOKENS = List.of(
            "DESTINATION_HINT", "DESTINATION_CLUE", "FINAL_DESTINATION", "PLACE_HINT",
            "장소 힌트", "장소 정답", "장소 추리", "장소명을 추출", "주소 숫자", "최종 장소를 찾아라"
    );

    @Test
    void savesCrimeMysteryAiDraftAndReadsBackGeneratedEntities() {
        AdminEpisodeRepository repository = mock(AdminEpisodeRepository.class);
        InMemoryAdminEpisodeRepositoryState state = new InMemoryAdminEpisodeRepositoryState();
        state.bind(repository);
        AdminEpisodeService service = new AdminEpisodeService(
                repository,
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class)
        );
        AiEpisodeDraftSaveRequest request = saveRequest();

        AdminEpisodeDetailResponse saved = assertDoesNotThrow(() -> service.saveAiDraft(request));

        assertNotNull(saved.getId());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals("범죄 미스터리", saved.getGenre());
        assertFinalAnswerKeywordItems(request.getDraft().getFinalAnswerKeywordItems());
        assertSavedFinalAnswerAliases(saved);
        assertSavedFinalAnswerKeywordItems(saved);
        assertSavedMissionStructure(saved);
        assertEquals("ALL_INVESTIGATION_MISSIONS_CLEARED", finalMission(request.getDraft()).getUnlockCondition());
    }

    @Test
    void normalizesAiDraftPuzzleTypeLabelsBeforeSave() {
        AdminEpisodeRepository repository = mock(AdminEpisodeRepository.class);
        InMemoryAdminEpisodeRepositoryState state = new InMemoryAdminEpisodeRepositoryState();
        state.bind(repository);
        AdminEpisodeService service = new AdminEpisodeService(
                repository,
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class)
        );
        AiEpisodeDraftSaveRequest request = saveRequest();
        request.getDraft().getMissions().get(1).setPuzzleType("숫자 암호");

        AdminEpisodeDetailResponse saved = assertDoesNotThrow(() -> service.saveAiDraft(request));

        AdminEpisodeDetailResponse.Spot normalizedSpot = saved.getSpots().stream()
                .filter(spot -> spot.getPuzzle() != null)
                .filter(spot -> "NUMBER_LOCK".equals(spot.getPuzzle().getPuzzleType()))
                .findFirst()
                .orElseThrow();
        assertEquals("NUMBER_LOCK", normalizedSpot.getPuzzle().getPuzzleType());
    }

    @Test
    void rejectsMojibakeAiDraftBeforeSave() {
        AdminEpisodeRepository repository = mock(AdminEpisodeRepository.class);
        InMemoryAdminEpisodeRepositoryState state = new InMemoryAdminEpisodeRepositoryState();
        state.bind(repository);
        AdminEpisodeService service = new AdminEpisodeService(
                repository,
                new ObjectMapper(),
                mock(TourApiService.class),
                mock(OperationAreaResolver.class),
                mock(KakaoLocalCandidateService.class)
        );
        AiEpisodeDraftSaveRequest request = saveRequest();
        request.getDraft().setEpisodeTitle("조선 후기, 속초의 그림자 譏硫え ???");

        ApiException exception = assertThrows(ApiException.class, () -> service.saveAiDraft(request));

        assertEquals("MOJIBAKE_TEXT_DETECTED", exception.getCode());
    }

    private AiEpisodeDraftSaveRequest saveRequest() {
        AiEpisodeDraftSaveRequest request = new AiEpisodeDraftSaveRequest();
        request.setStatus("DRAFT");
        request.setDraft(draftFixture());
        request.setSourceInput(sourceInputFixture());
        return request;
    }

    private AiEpisodeDraftResponse.EpisodeDraft draftFixture() {
        AiEpisodeDraftResponse.FinalAnswers answers = AiEpisodeDraftResponse.FinalAnswers.builder()
                .culprit(ANSWER_VALUES.get(0))
                .weapon(ANSWER_VALUES.get(1))
                .motive(ANSWER_VALUES.get(2))
                .method(ANSWER_VALUES.get(3))
                .build();
        return AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("정동 기록 조작 사건")
                .subtitle("허구 사건 파일")
                .genre("범죄 미스터리")
                .era("현재")
                .fictionSynopsis("정동 일대 전시 기록을 둘러싼 허구의 조작 사건이다.")
                .missionDescription("8개의 조사 단서를 모아 범인, 흉기, 동기, 방법을 판단한다.")
                .selectedGenre("범죄 미스터리")
                .finalAnswerKeywords(new ArrayList<>(ANSWER_VALUES))
                .finalAnswerKeywordItems(answerKeywordItems())
                .finalAnswers(answers)
                .finalAnswerType("CASE_TRUTH")
                .finalAnswer("범인/흉기/동기/방법")
                .finalAnswerAliases(new ArrayList<>(ANSWER_VALUES))
                .finalQuestion("범인, 흉기, 동기, 방법을 입력하라.")
                .finalTruthSummary("전시 기록 조작의 진실을 밝힌다.")
                .actualHistorySummary("실제 장소는 배경 모티브이며 사건과 인물은 허구다.")
                .deductionSecretFacts(List.of("허구 사건의 핵심 진실은 단서 조합으로만 드러난다."))
                .deductionForbiddenReveals(new ArrayList<>(ANSWER_VALUES))
                .maxDeductionQuestions(20)
                .missions(missions())
                .suspects(suspects())
                .evidences(List.of())
                .build();
    }

    private AiEpisodeDraftRequest sourceInputFixture() {
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setSelectedGenreId("CRIME_MYSTERY");
        request.setSelectedGenreName("범죄 미스터리");
        request.setFinalAnswerKeywordItems(answerKeywordInputs());
        request.setFinalAnswerKeywords(new ArrayList<>(ANSWER_VALUES));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit(ANSWER_VALUES.get(0));
        answers.setWeapon(ANSWER_VALUES.get(1));
        answers.setMotive(ANSWER_VALUES.get(2));
        answers.setMethod(ANSWER_VALUES.get(3));
        request.setFinalAnswers(answers);
        return request;
    }

    private List<AiEpisodeDraftResponse.AnswerKeywordItem> answerKeywordItems() {
        List<AiEpisodeDraftResponse.AnswerKeywordItem> items = new ArrayList<>();
        for (int i = 0; i < ANSWER_TYPES.size(); i++) {
            items.add(AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                    .slotId(ANSWER_TYPES.get(i))
                    .type(ANSWER_TYPES.get(i))
                    .displayType(displayType(ANSWER_TYPES.get(i)))
                    .label(displayType(ANSWER_TYPES.get(i)))
                    .keyword(ANSWER_VALUES.get(i))
                    .value(ANSWER_VALUES.get(i))
                    .personName("CULPRIT".equals(ANSWER_TYPES.get(i)) ? ANSWER_VALUES.get(i) : "")
                    .aliases(List.of())
                    .build());
        }
        return items;
    }

    private List<AiEpisodeDraftRequest.AnswerKeywordInput> answerKeywordInputs() {
        List<AiEpisodeDraftRequest.AnswerKeywordInput> items = new ArrayList<>();
        for (int i = 0; i < ANSWER_TYPES.size(); i++) {
            AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
            item.setSlotId(ANSWER_TYPES.get(i));
            item.setType(ANSWER_TYPES.get(i));
            item.setDisplayType(displayType(ANSWER_TYPES.get(i)));
            item.setLabel(displayType(ANSWER_TYPES.get(i)));
            item.setKeyword(ANSWER_VALUES.get(i));
            item.setPersonName("CULPRIT".equals(ANSWER_TYPES.get(i)) ? ANSWER_VALUES.get(i) : "");
            item.setAliases(List.of());
            items.add(item);
        }
        return items;
    }

    private List<AiEpisodeDraftResponse.MissionDraft> missions() {
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        missions.add(mission(1, "START", null, "도입 기록은 사건 개요와 조사 규칙만 설명한다.", false));
        for (int i = 0; i < 8; i++) {
            String target = switch (i) {
                case 0, 1 -> "CULPRIT";
                case 2, 3 -> "WEAPON";
                case 4, 5 -> "MOTIVE";
                default -> "METHOD";
            };
            missions.add(mission(
                    i + 2,
                    "ANSWER_HINT",
                    target,
                    (i + 1) + "번째 조사 단서는 " + displayType(target) + " 판단에 필요한 시간표, 접근 권한, 진술 모순을 분리해 보여준다.",
                    false
            ));
        }
        missions.add(mission(10, "FINAL", null, "최종 지점에서는 8개 조사 단서를 종합해 네 가지 정답을 입력한다.", true));
        return missions;
    }

    private AiEpisodeDraftResponse.MissionDraft mission(
            int order,
            String markerType,
            String targetKeywordType,
            String rewardClue,
            boolean finalPlace) {
        return AiEpisodeDraftResponse.MissionDraft.builder()
                .order(order)
                .placeName("정동 조사 지점 " + order)
                .actualPlaceName("정동 조사 지점 " + order)
                .address("서울 중구 정동 " + order)
                .latitude(37.56 + order * 0.001)
                .longitude(126.97 + order * 0.001)
                .markerType(markerType)
                .publicMarkerType("START".equals(markerType) ? "START" : "ANSWER_HINT")
                .clueRole(finalPlace ? "FINAL_PLACE" : "START".equals(markerType) ? "START" : "ANSWER_HINT")
                .finalPlace(finalPlace)
                .storyText("허구 사건의 현장 기록 " + order + "번이다. 실제 장소 범죄를 암시하지 않는다.")
                .arrivalRadius(50d)
                .puzzleType("OBSERVATION")
                .questionText("현장 기록 " + order + "에서 확인해야 할 단어를 입력하라.")
                .answer("기록" + order)
                .answerFormat("TEXT")
                .rewardClue(rewardClue)
                .rewardClueSlotId("ANSWER_CLUE")
                .rewardClueLabel("추리 단서")
                .targetKeywordType(targetKeywordType)
                .targetKeywordDisplayType(targetKeywordType == null ? null : displayType(targetKeywordType))
                .unlockCondition(finalPlace ? "ALL_INVESTIGATION_MISSIONS_CLEARED" : null)
                .supportsKeywordSlots(targetKeywordType == null ? List.of() : List.of(targetKeywordType))
                .hints(List.of("표식을 확인한다.", "기록 순서를 본다.", "진술과 비교한다."))
                .groundRule("실제 장소는 배경으로만 사용한다.")
                .verificationLevel("AUTO_OK")
                .build();
    }

    private List<AiEpisodeDraftResponse.SuspectDraft> suspects() {
        return List.of(
                suspect("기록 담당자", "담당자는 일부 시간대 설명이 비어 있다."),
                suspect("전시 보조원", "보조원은 접근 권한 설명이 서로 다르다."),
                suspect("외부 협력자", "협력자는 물품 이동 시각을 다르게 진술했다.")
        );
    }

    private AiEpisodeDraftResponse.SuspectDraft suspect(String name, String suspiciousPoint) {
        return AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(name)
                .alias(name)
                .shortDescription("허구 사건의 용의자")
                .relationToVictim("사건 관계자")
                .suspiciousPoint(suspiciousPoint)
                .alibiSummary("알리바이는 조사 단서와 대조해야 한다.")
                .build();
    }

    private void assertFinalAnswerKeywordItems(List<AiEpisodeDraftResponse.AnswerKeywordItem> items) {
        assertEquals(4, items.size());
        for (int i = 0; i < ANSWER_TYPES.size(); i++) {
            assertEquals(ANSWER_TYPES.get(i), items.get(i).getType());
            assertEquals(displayType(ANSWER_TYPES.get(i)), items.get(i).getDisplayType());
            assertEquals(ANSWER_VALUES.get(i), items.get(i).getValue());
            assertNotNull(items.get(i).getAliases());
        }
    }

    private void assertSavedFinalAnswerAliases(AdminEpisodeDetailResponse saved) {
        for (String value : ANSWER_VALUES) {
            assertTrue(saved.getFinalAnswerAliases().contains(value));
        }
        assertTrue(saved.getFinalAnswerAliases().contains("KW:" + String.join("|", ANSWER_VALUES)));
    }

    private void assertSavedFinalAnswerKeywordItems(AdminEpisodeDetailResponse saved) {
        assertEquals(ANSWER_VALUES, saved.getFinalAnswerKeywords());
        assertEquals(4, saved.getFinalAnswerKeywordItems().size());
        for (int i = 0; i < ANSWER_TYPES.size(); i++) {
            assertEquals(ANSWER_TYPES.get(i), saved.getFinalAnswerKeywordItems().get(i).getType());
            assertEquals(displayType(ANSWER_TYPES.get(i)), saved.getFinalAnswerKeywordItems().get(i).getDisplayType());
            assertEquals(ANSWER_VALUES.get(i), saved.getFinalAnswerKeywordItems().get(i).getValue());
            assertNotNull(saved.getFinalAnswerKeywordItems().get(i).getAliases());
        }
    }

    private void assertSavedMissionStructure(AdminEpisodeDetailResponse saved) {
        assertEquals(10, saved.getSpots().size());
        assertEquals(1, saved.getSpots().stream().filter(spot -> "START".equals(spot.getMarkerType())).count());
        assertEquals(1, saved.getSpots().stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count());
        List<AdminEpisodeDetailResponse.Spot> investigation = saved.getSpots().stream()
                .filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType()))
                .filter(spot -> !Boolean.TRUE.equals(spot.getFinalPlace()))
                .toList();
        assertEquals(8, investigation.size());
        for (AdminEpisodeDetailResponse.Spot spot : investigation) {
            assertNotNull(spot.getPuzzle());
            assertFalse(spot.getPuzzle().getRewardClue().isBlank());
            assertNoAnswerLeak(spot.getPuzzle().getRewardClue());
            assertNoForbiddenPlaceHint(spot.getMarkerType());
            assertNoForbiddenPlaceHint(spot.getPuzzle().getRewardPayload());
        }
        saved.getSpots().stream()
                .filter(spot -> "START".equals(spot.getMarkerType()) || Boolean.TRUE.equals(spot.getFinalPlace()))
                .map(AdminEpisodeDetailResponse.Spot::getStoryText)
                .forEach(this::assertNoAnswerLeak);
    }

    private AiEpisodeDraftResponse.MissionDraft finalMission(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft.getMissions().stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()))
                .findFirst()
                .orElseThrow();
    }

    private void assertNoAnswerLeak(String text) {
        for (String value : ANSWER_VALUES) {
            assertFalse(compact(text).contains(compact(value)), "direct answer leak: " + value);
        }
    }

    private void assertNoForbiddenPlaceHint(String text) {
        for (String token : FORBIDDEN_PLACE_HINT_TOKENS) {
            assertFalse(text != null && text.contains(token), "forbidden place hint token: " + token);
        }
    }

    private String displayType(String type) {
        return switch (type) {
            case "CULPRIT" -> "범인";
            case "WEAPON" -> "흉기";
            case "MOTIVE" -> "동기";
            case "METHOD" -> "방법";
            default -> "";
        };
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private static class InMemoryAdminEpisodeRepositoryState {
        private final AtomicLong episodeIds = new AtomicLong(1);
        private final AtomicLong spotIds = new AtomicLong(10);
        private final AtomicLong puzzleIds = new AtomicLong(100);
        private final AtomicLong suspectIds = new AtomicLong(1_000);
        private final AtomicLong evidenceIds = new AtomicLong(2_000);
        private final AtomicLong rewardIds = new AtomicLong(3_000);
        private final AtomicLong hintIds = new AtomicLong(4_000);
        private final Map<Long, Episode> episodes = new LinkedHashMap<>();
        private final Map<Long, MissionSpot> spots = new LinkedHashMap<>();
        private final Map<Long, Puzzle> puzzles = new LinkedHashMap<>();
        private final Map<Long, List<PuzzleHint>> hintsByPuzzle = new LinkedHashMap<>();
        private final Map<Long, CaseSuspect> suspects = new LinkedHashMap<>();
        private final Map<Long, CaseEvidence> evidences = new LinkedHashMap<>();
        private final Map<Long, EpisodePartnerReward> rewards = new LinkedHashMap<>();

        void bind(AdminEpisodeRepository repository) {
            when(repository.insertEpisode(any(Episode.class))).thenAnswer(invocation -> {
                Episode episode = invocation.getArgument(0);
                episode.setId(episodeIds.getAndIncrement());
                episodes.put(episode.getId(), episode);
                return 1;
            });
            when(repository.findEpisode(anyLong())).thenAnswer(invocation -> episodes.get(invocation.getArgument(0)));
            when(repository.insertSpot(any(MissionSpot.class))).thenAnswer(invocation -> {
                MissionSpot spot = invocation.getArgument(0);
                spot.setId(spotIds.getAndIncrement());
                spots.put(spot.getId(), spot);
                return 1;
            });
            when(repository.findSpots(anyLong())).thenAnswer(invocation -> spots.values().stream()
                    .filter(spot -> invocation.<Long>getArgument(0).equals(spot.getEpisodeId()))
                    .toList());
            when(repository.insertPuzzle(any(Puzzle.class))).thenAnswer(invocation -> {
                Puzzle puzzle = invocation.getArgument(0);
                puzzle.setId(puzzleIds.getAndIncrement());
                puzzles.put(puzzle.getId(), puzzle);
                return 1;
            });
            when(repository.findPuzzleBySpotId(anyLong())).thenAnswer(invocation -> puzzles.values().stream()
                    .filter(puzzle -> invocation.<Long>getArgument(0).equals(puzzle.getMissionSpotId()))
                    .findFirst()
                    .orElse(null));
            when(repository.updatePuzzle(any(Puzzle.class))).thenAnswer(invocation -> {
                Puzzle puzzle = invocation.getArgument(0);
                puzzles.put(puzzle.getId(), puzzle);
                return 1;
            });
            when(repository.insertHint(anyLong(), anyInt(), anyString())).thenAnswer(invocation -> {
                Long puzzleId = invocation.getArgument(0);
                PuzzleHint hint = new PuzzleHint();
                hint.setId(hintIds.getAndIncrement());
                hint.setPuzzleId(puzzleId);
                hint.setHintLevel(invocation.getArgument(1));
                hint.setHintText(invocation.getArgument(2));
                hintsByPuzzle.computeIfAbsent(puzzleId, ignored -> new ArrayList<>()).add(hint);
                return 1;
            });
            when(repository.findHints(anyLong())).thenAnswer(invocation -> hintsByPuzzle.getOrDefault(invocation.getArgument(0), List.of()));
            when(repository.insertSuspect(any(CaseSuspect.class))).thenAnswer(invocation -> {
                CaseSuspect suspect = invocation.getArgument(0);
                suspect.setId(suspectIds.getAndIncrement());
                suspects.put(suspect.getId(), suspect);
                return 1;
            });
            when(repository.findSuspects(anyLong())).thenAnswer(invocation -> suspects.values().stream()
                    .filter(suspect -> invocation.<Long>getArgument(0).equals(suspect.getEpisodeId()))
                    .toList());
            when(repository.insertEvidence(any(CaseEvidence.class))).thenAnswer(invocation -> {
                CaseEvidence evidence = invocation.getArgument(0);
                evidence.setId(evidenceIds.getAndIncrement());
                evidences.put(evidence.getId(), evidence);
                return 1;
            });
            when(repository.findEvidences(anyLong())).thenAnswer(invocation -> evidences.values().stream()
                    .filter(evidence -> invocation.<Long>getArgument(0).equals(evidence.getEpisodeId()))
                    .toList());
            when(repository.insertPartnerReward(any(EpisodePartnerReward.class))).thenAnswer(invocation -> {
                EpisodePartnerReward reward = invocation.getArgument(0);
                reward.setId(rewardIds.getAndIncrement());
                rewards.put(reward.getId(), reward);
                return 1;
            });
            when(repository.findPartnerRewards(anyLong())).thenAnswer(invocation -> rewards.values().stream()
                    .filter(reward -> invocation.<Long>getArgument(0).equals(reward.getEpisodeId()))
                    .toList());
            when(repository.findProgressStats(anyLong())).thenReturn(new AdminEpisodeProgressStats());
        }
    }
}
