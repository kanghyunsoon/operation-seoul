package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import com.operation.seoul.global.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void rejectsMissingApprovedFinalAnswersWithoutDefaultInjection() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.setFinalAnswerKeywordItems(List.of());
        source.setFinalAnswers(null);

        FinalAnswerContractSupport.normalizeFinalAnswerKeywordItems(source);

        ApiException thrown = assertThrows(ApiException.class, () -> FinalAnswerContractSupport.validateFinalAnswerContract(source));

        assertEquals("INVALID_FINAL_ANSWER_KEYWORDS", thrown.getCode());
        assertTrue(source.getFinalAnswerKeywordItems() == null || source.getFinalAnswerKeywordItems().isEmpty());
    }

    @Test
    void rejectsWeakApprovedFinalAnswerKeywords() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "박선우(관장)"),
                keyword("WEAPON", "흉기", "붓펜"),
                keyword("MOTIVE", "동기", "위작 전시 의혹 은폐"),
                keyword("METHOD", "방법", "함")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("박선우(관장)");
        answers.setWeapon("붓펜");
        answers.setMotive("위작 전시 의혹 은폐");
        answers.setMethod("함");
        source.setFinalAnswers(answers);

        ApiException thrown = assertThrows(ApiException.class, () -> FinalAnswerContractSupport.validateFinalAnswerContract(source));

        assertEquals("WEAK_FINAL_ANSWER_KEYWORDS", thrown.getCode());
    }

    @Test
    void acceptsConcreteApprovedFinalAnswerKeywords() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "박선우(관장)"),
                keyword("WEAPON", "흉기", "독성 잉크가 든 붓펜"),
                keyword("MOTIVE", "동기", "위작 거래 은폐"),
                keyword("METHOD", "방법", "독성 잉크가 든 붓펜으로 감정 확인 서명란을 오염시킴")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("박선우(관장)");
        answers.setWeapon("독성 잉크가 든 붓펜");
        answers.setMotive("위작 거래 은폐");
        answers.setMethod("독성 잉크가 든 붓펜으로 감정 확인 서명란을 오염시킴");
        source.setFinalAnswers(answers);

        FinalAnswerContractSupport.validateFinalAnswerContract(source);
    }

    @Test
    void rejectsShortMethodKeywordWithoutServerRepair() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "최서윤(큐레이터)"),
                keyword("WEAPON", "흉기", "독성 잉크가 든 붓펜"),
                keyword("MOTIVE", "동기", "위작 전시 은폐"),
                keyword("METHOD", "방법", "만지게 함")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("최서윤(큐레이터)");
        answers.setWeapon("독성 잉크가 든 붓펜");
        answers.setMotive("위작 전시 은폐");
        answers.setMethod("만지게 함");
        source.setFinalAnswers(answers);

        ApiException exception = assertThrows(ApiException.class, () -> FinalAnswerContractSupport.validateFinalAnswerContract(source));

        assertEquals("WEAK_FINAL_ANSWER_KEYWORDS", exception.getCode());
        assertEquals("만지게 함", source.getFinalAnswers().getMethod());
        assertEquals("만지게 함", source.getFinalAnswerKeywordItems().get(3).getKeyword());
    }
    @Test
    void draftValidationRejectsWeakFinalAnswerKeywords() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFinalAnswerKeywordItems(List.of(
                answerKeywordItem("CULPRIT", "여행사 직원"),
                answerKeywordItem("WEAPON", "고산병 약"),
                answerKeywordItem("MOTIVE", "범죄"),
                answerKeywordItem("METHOD", "투여")
        ));

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "CONCRETE_FINAL_KEYWORD_REQUIRED".equals(finding.getCode())));
    }

    @Test
    void planPromptRejectsRoleOnlyAndGenericKeywordExamples() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();

        String prompt = GeminiAnswerPlanPromptBuilder.build(source);

        assertTrue(prompt.contains("Before returning JSON, internally follow this generation process"));
        assertTrue(prompt.contains("Choose one murder mechanism that fits the anchor domain"));
        assertTrue(prompt.contains("Do not default to poisoning, contamination, toxic residue, or skin contact"));
        assertTrue(prompt.contains("method_keyword"));
        assertTrue(prompt.contains("method_sentence"));
        assertTrue(prompt.contains("Never reuse stale sample answers or names"));
        assertFalse(prompt.contains("Bad example:"));
        assertFalse(prompt.contains("Good example:"));
    }

    @Test
    void createAnswerPlanRequiresGeminiApiKey() {
        AiEpisodeDraftRequest source = sourceInput();

        ApiException thrown = assertThrows(ApiException.class, () -> service.createAnswerPlan(source));

        assertEquals("GEMINI_API_KEY_MISSING", thrown.getCode());
    }

    @Test
    void extractsTourApiStoryAnchorsWithoutPlaceNameOrAddress() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setName("Actual Place Name");
        source.getPlaces().get(9).setAddress("Actual Address");
        source.getPlaces().get(9).setResearchSourceSummary("조선 후기 상인 조합의 세금 장부 분쟁 기록이 전해진다");
        source.getPlaces().get(9).setExternalResearchNotes(List.of("폐쇄된 창고의 봉인 문서와 물품 검수 절차가 남아 있다"));

        List<String> anchors = TourApiPlanInputExtractor.extract(source).storyAnchors();

        assertFalse(anchors.isEmpty());
        assertTrue(anchors.get(0).contains("세금 장부 분쟁"));
        assertFalse(String.join(" ", anchors).contains("Actual Place Name"));
        assertFalse(String.join(" ", anchors).contains("Actual Address"));
    }

    @Test
    void extractsTourApiHistoricalContextWithoutPlaceNameOrAddress() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setName("Actual Restaurant Name");
        source.getPlaces().get(9).setAddress("Actual Street Address");
        source.getPlaces().get(9).setDescription("Joseon-era warehouse tax ledger incident");
        source.getPlaces().get(9).setKeywords(List.of("old harbor", "customs record"));
        source.getPlaces().get(9).setVerificationNotes(List.of("verified local history marker"));
        source.getPlaces().get(9).setExternalResearchNotes(List.of("archive note about merchant dispute and sealed account book"));
        source.getPlaces().get(9).setResearchSourceSummary("TourAPI heritage summary");

        String context = TourApiPlanInputExtractor.extract(source).historicalContext();

        assertTrue(context.contains("Joseon-era warehouse tax ledger incident"));
        assertFalse(context.contains("old harbor"));
        assertTrue(context.contains("archive note about merchant dispute and sealed account book"));
        assertTrue(context.contains("TourAPI heritage summary"));
        assertFalse(context.contains("Actual Restaurant Name"));
        assertFalse(context.contains("Actual Street Address"));
        assertFalse(context.contains("verified local history marker"));
    }

    @Test
    void planPromptUsesHistoricalContextWithoutPlaceNameOrAddress() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setName("Actual Restaurant Name");
        source.getPlaces().get(9).setAddress("Actual Street Address");
        source.getPlaces().get(9).setDescription("historic merchant ledger conflict");
        source.getPlaces().get(9).setResearchSourceSummary("merchant tax ledger dispute");

        String prompt = GeminiAnswerPlanPromptBuilder.build(source);

        assertTrue(prompt.contains("Derive the four final answer values from the story anchors"));
        assertTrue(prompt.contains("Story anchors to fictionalize"));
        assertTrue(prompt.contains("merchant tax ledger dispute"));
        assertTrue(prompt.contains("historic merchant ledger conflict"));
        assertFalse(prompt.contains("Actual Restaurant Name"));
        assertFalse(prompt.contains("Actual Street Address"));
    }

    @Test
    void planPromptExcludesKakaoLocalAndSiteVerificationNoise() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setDescription("historic customs archive dispute 주변 확인 후보: Modern Gallery, Coffee Shop.");
        source.getPlaces().get(9).setResearchSourceSummary("TourAPI heritage summary about sealed tax records");
        source.getPlaces().get(9).setAdminMemo("RAG/사이트 보강으로 주변 Kakao Local 신호를 사용했습니다.");
        source.getPlaces().get(9).setKeywords(List.of("문화전시", "카페쉼터", "customs ledger"));
        source.getPlaces().get(9).setVerificationNotes(List.of("현장 확인: 간판과 입구 검수 필요"));
        source.getPlaces().get(9).setSiteVerificationSignals(List.of("Kakao Local 주변 후보는 현장 검수와 동선 확인 전용입니다."));
        source.getPlaces().get(9).setExternalResearchNotes(List.of(
                "Selected place context: name=Modern Gallery / nearby=Coffee Shop",
                "archive note about sealed merchant ledger"
        ));

        String prompt = GeminiAnswerPlanPromptBuilder.build(source);

        assertTrue(prompt.contains("historic customs archive dispute"));
        assertTrue(prompt.contains("TourAPI heritage summary about sealed tax records"));
        assertTrue(prompt.contains("archive note about sealed merchant ledger"));
        assertFalse(prompt.contains("customs ledger"));
        assertFalse(prompt.contains("Modern Gallery"));
        assertFalse(prompt.contains("Coffee Shop"));
        assertFalse(prompt.contains("Kakao Local"));
        assertFalse(prompt.contains("문화전시"));
        assertFalse(prompt.contains("카페쉼터"));
        assertFalse(prompt.contains("현장 확인"));
        assertFalse(prompt.contains("Selected place context"));

        TourApiPlanContext planContext = TourApiPlanInputExtractor.extract(source);
        List<String> included = planContext.includedInputs();
        List<String> excluded = planContext.excludedInputs();

        assertTrue(included.stream().anyMatch(value -> value.contains("TourAPI heritage summary")));
        assertTrue(excluded.stream().anyMatch(value -> value.contains("Kakao Local")));
        assertTrue(excluded.stream().anyMatch(value -> value.contains("현장 확인")));
        assertTrue(excluded.stream().anyMatch(value -> value.contains("siteVerificationSignals")));
    }

    @Test
    void planContextUsesFinalPlaceOnlyAndExcludesMiddleRouteResearch() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(4).setName("Middle Route Place");
        source.getPlaces().get(4).setResearchSourceSummary("middle route university archive");
        source.getPlaces().get(4).setExternalResearchNotes(List.of("middle route alumni record"));
        source.getPlaces().get(9).setName("Final Destination");
        source.getPlaces().get(9).setResearchSourceSummary("final destination palace archive");
        source.getPlaces().get(9).setExternalResearchNotes(List.of("final destination sealed registry"));

        TourApiPlanContext context = TourApiPlanInputExtractor.extract(source);
        String anchors = String.join(" ", context.storyAnchors());
        String included = String.join(" ", context.includedInputs());
        String excluded = String.join(" ", context.excludedInputs());
        String prompt = GeminiAnswerPlanPromptBuilder.build(source);

        assertTrue(anchors.contains("final destination palace archive"));
        assertTrue(anchors.contains("final destination sealed registry"));
        assertFalse(anchors.contains("middle route university archive"));
        assertFalse(included.contains("middle route university archive"));
        assertFalse(prompt.contains("middle route university archive"));
        assertTrue(excluded.contains("middle route university archive"));
        assertTrue(excluded.contains("non-final route point is not a story anchor"));
    }

    @Test
    void planContextPrefersExplicitFinalSpotOverRoutePlaces() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(9).setName("Route Last Place");
        source.getPlaces().get(9).setResearchSourceSummary("route last place archive");

        AiEpisodeDraftRequest.PlaceInput finalSpot = new AiEpisodeDraftRequest.PlaceInput();
        finalSpot.setName("Explicit Final Spot");
        finalSpot.setResearchSourceSummary("explicit final spot archive");
        finalSpot.setExternalResearchNotes(List.of("explicit final spot registry"));
        source.setFinalSpot(finalSpot);

        TourApiPlanContext context = TourApiPlanInputExtractor.extract(source);
        String anchors = String.join(" ", context.storyAnchors());
        String included = String.join(" ", context.includedInputs());
        String excluded = String.join(" ", context.excludedInputs());
        String prompt = GeminiAnswerPlanPromptBuilder.build(source);

        assertTrue(anchors.contains("explicit final spot archive"));
        assertTrue(included.contains("explicit final spot archive"));
        assertTrue(prompt.contains("explicit final spot archive"));
        assertFalse(anchors.contains("route last place archive"));
        assertFalse(included.contains("route last place archive"));
        assertFalse(prompt.contains("route last place archive"));
        assertTrue(excluded.contains("route last place archive"));
        assertTrue(excluded.contains("non-final route point is not a story anchor"));
    }

    @Test
    void sanitizesGeminiPlanCulpritToNameOnly() throws Exception {
        JsonNode node = new ObjectMapper().readTree("""
                [
                  {"slotId":"CULPRIT","keyword":"한지원(큐레이터)"},
                  {"slotId":"WEAPON","keyword":"독성 안료가 묻은 감정용 장갑"},
                  {"slotId":"MOTIVE","keyword":"작품 소유권 분쟁을 숨기기 위한 범행"},
                  {"slotId":"METHOD","keyword":"감정용 장갑 안쪽에 독성 안료를 묻혀 피해자가 작품을 확인하며 접촉하게 함"}
                ]
                """);
        List<AiEpisodePlanResponse.AnswerKeyword> keywords = new GeminiAnswerPlanGenerator(new ObjectMapper(), prompt -> "{}")
                .sanitizePlanKeywords(node);

        assertEquals("한지원", keywords.get(0).getKeyword());
        assertEquals("한지원", keywords.get(0).getPersonName());
    }

    @Test
    void rejectsGeminiPlanWithLiteraryCulpritAndVagueMethod() throws Exception {
        JsonNode node = new ObjectMapper().readTree("""
                [
                  {"slotId":"CULPRIT","keyword":"이몽룡"},
                  {"slotId":"WEAPON","keyword":"오염된 죽염 안약"},
                  {"slotId":"MOTIVE","keyword":"춘향가 위조본 유통 은폐"},
                  {"slotId":"METHOD","keyword":"눈에 몰래 투여하여 혼란을 야기함"}
                ]
                """);
        GeminiAnswerPlanGenerator generator = new GeminiAnswerPlanGenerator(new ObjectMapper(), prompt -> "{}");

        ApiException thrown = assertThrows(ApiException.class, () -> generator.sanitizePlanKeywords(node));

        assertEquals("GEMINI_PLAN_INVALID", thrown.getCode());
    }

    @Test
    void rejectsGeminiPlanWithUnclearImplantAndIngestionMethod() throws Exception {
        JsonNode node = new ObjectMapper().readTree("""
                [
                  {"slotId":"CULPRIT","keyword":"박지성"},
                  {"slotId":"WEAPON","keyword":"강화된 금속 가루가 섞인 붓"},
                  {"slotId":"MOTIVE","keyword":"미공개 고미술품 거래 은폐"},
                  {"slotId":"METHOD","keyword":"필기구에 몰래 이식하여 내용물 섭취 유도"}
                ]
                """);
        GeminiAnswerPlanGenerator generator = new GeminiAnswerPlanGenerator(new ObjectMapper(), prompt -> "{}");

        ApiException thrown = assertThrows(ApiException.class, () -> generator.sanitizePlanKeywords(node));

        assertEquals("GEMINI_PLAN_INVALID", thrown.getCode());
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

        DraftCrimeMysteryGuardrailApplier.apply(draft, source, new ArrayList<>(), (ignoredDraft, ignoredIssues) -> {});

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

        DraftCrimeMysteryGuardrailApplier.apply(draft, source, new ArrayList<>(), (ignoredDraft, ignoredIssues) -> {});

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
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

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
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_ANSWER_VALUES"));
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertNoInvestigationRewardClueLeaksFinalAnswerValues(response.getDraft());
        assertTrue(response.getDraft().getMissions().stream()
                .anyMatch(mission -> mission.getRewardClue() != null
                        && mission.getRewardClue().contains("CCTV")));
    }

    @Test
    void buildDraftResponseRepairsDuplicateSuspectsAndPlaceDrivenSynopsis() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFictionSynopsis("테스트 장소 2에서 사건이 시작되고 테스트 장소 3의 기록을 따라 이동하는 이야기입니다.");
        draft.setSuspects(List.of(
                suspect("강수진", "행사 자료를 정리했다고 주장합니다.", "피해자 업무 공간에 접근할 수 있습니다."),
                suspect("강수진", "회의 기록을 확인했다고 주장합니다.", "피해자와 갈등이 있었습니다."),
                suspect("강수진", "전시 준비를 했다고 주장합니다.", "동선 공백이 있습니다.")
        ));
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_SUSPECTS"));
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_SYNOPSIS_SUSPECTS"));
        assertEquals(3, response.getDraft().getSuspects().stream()
                .map(AiEpisodeDraftResponse.SuspectDraft::getDisplayName)
                .distinct()
                .count());
        assertFalse(response.getDraft().getFictionSynopsis().contains("테스트 장소"));
        assertTrue(response.getDraft().getFictionSynopsis().contains("외부 침입"));
        assertTrue(response.getDraft().getFictionSynopsis().contains("세 명"));
    }

    @Test
    void repairedSynopsisAndMissionStoriesFollowFinalKeywordDomain() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "서민재"),
                keyword("WEAPON", "흉기", "독성 방부제가 묻은 항만 서류 봉투"),
                keyword("MOTIVE", "동기", "밀수 장부 은폐"),
                keyword("METHOD", "방법", "피해자가 매일 확인하던 화물 인수 서류를 독성 봉투로 바꿔치기")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("서민재");
        answers.setWeapon("독성 방부제가 묻은 항만 서류 봉투");
        answers.setMotive("밀수 장부 은폐");
        answers.setMethod("피해자가 매일 확인하던 화물 인수 서류를 독성 봉투로 바꿔치기");
        source.setFinalAnswers(answers);
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFictionSynopsis("장소 기록을 따라 이동하는 짧은 이야기입니다.");
        draft.setSuspects(List.of(
                suspect("서민재", "서류 작업을 했다고 주장하지만 일부 기록만 남아 있습니다.", "피해자의 자료 검토실에 접근할 수 있었습니다."),
                suspect("홍지영", "비즈니스 미팅 기록이 일부 확인됩니다.", "피해자가 공개하려던 기록으로 사업상 손해를 볼 수 있었습니다."),
                suspect("박태준", "잠시 자리를 비웠다가 복귀했다고 진술합니다.", "문서 보관 절차를 가장 잘 알고 있었습니다.")
        ));
        for (AiEpisodeDraftResponse.MissionDraft mission : draft.getMissions()) {
            mission.setStoryText(null);
        }
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getDraft().getFictionSynopsis().contains("항만 물류 감사관"));
        assertTrue(response.getDraft().getFictionSynopsis().contains("자료 검토실"));
        assertTrue(response.getDraft().getFictionSynopsis().contains("밀수 장부 은폐"));
        assertFalse(response.getDraft().getFictionSynopsis().contains("미술품 수집가"));
        assertFalse(response.getDraft().getFictionSynopsis().contains("갤러리"));
        assertTrue(response.getDraft().getMissions().stream().allMatch(mission -> mission.getStoryText() != null && !mission.getStoryText().isBlank()));
    }

    @Test
    void buildDraftResponseRedactsRealPlaceNamesFromStoryTextButKeepsMissionPlaceName() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(0).setName("솔솥 광화문 케이트윈점");
        source.getPlaces().get(1).setName("행복한밥상");
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setFictionSynopsis("솔솥 광화문 케이트윈점에서 사건 흔적이 발견되고 행복한밥상의 기록을 대조하는 이야기입니다.");
        draft.getMissions().get(0).setPlaceName("솔솥 광화문 케이트윈점");
        draft.getMissions().get(0).setStoryText("솔솥 광화문 케이트윈점에서 사건 파일을 확인합니다.");
        draft.getMissions().get(1).setStoryText("행복한밥상에서 발견된 기록을 비교합니다.");
        draft.getMissions().get(1).setRewardClue("행복한밥상 출입 기록에 공백이 있습니다.");
        draft.getEvidences().get(0).setTextSummary("행복한밥상 기록과 연결된 사건 자료입니다.");
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REDACTED_REAL_PLACE_NAMES"));
        assertEquals("솔솥 광화문 케이트윈점", response.getDraft().getMissions().get(0).getPlaceName());
        assertFalse(response.getDraft().getFictionSynopsis().contains("솔솥 광화문 케이트윈점"));
        assertFalse(response.getDraft().getFictionSynopsis().contains("행복한밥상"));
        assertFalse(response.getDraft().getMissions().stream()
                .map(mission -> String.join(" ", textOf(mission.getStoryText()), textOf(mission.getQuestionText()), textOf(mission.getRewardClue())))
                .anyMatch(text -> text.contains("솔솥 광화문 케이트윈점") || text.contains("행복한밥상")));
        assertFalse(response.getDraft().getEvidences().stream()
                .map(evidence -> String.join(" ", textOf(evidence.getTitle()), textOf(evidence.getTextSummary())))
                .anyMatch(text -> text.contains("솔솥 광화문 케이트윈점") || text.contains("행복한밥상")));
    }

    @Test
    void buildDraftResponseRepairsEvidenceCardsThatLeakFinalAnswerValues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getEvidences().get(0).setTextSummary("추가 지문이 강수진의 지문과 일치한다.");
        draft.getEvidences().get(1).setTextSummary("흉기는 독성 캡슐이다.");
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_EVIDENCES"));
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), response.getDraft().getEvidences().stream()
                .map(AiEpisodeDraftResponse.EvidenceDraft::getSourceMissionOrder)
                .toList());
        assertNoEvidenceLeaksFinalAnswerValues(response.getDraft());
    }

    @Test
    void repairedInvestigationCluesFollowApprovedAnswerDomain() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getFinalAnswerKeywordItems().get(0).setKeyword("한지원");
        source.getFinalAnswerKeywordItems().get(0).setPersonName("한지원");
        source.getFinalAnswerKeywordItems().get(1).setKeyword("독성 시약이 섞인 연구실 음료");
        source.getFinalAnswerKeywordItems().get(2).setKeyword("연구 조작 은폐");
        source.getFinalAnswerKeywordItems().get(3).setKeyword("피해자의 매일 시험 전 마시는 음료를 독성 음료로 바꿔치기");
        source.getFinalAnswers().setCulprit("한지원");
        source.getFinalAnswers().setWeapon("독성 시약이 섞인 연구실 음료");
        source.getFinalAnswers().setMotive("연구 조작 은폐");
        source.getFinalAnswers().setMethod("피해자의 매일 시험 전 마시는 음료를 독성 음료로 바꿔치기");
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigationMissions(draft)) {
            mission.setRewardClue("leaked answer value: 한지원");
        }
        draft.setEvidences(List.of());
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        List<String> repairedClues = investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .toList();
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertTrue(repairedClues.stream().allMatch(clue -> clue != null && clue.length() >= 20));
        assertTrue(repairedClues.stream().anyMatch(clue -> clue.contains("CCTV")));
        assertTrue(repairedClues.stream().anyMatch(clue -> clue.contains("문서") || clue.contains("기록")));
        assertTrue(repairedClues.stream().noneMatch(clue -> clue.contains("약통") || clue.contains("수면제") || clue.contains("캡슐")));
        assertNoInvestigationRewardClueLeaksFinalAnswerValues(response.getDraft());
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
                "사건 당사자 간에 주고받은 격앙된 내용의 메시지 기록이 발견되었습니다.",
                "피해자가 매일 확인하던 서류 봉투는 사건 당일 같은 시간에 교체된 흔적이 있었다.",
                "봉투 교체 시간과 잠금장치 해제 시간이 같은 업무 경로 위에서 이어진다."
        );
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue(distinctClues.get(i));
        }
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertFalse(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertEquals(distinctClues, investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .toList());
    }

    @Test
    void buildDraftResponseRejectsCulpritClueThatExcludesAllSuspects() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        investigationMissions(draft).get(0)
                .setRewardClue("현장에서 발견된 지문 중 피해자의 것과 일치하지 않는 하나의 추가 지문이 용의자 세 명의 것과 모두 다르다는 사실이 밝혀졌다.");
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_INVESTIGATION_CLUES_CULPRIT_OUTSIDE_SUSPECTS"));
        assertFalse(investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .anyMatch(clue -> clue != null && clue.contains("용의자 세 명의 것과 모두 다르")));
    }

    @Test
    void buildDraftResponseRedactsSuspectNamesWithoutReplacingValidClues() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getFinalAnswers().setCulprit("한지원");
        source.getFinalAnswerKeywordItems().get(0).setKeyword("한지원");
        source.getFinalAnswerKeywordItems().get(0).setPersonName("한지원");
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        draft.setSuspects(List.of(
                suspect("한지원", "행사 준비 기록이 일부 알리바이를 뒷받침합니다.", "사무실 접근 권한이 남아 있습니다."),
                suspect("오도윤", "통화 기록이 일부 알리바이를 뒷받침합니다.", "재정적 이익 가능성이 남아 있습니다."),
                suspect("서민재", "CCTV가 일부 알리바이를 뒷받침합니다.", "동선 공백이 남아 있습니다.")
        ));
        applyApprovedContract(draft, source);
        List<String> clues = List.of(
                "한지원 CCTV 기록에는 사건 직전 서재 접근 동선이 남아 있었다.",
                "오도윤 CCTV 기록은 사건 시간 동안 외부 이동이 유지되었음을 보여준다.",
                "약통 안 캡슐 일부에서 일반 수면제와 다른 독성 물질이 검출되었다.",
                "독성 물질은 음식이나 음료가 아니라 캡슐 내부 흔적에서만 확인되었다.",
                "한지원에게 보낸 해고 통보 메일과 강한 불만을 드러낸 답장이 발견되었다.",
                "오도윤은 피해자 사망 시 재정적 이득을 얻을 수 있었다는 계약 기록이 있다.",
                "피해자의 매일 복용 시간과 약통 접근 시간이 같은 동선에 묶인다.",
                "약병 보관 서랍의 열림 기록과 캡슐 교체 시간이 겹친다."
        );
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        for (int i = 0; i < investigation.size(); i++) {
            investigation.get(i).setRewardClue(clues.get(i));
        }
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES"));
        assertFalse(response.getValidationWarnings().contains("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES"));
        List<String> redactedClues = investigationMissions(response.getDraft()).stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getRewardClue)
                .toList();
        assertTrue(redactedClues.stream().noneMatch(clue -> clue.contains("한지원") || clue.contains("오도윤") || clue.contains("서민재")));
        assertTrue(redactedClues.stream().noneMatch(clue -> clue.contains("첫 번째 용의자") || clue.contains("두 번째 용의자") || clue.contains("세 번째 용의자")));
        assertTrue(redactedClues.stream().anyMatch(clue -> clue.contains("기록 속 인물")));
        assertFalse(redactedClues.stream().anyMatch(clue -> clue.contains("문서에 언급된 인물")));
        assertTrue(redactedClues.stream().anyMatch(clue -> clue.contains("이해관계가 드러난 인물") || clue.contains("해당 당사자") || clue.contains("문서상 이해관계자")));
    }

    @Test
    void buildDraftResponseRewritesGenericSpecificSuspectReferences() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        investigationMissions(draft).get(0)
                .setRewardClue("CCTV 기록상 특정 용의자의 출입 기록 일부가 누락되었다.");
        draft.getEvidences().get(0)
                .setTextSummary("사건 당일 특정 용의자가 사무실에 있었던 기록이 남아 있다.");
        AiEpisodeDraftResponse response = buildDraftResponse(draft, source);

        assertTrue(response.getPublishable());
        assertTrue(response.getValidationWarnings().contains("GUARDRAIL_REWROTE_GENERIC_SUSPECT_REFERENCES"));
        assertFalse(response.getDraft().getMissions().stream()
                .anyMatch(mission -> mission.getRewardClue() != null && mission.getRewardClue().contains("특정 용의자")));
        assertFalse(response.getDraft().getEvidences().stream()
                .anyMatch(evidence -> evidence.getTextSummary() != null && evidence.getTextSummary().contains("특정 용의자")));
        assertTrue(response.getDraft().getMissions().stream()
                .anyMatch(mission -> mission.getRewardClue() != null && mission.getRewardClue().contains("기록 속 인물")));
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
        AiEpisodeDraftResponse.EpisodeDraft draft = new GeminiDraftGenerator(mapper, prompt -> root.toString())
                .generate(sourceInput());

        assertEquals("정동의 봉인된 기록", draft.getEpisodeTitle());
        assertEquals("범죄 미스터리", draft.getGenre());
    }

    private void applyApprovedContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest source) {
        DraftFinalAnswerContractApplier.apply(draft, source);
    }

    @Test
    void draftPromptOmitsRealPlaceContextFromStoryGeneration() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        source.getPlaces().get(0).setName("솔솥 광화문 케이트윈점");
        source.getPlaces().get(0).setAddress("서울 종로구 종로1길 50");
        source.getPlaces().get(1).setName("행복한밥상");
        source.getPlaces().get(1).setAddress("서울 중구 세종대로 1");
        source.getPlaces().get(9).setExternalResearchNotes(List.of("external archive note about opening ceremony records"));
        source.getPlaces().get(9).setReferenceUrls(List.of("https://example.org/archive/final-place"));
        source.getPlaces().get(9).setResearchSourceSummary("external web and document notes");

        String prompt = GeminiDraftPromptBuilder.build(source);

        assertTrue(prompt.contains("external archive note about opening ceremony records"));
        assertFalse(prompt.contains("https://example.org/archive/final-place"));
        assertFalse(prompt.contains("finalPlaceMotif"));
        assertFalse(prompt.contains("routePlaces"));
        assertFalse(prompt.contains("솔솥 광화문 케이트윈점"));
        assertFalse(prompt.contains("행복한밥상"));
        assertFalse(prompt.contains("서울 종로구 종로1길 50"));
        assertTrue(prompt.contains("storyAnchors"));
        assertTrue(prompt.contains("장소는 나중에 미션에 배정될 지도 좌표일 뿐이다."));
        assertTrue(prompt.contains("actualHistorySummary는 허구 사건 해설이 아니다."));
        assertTrue(prompt.contains("approvedFinalAnswers"));
        assertTrue(prompt.contains("CULPRIT: 강수진"));
        assertTrue(prompt.contains("finalTruthSummary에는 승인된 CULPRIT, WEAPON, MOTIVE, METHOD 값을 그대로 모두 포함한다."));
        assertTrue(prompt.contains("미션 슬롯"));
        assertTrue(prompt.contains("rewardClue에 정답 값을 그대로 쓰지 않는다."));
        assertTrue(prompt.contains("최종 장소를 찾아라"));
        assertTrue(prompt.contains("크라임씬 사건 작가"));
        assertTrue(prompt.contains("피해자 신원, 시신/사건 발견 상황, 직접적인 사망 방식"));
        assertTrue(prompt.contains("승인된 METHOD가 독살/오염/접촉이 아니라면"));
        assertTrue(prompt.contains("실제 역사 사건을 살인 사건처럼 꾸미지 않는다"));
        assertTrue(prompt.contains("suspects는 정확히 3명"));
        assertTrue(prompt.contains("evidences는 8개"));
        assertTrue(prompt.contains("sourceMissionOrder 2~9"));
        assertTrue(prompt.contains("조작 순서/접근 경로/실행 가능성"));
        assertFalse(prompt.contains("daily medication habit"));
        assertFalse(prompt.contains("capsule, medication"));
        assertFalse(prompt.contains("Never reuse stale sample answers or names"));
        assertTrue(prompt.contains("용의자 3명, 각자의 이해관계"));
        assertTrue(prompt.contains("evidences는 8개"));
        assertTrue(prompt.contains("지도 동선은 사건 줄거리와 단서에 사용하지 않는다"));
        assertTrue(prompt.contains("기록, 지문, 출입 로그, CCTV 공백"));
        assertTrue(prompt.contains("반환 JSON 필수 필드"));
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

        DraftCrimeMysteryGuardrailApplier.apply(draft, source, new ArrayList<>(), (ignoredDraft, ignoredIssues) -> {});

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

    private void assertNoEvidenceLeaksFinalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answerValues = finalAnswerValues(draft);
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : draft.getEvidences()) {
            String text = String.join(" ",
                    evidence.getTitle() == null ? "" : evidence.getTitle(),
                    evidence.getTextSummary() == null ? "" : evidence.getTextSummary());
            assertFalse(answerValues.stream().anyMatch(text::contains), "Leaked final answer value in evidence " + evidence.getSourceMissionOrder());
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

    private AiEpisodeDraftResponse buildDraftResponse(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest source) {
        return DraftResponseAssembler.build(draft, source, new ArrayList<>(), service::validateDraft, (ignoredDraft, ignoredIssues) -> {});
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

    private AiEpisodeDraftResponse.AnswerKeywordItem answerKeywordItem(String slotId, String value) {
        return AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                .slotId(slotId)
                .type(slotId)
                .label(targetLabel(slotId))
                .displayType(targetLabel(slotId))
                .keyword(value)
                .value(value)
                .personName("CULPRIT".equals(slotId) ? value : "")
                .build();
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
                .fictionSynopsis("한태준이 행사 전날 잠긴 집무실에서 숨진 채 발견되었다. 사인은 독성 캡슐과 연결된 중독으로 추정되며 외부 침입 흔적은 없었다. 사건 시간대에 의미 있는 접근 권한을 가진 인물은 강수진, 박도윤, 이재민 세 명뿐이었다.")
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

    private String textOf(String value) {
        return value == null ? "" : value;
    }
}

