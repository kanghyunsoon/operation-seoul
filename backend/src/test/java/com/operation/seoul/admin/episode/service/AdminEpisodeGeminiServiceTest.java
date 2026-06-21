package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEpisodeGeminiServiceTest {
    private final AdminEpisodeGeminiService service = new AdminEpisodeGeminiService(new ObjectMapper());

    @Test
    void appliesFixedCrimeMysteryAnswerContract() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);

        applyApprovedContract(draft, source);

        assertEquals("범죄 미스터리", draft.getGenre());
        assertEquals("범죄 미스터리", draft.getSelectedGenre());
        assertEquals(4, draft.getFinalAnswerKeywords().size());
        assertEquals("강수진", draft.getFinalAnswers().getCulprit());
        assertEquals("독성 캡슐", draft.getFinalAnswers().getWeapon());
        assertEquals("비밀 계약 은폐", draft.getFinalAnswers().getMotive());
        assertEquals("약병 바꿔치기", draft.getFinalAnswers().getMethod());
        assertTrue(draft.getFinalAnswer().contains("범인: 강수진"));
    }

    @Test
    void validatesTenPlaceEightClueStructure() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);
        request.setSourceInput(source);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertTrue(result.isValid());
        assertFalse(result.getFindings().stream().anyMatch(finding -> "DESTINATION_HINT_FORBIDDEN".equals(finding.getCode())));
    }

    @Test
    void rejectsDirectAnswerLeakInInvestigationClue() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getMissions().get(1).setRewardClue("추가 지문이 강수진의 지문과 일치한다.");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "DIRECT_ANSWER_LEAK".equals(finding.getCode())));
    }

    @Test
    void rejectsMojibakeGeneratedText() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setEpisodeTitle("조선 후기, 속초의 그림자 譏硫え ???");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "MOJIBAKE_TEXT_DETECTED".equals(finding.getCode())));
    }

    @Test
    void rejectsFinalPlaceUsedAsAnswerClue() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        AiEpisodeDraftResponse.MissionDraft finalMission = draft.getMissions().get(9);
        finalMission.setTargetKeywordType("METHOD");
        finalMission.setSupportsKeywordSlots(List.of("METHOD"));

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE".equals(finding.getCode())));
    }

    @Test
    void rejectsMissingFinalKeywordItemSlot() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFinalAnswerKeywordItems(draft.getFinalAnswerKeywordItems().subList(0, 3));

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED".equals(finding.getCode())));
    }

    @Test
    void rejectsImmersionBreakingGeneratedText() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setActualHistorySummary("RAG context and TourAPI reference were used for this fictional suspect.");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "IMMERSION_BREAKING_TEXT".equals(finding.getCode())));
    }

    @Test
    void rejectsGenericFallbackInvestigationClue() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getMissions().get(1).setRewardClue("2번 조사 단서는 범인 판단에 필요한 근거를 제공합니다.");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "GENERIC_DEDUCTIVE_CLUE".equals(finding.getCode())));
    }

    @Test
    void rejectsMissingEvidenceCards() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setEvidences(List.of());

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "EIGHT_EVIDENCE_CARDS_REQUIRED".equals(finding.getCode())));
    }

    @Test
    void rejectsCulpritMissingFromSuspects() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setSuspects(List.of(
                suspect("김민준", "출장 중이었다고 주장합니다.", "채무 문서에 접근했습니다."),
                suspect("이서연", "자택에 있었다고 주장합니다.", "피해자와 갈등이 있었습니다."),
                suspect("박지훈", "개인 약속이 있었다고 주장합니다.", "유산에 관심이 있었습니다.")
        ));

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "CULPRIT_MUST_BE_SUSPECT".equals(finding.getCode())));
    }

    @Test
    void rejectsClueSlotMismatch() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getMissions().get(3).setRewardClue("박 교수가 최근 거액의 채무 관련 서류를 보관하고 있었다는 증언");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "CLUE_SLOT_MISMATCH".equals(finding.getCode())));
    }

    @Test
    void deterministicGuardrailRepairsLeakedAndMisplacedGeminiClues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getMissions().get(1).setRewardClue("강수진이 피해자의 약을 바꿔치기했다.");
        draft.getMissions().get(1).setTargetKeywordType("CULPRIT");
        draft.getMissions().get(3).setRewardClue("해고 통보 문서가 발견되었다.");
        draft.getMissions().get(3).setTargetKeywordType("WEAPON");
        draft.setFinalTruthSummary("정답 설명이 부족합니다.");

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyDeterministicCrimeMysteryGuardrail",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);
        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertTrue(result.isValid());
        assertEquals(3, draft.getSuspects().size());
        assertEquals(8, draft.getEvidences().size());
        assertTrue(draft.getFinalTruthSummary().contains("범인: 강수진"));
        assertTrue(draft.getFinalTruthSummary().contains("흉기: 독성 캡슐"));
        assertFalse(draft.getMissions().stream()
                .filter(mission -> mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .anyMatch(mission -> mission.getRewardClue().contains("강수진")));
    }

    @Test
    void deterministicGuardrailRepairsRecentGeminiFailurePattern() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFinalTruthSummary("강수진은 해고 통보에 원한을 품고 수면제 캡슐을 바꿔치기했다.");
        List<String> badClues = List.of(
                "박진우의 작업실 책상 서랍에서 발견된 강수진의 메모에는 반드시 대가를 치르게 할 거야라는 내용이 적혀 있었다.",
                "박진우의 법률 자문 서류에는 강수진에 대한 해고 통보 절차가 진행 중이었음이 명시되어 있었다.",
                "박진우의 약통에서 발견된 캡슐 중 하나에서 미량의 독성 물질이 검출되었다.",
                "강수진이 박진우의 복용 약과 유사한 색상의 캡슐을 대량으로 구매한 내역이 확인되었다.",
                "사건 당일, 강수진은 박진우의 집 근처 CCTV에 포착되었으나 알리바이를 증명할 명확한 시간 기록은 없었다.",
                "박진우의 약통 안에는 평소 복용하던 약과 동일한 형태와 색상의 캡슐들이 들어 있었다.",
                "강수진은 해고당하기 전 수개월간 낮은 근무 평가를 받아왔다는 기록이 발견되었다.",
                "강수진이 구매했던 수면제 성분과 사건 현장에서 검출된 독성 물질이 혼합될 수 있음이 확인되었다."
        );
        for (int i = 0; i < badClues.size(); i++) {
            draft.getMissions().get(i + 1).setRewardClue(badClues.get(i));
        }

        AiEpisodeDraftValidationRequest beforeRequest = new AiEpisodeDraftValidationRequest();
        beforeRequest.setDraft(draft);
        AiEpisodeDraftValidationResponse before = service.validateDraft(beforeRequest);
        assertFalse(before.isValid());

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyDeterministicCrimeMysteryGuardrail",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());

        AiEpisodeDraftValidationRequest afterRequest = new AiEpisodeDraftValidationRequest();
        afterRequest.setDraft(draft);
        AiEpisodeDraftValidationResponse after = service.validateDraft(afterRequest);

        assertTrue(after.isValid());
        assertTrue(draft.getFinalTruthSummary().contains("범인: 강수진"));
        assertEquals(List.of("CULPRIT", "CULPRIT", "WEAPON", "WEAPON", "MOTIVE", "MOTIVE", "METHOD", "METHOD"),
                draft.getMissions().stream()
                        .filter(mission -> mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                        .map(AiEpisodeDraftResponse.MissionDraft::getTargetKeywordType)
                        .toList());
        assertFalse(draft.getMissions().stream()
                .filter(mission -> mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .anyMatch(mission -> mission.getRewardClue().contains("강수진")));
    }

    @Test
    void buildDraftResponsePublishesAfterDeterministicGuardrail() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        draft.setFinalTruthSummary("정답 설명이 부족합니다.");
        draft.getMissions().get(1).setRewardClue("강수진이 박진우의 집 근처 CCTV에 포착되었다.");
        draft.getMissions().get(3).setRewardClue("해고 통보 문서가 발견되었다.");
        draft.getMissions().get(3).setTargetKeywordType("WEAPON");

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "buildDraftResponse",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        AiEpisodeDraftResponse response = (AiEpisodeDraftResponse) method.invoke(service, draft, source, new ArrayList<String>());

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_FINAL_TRUTH_SUMMARY"));
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertEquals("범죄 미스터리", response.getDraft().getGenre());
        assertEquals(4, response.getDraft().getFinalAnswerKeywordItems().size());
        assertEquals(10, response.getDraft().getMissions().size());
        assertEquals(3, response.getDraft().getSuspects().size());
        assertEquals(8, response.getDraft().getEvidences().size());
    }

    @Test
    void buildDraftResponsePublishesAfterRepairingAllFinalAnswerValueLeaks() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        List<String> answerValues = finalAnswerValues(draft);
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue("leaked answer value: " + answerValues.get(i % answerValues.size()));
        }

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "buildDraftResponse",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        AiEpisodeDraftResponse response = (AiEpisodeDraftResponse) method.invoke(service, draft, source, new ArrayList<String>());

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_ANSWER_VALUES"));
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertNoInvestigationRewardClueLeaksFinalAnswerValues(response.getDraft());
        assertTrue(response.getDraft().getMissions().stream()
                .anyMatch(mission -> mission.getRewardClue() != null
                        && mission.getRewardClue().contains("화상회의")
                        && mission.getRewardClue().contains("CCTV 공백")));
    }

    @Test
    void buildDraftResponsePreservesValidDistinctGeminiClues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        List<String> distinctClues = List.of(
                "출입 기록에는 사건 시간대에 개인 기록 보관실에 접근한 사람이 한 명만 남아 있었다.",
                "피해자의 책상 서랍 손잡이에서 일정표 관리 권한을 가진 사람의 지문 흔적이 확인되었다.",
                "찻잔 바닥의 잔류 성분에서 일반 음료와 다른 독성 물질이 검출되었다.",
                "현장 쓰레기통에서 독성 물질을 옮기는 데 쓰인 작은 유리 도구 조각이 발견되었다.",
                "사망 직전 작성된 계약 문서에는 특정 직원을 배제하는 조항이 추가되어 있었다.",
                "피해자와 가까운 직원이 최근 손실과 갈등 때문에 강한 불만을 드러냈다는 메시지가 남아 있었다.",
                "피해자가 매일 확인하던 서류 봉투는 사건 당일 같은 시간에 교체된 흔적이 있었다.",
                "봉투 교체 시간과 잠금장치 해제 시간이 같은 업무 경로 위에서 이어진다."
        );
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue(distinctClues.get(i));
        }

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "buildDraftResponse",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        AiEpisodeDraftResponse response = (AiEpisodeDraftResponse) method.invoke(service, draft, source, new ArrayList<String>());

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().isEmpty());
        assertEquals(distinctClues, investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .toList());
    }

    @Test
    void buildDraftResponseRedactsSuspectNamesWithoutReplacingValidClues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getFinalAnswers().setCulprit("Alice");
        source.getFinalAnswerKeywordItems().get(0).setKeyword("Alice");
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        draft.setSuspects(List.of(
                suspect("Alice", "event log confirms partial alibi", "office access remains suspicious"),
                suspect("Bob", "call log confirms partial alibi", "financial benefit remains suspicious"),
                suspect("Carol", "CCTV confirms partial alibi", "missing time remains suspicious")
        ));
        applyApprovedContract(draft, source);
        List<String> clues = List.of(
                "Alice CCTV 기록에는 사건 직전 서재 접근 동선이 남아 있었다.",
                "Bob CCTV 기록은 사건 시간 동안 외부 이동이 유지되었음을 보여준다.",
                "약통 안 캡슐 일부에서 일반 수면제와 다른 독성 물질이 검출되었다.",
                "독성 물질은 음식이나 음료가 아니라 캡슐 내부 흔적에서만 확인되었다.",
                "Alice에게 보낸 해고 통보 메일과 강한 불만을 드러낸 답장이 발견되었다.",
                "Bob은 피해자 사망 시 재정적 이득을 얻을 수 있었다는 계약 기록이 있다.",
                "피해자의 매일 복용 시간과 약통 접근 시간이 같은 동선에 묶인다.",
                "약병 보관 서랍의 열림 기록과 캡슐 교체 시간이 겹친다."
        );
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue(clues.get(i));
        }

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "buildDraftResponse",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        AiEpisodeDraftResponse response = (AiEpisodeDraftResponse) method.invoke(service, draft, source, new ArrayList<String>());

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES"));
        assertFalse(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        List<String> redactedClues = investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .toList();
        assertTrue(redactedClues.stream().noneMatch(clue -> clue.contains("Alice") || clue.contains("Bob") || clue.contains("Carol")));
        assertTrue(redactedClues.stream().anyMatch(clue -> clue.contains("첫 번째 용의자")));
        assertTrue(redactedClues.stream().anyMatch(clue -> clue.contains("두 번째 용의자")));
    }

    @Test
    void extractsDraftFromWrappedGeminiJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree("""
                {
                  "draft": {
                    "episodeTitle": "정동의 봉인된 기록",
                    "genre": "범죄 미스터리"
                  }
                }
                """);
        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod("draftJsonNode", JsonNode.class);
        method.setAccessible(true);

        JsonNode draftNode = (JsonNode) method.invoke(service, root);

        assertEquals("정동의 봉인된 기록", draftNode.path("episodeTitle").asText());
        assertEquals("범죄 미스터리", draftNode.path("genre").asText());
    }

    private void applyApprovedContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest source) throws Exception {
        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyApprovedFinalAnswerContract",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());
    }

    @Test
    void draftPromptIncludesExternalResearchContext() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setExternalResearchNotes(List.of("external archive note about opening ceremony records"));
        source.getPlaces().get(9).setReferenceUrls(List.of("https://example.org/archive/final-place"));
        source.getPlaces().get(9).setResearchSourceSummary("external web and document notes");

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod("buildDraftPrompt", AiEpisodeDraftRequest.class);
        method.setAccessible(true);
        String prompt = (String) method.invoke(service, source);

        assertTrue(prompt.contains("external archive note about opening ceremony records"));
        assertTrue(prompt.contains("https://example.org/archive/final-place"));
        assertTrue(prompt.contains("finalPlaceMotif"));
        assertTrue(prompt.contains("approvedFinalAnswers"));
        assertTrue(prompt.contains("CULPRIT: 강수진"));
        assertTrue(prompt.contains("finalTruthSummary must include the approved CULPRIT, WEAPON, MOTIVE, and METHOD values verbatim"));
        assertTrue(prompt.contains("Slot-specific clue rules"));
        assertTrue(prompt.contains("Do not write the culprit name"));
        assertTrue(prompt.contains("Do not describe motive"));
        assertTrue(prompt.contains("Do not create place hints"));
        assertTrue(prompt.contains("Case blueprint"));
        assertTrue(prompt.contains("locked-room crime mystery"));
        assertTrue(prompt.contains("Target story pattern"));
        assertTrue(prompt.contains("only 3 suspects had meaningful access"));
        assertTrue(prompt.contains("daily medication habit"));
        assertTrue(prompt.contains("Suspects must include exactly 3 people"));
        assertTrue(prompt.contains("Evidences must include exactly 8 cards"));
        assertTrue(prompt.contains("uniquely deducible only after combining all 8 clues"));
        assertTrue(prompt.contains("Do not use simple mood, scenery, tourism facts"));
        assertTrue(prompt.contains("Required JSON shape"));
    }

    @Test
    void deterministicGuardrailRemovesAllFinalAnswerValueLeaksFromInvestigationClues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        List<String> answerValues = finalAnswerValues(draft);
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue("leaked answer value: " + answerValues.get(i % answerValues.size()));
        }

        AiEpisodeDraftValidationRequest beforeRequest = new AiEpisodeDraftValidationRequest();
        beforeRequest.setDraft(draft);
        AiEpisodeDraftValidationResponse before = service.validateDraft(beforeRequest);
        assertFalse(before.isValid());

        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyDeterministicCrimeMysteryGuardrail",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());

        AiEpisodeDraftValidationRequest afterRequest = new AiEpisodeDraftValidationRequest();
        afterRequest.setDraft(draft);
        AiEpisodeDraftValidationResponse after = service.validateDraft(afterRequest);

        assertTrue(after.isValid());
        assertNoInvestigationRewardClueLeaksFinalAnswerValues(draft);
    }

    private void assertNoInvestigationRewardClueLeaksFinalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answerValues = finalAnswerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigationMissions(draft)) {
            String clue = mission.getRewardClue() == null ? "" : mission.getRewardClue();
            assertFalse(answerValues.stream().anyMatch(clue::contains), "Leaked final answer value in mission " + mission.getOrder());
        }
    }

    private List<String> finalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft.getFinalAnswerKeywordItems().stream()
                .map(item -> item.getValue() == null || item.getValue().isBlank() ? item.getKeyword() : item.getValue())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<AiEpisodeDraftResponse.MissionDraft> investigationMissions(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft.getMissions().stream()
                .filter(mission -> mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .toList();
    }

    private AiEpisodeDraftRequest sourceInput() {
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setSelectedGenreId("CRIME_MYSTERY");
        request.setSelectedGenreName("범죄 미스터리");
        request.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "강수진"),
                keyword("WEAPON", "흉기", "독성 캡슐"),
                keyword("MOTIVE", "동기", "비밀 계약 은폐"),
                keyword("METHOD", "방법", "약병 바꿔치기")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("강수진");
        answers.setWeapon("독성 캡슐");
        answers.setMotive("비밀 계약 은폐");
        answers.setMethod("약병 바꿔치기");
        request.setFinalAnswers(answers);

        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            AiEpisodeDraftRequest.PlaceInput place = new AiEpisodeDraftRequest.PlaceInput();
            place.setName("테스트 장소 " + (i + 1));
            place.setAddress("서울 테스트로 " + (i + 1));
            place.setLatitude(37.5 + i * 0.001);
            place.setLongitude(126.9 + i * 0.001);
            place.setRole(i == 0 ? "START" : i == 9 ? "FINAL" : "ANSWER_HINT");
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
        return item;
    }

    private AiEpisodeDraftResponse.EpisodeDraft playableDraft(AiEpisodeDraftRequest source) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < source.getPlaces().size(); i++) {
            boolean start = i == 0;
            boolean finalPlace = i == 9;
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(source.getPlaces().get(i).getName())
                    .markerType(start ? "START" : finalPlace ? "FINAL" : "ANSWER_HINT")
                    .publicMarkerType(start ? "START" : "ANSWER_HINT")
                    .clueRole(start ? "START" : finalPlace ? "FINAL_PLACE" : "ANSWER_HINT")
                    .finalPlace(finalPlace)
                    .storyText(start ? "사건 파일을 확인하는 시작 장소입니다." : finalPlace ? "조사 완료 후 자동 공개되는 최종 정답 입력 장소입니다." : "현장 기록과 사건 자료를 비교하는 조사 장소입니다.")
                    .puzzleType("STORY_COMBINATION")
                    .questionText("현장 기록과 사건 파일을 비교해 답을 입력하세요.")
                    .answer("현장단서" + (i + 1))
                    .answerFormat("TEXT")
                    .rewardClue(start || finalPlace ? "사건 진행 정보" : clue(i))
                    .rewardClueSlotId(start || finalPlace ? "" : "ANSWER_CLUE")
                    .targetKeywordType(start || finalPlace ? "" : target(i))
                    .targetKeywordDisplayType(start || finalPlace ? "" : targetLabel(target(i)))
                    .unlockCondition(finalPlace ? "ALL_INVESTIGATION_MISSIONS_CLEARED" : "")
                    .supportsKeywordSlots(start || finalPlace ? List.of() : List.of(target(i)))
                    .hints(List.of("시간 기록을 비교하세요.", "접근 권한을 확인하세요.", "물질 흔적을 대조하세요."))
                    .build());
        }

        return AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("테스트 범죄 미스터리")
                .subtitle("허구 사건 초안")
                .genre("범죄 미스터리")
                .selectedGenre("범죄 미스터리")
                .fictionSynopsis("문화적 배경과 장소의 분위기를 모티브로 구성한 사건입니다.")
                .missionDescription("8개 조사 단서로 네 개 정답 슬롯을 판단합니다.")
                .finalQuestion("범인, 흉기, 동기, 방법을 입력하세요.")
                .finalTruthSummary("강수진이 독성 캡슐을 사용했고, 비밀 계약 은폐를 위해 약병 바꿔치기를 실행했습니다.")
                .actualHistorySummary("장소 정보는 배경 모티브로만 사용했습니다.")
                .missions(missions)
                .suspects(List.of(
                        suspect("강수진", "회의 시간이 비어 있습니다.", "기록 접근 권한이 있습니다."),
                        suspect("박도윤", "통화 기록이 불완전합니다.", "약병 보관함에 접근했습니다."),
                        suspect("이재민", "동선 설명이 엇갈립니다.", "CCTV 공백 시간대가 있습니다.")
                ))
                .evidences(evidences())
                .build();
    }

    private String clue(int index) {
        return List.of(
                "피해자 일정표에 없던 인물이 회의실에 접근했다.",
                "출입 기록의 추가 지문은 자료실 접근 권한자에게서 나왔다.",
                "약병의 봉인 상태가 사건 직전 한 차례 바뀌었다.",
                "독성 물질은 캡슐 내부에서만 검출되었다.",
                "비밀 계약 종료 문서가 사건 당일 삭제되었다.",
                "피해자는 계약 은폐를 공개하려 했다.",
                "약병 보관함 열쇠가 정상 위치에서 사라졌다.",
                "회의실 컵과 약병의 교체 시간이 같은 동선에 묶인다."
        ).get(index - 1);
    }

    private String target(int index) {
        return switch (index) {
            case 1, 2 -> "CULPRIT";
            case 3, 4 -> "WEAPON";
            case 5, 6 -> "MOTIVE";
            default -> "METHOD";
        };
    }

    private String targetLabel(String target) {
        return switch (target) {
            case "CULPRIT" -> "범인";
            case "WEAPON" -> "흉기";
            case "MOTIVE" -> "동기";
            case "METHOD" -> "방법";
            default -> "";
        };
    }

    private AiEpisodeDraftResponse.SuspectDraft suspect(String name, String alibi, String suspicion) {
        return AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(name)
                .alibiSummary(alibi)
                .suspiciousPoint(suspicion)
                .build();
    }

    private List<AiEpisodeDraftResponse.EvidenceDraft> evidences() {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = new ArrayList<>();
        for (int order = 2; order <= 9; order++) {
            evidences.add(AiEpisodeDraftResponse.EvidenceDraft.builder()
                    .title(order + "번 조사 증거")
                    .type("STORY_CLUE")
                    .textSummary(clue(order - 1))
                    .sourceMissionOrder(order)
                    .build());
        }
        return evidences;
    }
}
