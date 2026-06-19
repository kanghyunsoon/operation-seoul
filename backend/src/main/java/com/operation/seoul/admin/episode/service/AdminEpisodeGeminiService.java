package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminEpisodeGeminiService {
    private static final String GENRE_ID = "CRIME_MYSTERY";
    private static final String GENRE_NAME = "\ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final List<String> SLOT_IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    private static final Map<String, String> SLOT_LABELS = Map.of(
            "CULPRIT", "\ubc94\uc778",
            "WEAPON", "\ud749\uae30",
            "MOTIVE", "\ub3d9\uae30",
            "METHOD", "\ubc29\ubc95"
    );
    private static final List<String> DEFAULT_ANSWERS = List.of(
            "\uac15\uc218\uc9c4",
            "\ub3c5\uc131 \ucea1\uc290",
            "\ube44\ubc00 \uacc4\uc57d \uc740\ud3d0",
            "\uc57d\ubcd1 \ubc14\uafd4\uce58\uae30"
    );

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(requestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(145_000);
        return factory;
    }

    public AiEpisodePlanResponse createAnswerPlan(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        ensureApiKey();
        JsonNode root = parseJson(callGemini(buildPlanPrompt()), "GEMINI_PLAN_PARSE_FAILED");
        List<AiEpisodePlanResponse.AnswerKeyword> keywords = sanitizePlanKeywords(root.path("finalAnswerKeywords"));
        return AiEpisodePlanResponse.builder()
                .selectedGenreId(GENRE_ID)
                .selectedGenreName(GENRE_NAME)
                .answerSlots(answerSlotPlans())
                .finalAnswerKeywords(keywords)
                .finalAnswerKeywordItems(keywords)
                .finalAnswers(planFinalAnswers(keywords))
                .finalQuestionGuide("\uc870\uc0ac \ubbf8\uc158 8\uac1c\ub97c \uc644\ub8cc\ud55c \ub4a4 \ubc94\uc778, \ud749\uae30, \ub3d9\uae30, \ubc29\ubc95\uc744 \uac01\uac01 \uc785\ub825\ud569\ub2c8\ub2e4.")
                .rationale("\uc7a5\ub974\ub294 \ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac\ub85c \uace0\uc815\ud558\uace0 TourAPI \uc7a5\uc18c\ub294 \ubc30\uacbd \ubaa8\ud2f0\ube0c\ub85c\ub9cc \uc0ac\uc6a9\ud569\ub2c8\ub2e4.")
                .planReviewRequired(false)
                .reviewReason("")
                .fieldVerificationRecommended(true)
                .rejectedGenreReasons(List.of("\uc7a5\uc18c \ud78c\ud2b8\ub098 \ucd5c\uc885 \uc7a5\uc18c \ucd94\ub9ac \uad6c\uc870\ub294 \uc0ac\uc6a9\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."))
                .validationWarnings(List.of())
                .nextSteps(List.of("4\uac1c \uc815\ub2f5 \uc2ac\ub86f\uc744 \uac80\uc218\ud558\uace0 AI \ucd08\uc548\uc744 \uc0dd\uc131\ud558\uc138\uc694."))
                .build();
    }

    public AiEpisodeDraftResponse createGeminiDraft(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        normalizeFinalAnswerKeywordItems(request);
        validateFinalAnswerContract(request);
        ensureApiKey();
        AiEpisodeDraftResponse.EpisodeDraft draft;
        try {
            JsonNode root = parseJson(callGemini(buildDraftPrompt()), "GEMINI_DRAFT_PARSE_FAILED");
            draft = objectMapper.treeToValue(root, AiEpisodeDraftResponse.EpisodeDraft.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini JSON parse failed.");
        }
        List<String> warnings = new ArrayList<>();
        applyApprovedFinalAnswerContract(draft, request, warnings);
        normalizeDraft(draft, request);
        AiEpisodeDraftValidationRequest validationRequest = new AiEpisodeDraftValidationRequest();
        validationRequest.setDraft(draft);
        validationRequest.setSourceInput(request);
        AiEpisodeDraftValidationResponse validation = validateDraft(validationRequest);
        return AiEpisodeDraftResponse.builder()
                .generatorType("GEMINI_CRIME_MYSTERY_RAG")
                .message("TourAPI \uc7a5\uc18c \uc815\ubcf4\ub97c \ubc30\uacbd \ubaa8\ud2f0\ube0c\ub85c \uc0ac\uc6a9\ud55c \ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac \ucd08\uc548\uc785\ub2c8\ub2e4.")
                .publishable(validation.isValid())
                .draft(draft)
                .validationWarnings(mergeWarnings(warnings, validation))
                .nextSteps(List.of("8\uac1c \uc870\uc0ac \ub2e8\uc11c\uc758 \uc911\ubcf5\uacfc \uc815\ub2f5 \ub178\ucd9c \uc5ec\ubd80\ub97c \uac80\uc218\ud558\uc138\uc694."))
                .build();
    }

    public AiEpisodeDraftValidationResponse validateDraft(AiEpisodeDraftValidationRequest request) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        AiEpisodeDraftResponse.EpisodeDraft draft = request == null ? null : request.getDraft();
        if (draft == null) {
            addFinding(findings, "ERROR", "DRAFT_REQUIRED", "\uac80\uc99d\ud560 \ucd08\uc548\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", null, "draft");
            return validationResponse(findings);
        }
        if (!GENRE_NAME.equals(draft.getGenre()) || !GENRE_NAME.equals(draft.getSelectedGenre())) {
            addFinding(findings, "ERROR", "GENRE_MUST_BE_CRIME_MYSTERY", "\uc7a5\ub974\ub294 \ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac\ub85c \uace0\uc815\ub418\uc5b4\uc57c \ud569\ub2c8\ub2e4.", null, "genre");
        }
        validateFinalAnswers(draft, findings);
        validateMissions(draft, findings);
        validateSuspects(draft, findings);
        validatePlaceSafety(draft, findings);
        findings.addAll(AiDraftTextQualityValidator.findings(draft));
        return validationResponse(findings);
    }

    private void applyApprovedFinalAnswerContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        normalizeFinalAnswerKeywordItems(request);
        Map<String, String> approved = approvedAnswers(request);
        List<String> values = SLOT_IDS.stream().map(approved::get).toList();
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerKeywords(values);
        draft.setFinalAnswerKeywordItems(SLOT_IDS.stream()
                .map(slot -> AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                        .slotId(slot).type(slot).displayType(SLOT_LABELS.get(slot)).label(SLOT_LABELS.get(slot))
                        .keyword(approved.get(slot)).value(approved.get(slot))
                        .personName("CULPRIT".equals(slot) ? approved.get(slot) : "")
                        .aliases(List.of()).build())
                .toList());
        draft.setFinalAnswers(AiEpisodeDraftResponse.FinalAnswers.builder()
                .culprit(approved.get("CULPRIT")).weapon(approved.get("WEAPON"))
                .motive(approved.get("MOTIVE")).method(approved.get("METHOD"))
                .relatedPerson(approved.get("CULPRIT")).coreClue(approved.get("WEAPON"))
                .finalLocation(approved.get("METHOD")).build());
        draft.setFinalAnswer(String.format("%s: %s / %s: %s / %s: %s / %s: %s",
                SLOT_LABELS.get("CULPRIT"), approved.get("CULPRIT"),
                SLOT_LABELS.get("WEAPON"), approved.get("WEAPON"),
                SLOT_LABELS.get("MOTIVE"), approved.get("MOTIVE"),
                SLOT_LABELS.get("METHOD"), approved.get("METHOD")));
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setFinalQuestion(defaultIfBlank(draft.getFinalQuestion(), "\ubc94\uc778, \ud749\uae30, \ub3d9\uae30, \ubc29\ubc95\uc744 \uac01\uac01 \uc785\ub825\ud558\uc138\uc694."));
    }

    private void normalizeDraft(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : draft.getMaxDeductionQuestions());
        draft.setActualHistorySummary(defaultIfBlank(draft.getActualHistorySummary(), "TourAPI \uc7a5\uc18c \uc815\ubcf4\ub294 \ubc30\uacbd \ubaa8\ud2f0\ube0c\ub85c\ub9cc \uc0ac\uc6a9\ub418\uba70 \uc0ac\uac74\uacfc \uc778\ubb3c\uc740 \ubaa8\ub450 \ud5c8\uad6c\uc785\ub2c8\ub2e4."));
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
            mission.setQuestionText(defaultIfBlank(mission.getQuestionText(), "\ud604\uc7a5 \uae30\ub85d\uacfc \uc0ac\uac74 \uc790\ub8cc\ub97c \ube44\uad50\ud574 \ub2f5\ud558\uc138\uc694."));
            mission.setAnswer(defaultIfBlank(mission.getAnswer(), "\ub2e8\uc11c" + (i + 1)));
            mission.setAnswerFormat(defaultIfBlank(mission.getAnswerFormat(), "TEXT"));
            mission.setHints(ensureThreeHints(mission.getHints()));
            if (!start && !finalPlace) {
                String target = defaultTargetKeywordType(i - 1);
                mission.setTargetKeywordType(target);
                mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
                mission.setRewardClueSlotId("ANSWER_CLUE");
                mission.setRewardClueLabel(SLOT_LABELS.get(target) + " \ub2e8\uc11c");
                mission.setSupportsKeywordSlots(List.of(target));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), fallbackRewardClue(i + 1, target)));
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "\uc870\uc0ac \ubbf8\uc158 8\uac1c \uc644\ub8cc \uc2dc \uc790\ub3d9 \uacf5\uac1c"));
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private void validateFinalAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers == null || blank(answers.getCulprit()) || blank(answers.getWeapon()) || blank(answers.getMotive()) || blank(answers.getMethod())) {
            addFinding(findings, "ERROR", "FOUR_FINAL_ANSWERS_REQUIRED", "\ucd5c\uc885 \uc815\ub2f5\uc740 \ubc94\uc778, \ud749\uae30, \ub3d9\uae30, \ubc29\ubc95 4\uac1c\uc785\ub2c8\ub2e4.", null, "finalAnswers");
        }
        if (draft.getFinalAnswerKeywords() == null || draft.getFinalAnswerKeywords().size() != 4 || draft.getFinalAnswerKeywords().stream().anyMatch(this::blank)) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORDS_REQUIRED", "\ucd5c\uc885 \uc815\ub2f5 \ud0a4\uc6cc\ub4dc\ub294 \uc815\ud655\ud788 4\uac1c\uc5ec\uc57c \ud569\ub2c8\ub2e4.", null, "finalAnswerKeywords");
        }
    }

    private void validateMissions(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = safeList(draft.getMissions());
        if (missions.size() != 10) {
            addFinding(findings, "ERROR", "TEN_PLACES_REQUIRED", "START 1, investigation 8, FINAL 1 missions are required.", null, "missions");
            return;
        }
        List<AiEpisodeDraftResponse.MissionDraft> investigation = missions.stream()
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (missions.stream().filter(mission -> "START".equals(normalize(mission.getMarkerType()))).count() != 1) addFinding(findings, "ERROR", "ONE_START_REQUIRED", "START mission must be exactly one.", null, "missions");
        if (missions.stream().filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))).count() != 1) addFinding(findings, "ERROR", "ONE_FINAL_REQUIRED", "FINAL mission must be exactly one.", null, "missions");
        if (investigation.size() != 8) addFinding(findings, "ERROR", "EIGHT_INVESTIGATION_CLUES_REQUIRED", "Investigation missions must be exactly eight.", null, "missions");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(clue) || clue.length() < 10) addFinding(findings, "ERROR", "DEDUCTIVE_CLUE_REQUIRED", "Investigation rewardClue is required.", mission.getOrder(), "rewardClue");
            if (!blank(clue) && !clues.add(compact(clue))) addFinding(findings, "ERROR", "DUPLICATE_CLUE", "Investigation clues must be unique.", mission.getOrder(), "rewardClue");
            if (answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) addFinding(findings, "ERROR", "DIRECT_ANSWER_LEAK", "rewardClue must not include final answer values.", mission.getOrder(), "rewardClue");
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) addFinding(findings, "ERROR", "TARGET_KEYWORD_TYPE_REQUIRED", "targetKeywordType is required.", mission.getOrder(), "targetKeywordType");
            else counts.computeIfPresent(target, (key, count) -> count + 1);
            if (containsForbiddenPlaceHint(mission)) addFinding(findings, "ERROR", "DESTINATION_HINT_FORBIDDEN", "Place hint structure is forbidden.", mission.getOrder(), "markerType");
        }
        for (String slot : SLOT_IDS) {
            int count = counts.getOrDefault(slot, 0);
            if (count != 2) addFinding(findings, "ERROR", "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED", slot + " must be supported by exactly 2 investigation missions. current=" + count, null, "missions.targetKeywordType");
        }
    }

    private void validateSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() < 3) addFinding(findings, "ERROR", "THREE_SUSPECTS_REQUIRED", "At least three suspects are required.", null, "suspects");
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                addFinding(findings, "ERROR", "SUSPECT_DETAILS_REQUIRED", "Suspect details are required.", null, "suspects[" + i + "]");
            }
        }
    }

    private void validatePlaceSafety(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String text = String.join(" ", trim(draft.getFictionSynopsis()), trim(draft.getMissionDescription()), safeList(draft.getMissions()).stream().map(AiEpisodeDraftResponse.MissionDraft::getStoryText).map(this::trim).collect(Collectors.joining(" ")));
        if (containsAny(text, "\uc2e4\uc81c\ub85c \ubc1c\uc0dd\ud55c \uc0b4\uc778", "\uc2e4\uc81c \ubc94\uc8c4 \ud604\uc7a5", "\uc774 \uc7a5\uc18c\uc5d0\uc11c \uc0b4\ud574", "\uc774\uacf3\uc5d0\uc11c \uc2e4\uc81c\ub85c")) {
            addFinding(findings, "ERROR", "REAL_PLACE_CRIME_IMPLICATION", "\uc2e4\uc81c \uc7a5\uc18c\uc5d0\uc11c \uc2e4\uc81c \ubc94\uc8c4\uac00 \ubc1c\uc0dd\ud55c \uac83\ucc98\ub7fc \uc4f0\uba74 \uc548 \ub429\ub2c8\ub2e4.", null, "fictionSynopsis");
        }
    }

    private AiEpisodeDraftValidationResponse validationResponse(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        long errors = findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).count();
        long warns = findings.stream().filter(finding -> "WARN".equals(finding.getSeverity())).count();
        return AiEpisodeDraftValidationResponse.builder()
                .valid(errors == 0)
                .riskScore((int) Math.min(100, errors * 20 + warns * 5))
                .summary(errors == 0 ? "\ud544\uc218 \ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac \uac80\uc99d\uc744 \ud1b5\uacfc\ud588\uc2b5\ub2c8\ub2e4." : errors + " required fixes remain.")
                .findings(findings)
                .requiredFixes(findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).distinct().toList())
                .publishChecklist(List.of("TourAPI places are background motifs only.", "Each answer slot has exactly two investigation clues.", "No place hint or mojibake text is present."))
                .build();
    }

    private String buildPlanPrompt() {
        return "Return JSON only. Genre is fixed to CRIME_MYSTERY. Final answers are exactly CULPRIT, WEAPON, MOTIVE, METHOD. Do not create place hints or destination clues.";
    }

    private String buildDraftPrompt() {
        return "Return JSON only for a Korean crime mystery. Genre is fixed. Final answers are culprit, weapon, motive, method. Do not create place hints. Final place is not a deduction target and unlocks after 8 investigation missions. TourAPI places are background motifs only. All crime and people are fictional. rewardClue must not include final answer values. Each investigation mission narrows exactly one targetKeywordType.";
    }

    private String callGemini(String prompt) {
        String url = API_BASE_URL + "/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            JsonNode root = objectMapper.readTree(restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class));
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini request failed. status=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini request failed. cause=" + e.getClass().getSimpleName());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_RESPONSE_PARSE_FAILED", "Gemini response parse failed.");
        }
    }

    private JsonNode parseJson(String value, String code) {
        try {
            return objectMapper.readTree(stripJsonFence(value));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, code, "Gemini JSON parse failed.");
        }
    }

    private String stripJsonFence(String value) {
        String text = trim(value);
        if (text.startsWith("```")) text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        return text;
    }

    private void ensureApiKey() {
        if (blank(geminiApiKey)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING", "gemini.api.key is required.");
    }

    private void validatePlaces(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().size() != 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_COUNT", "AI draft requires exactly 10 places.");
    }

    private void normalizeFinalAnswerKeywordItems(AiEpisodeDraftRequest request) {
        if (request == null || (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty())) return;
        List<AiEpisodeDraftRequest.AnswerKeywordInput> items = new ArrayList<>();
        for (int i = 0; i < SLOT_IDS.size(); i++) {
            AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
            item.setSlotId(SLOT_IDS.get(i));
            item.setType(SLOT_IDS.get(i));
            item.setLabel(SLOT_LABELS.get(SLOT_IDS.get(i)));
            item.setDisplayType(SLOT_LABELS.get(SLOT_IDS.get(i)));
            item.setKeyword(DEFAULT_ANSWERS.get(i));
            items.add(item);
        }
        request.setFinalAnswerKeywordItems(items);
    }

    private void validateFinalAnswerContract(AiEpisodeDraftRequest request) {
        Map<String, String> values = approvedAnswers(request);
        if (SLOT_IDS.stream().anyMatch(slot -> blank(values.get(slot)))) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINAL_ANSWER_KEYWORDS", "Final answer keyword items must include CULPRIT, WEAPON, MOTIVE, METHOD.");
    }

    private Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < SLOT_IDS.size(); i++) result.put(SLOT_IDS.get(i), DEFAULT_ANSWERS.get(i));
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
                if (SLOT_IDS.contains(slot)) result.put(slot, defaultIfBlank(answerKeywordValue(item), result.get(slot)));
            }
        }
        if (request != null && request.getFinalAnswers() != null) {
            putIfNotBlank(result, "CULPRIT", request.getFinalAnswers().getCulprit());
            putIfNotBlank(result, "WEAPON", request.getFinalAnswers().getWeapon());
            putIfNotBlank(result, "MOTIVE", request.getFinalAnswers().getMotive());
            putIfNotBlank(result, "METHOD", request.getFinalAnswers().getMethod());
        }
        return result;
    }

    private String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        return "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getSourceText());
    }

    private void putIfNotBlank(Map<String, String> values, String key, String value) {
        if (!blank(value)) values.put(key, value.trim());
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlotPlans() {
        return SLOT_IDS.stream().map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder().slotId(slot).label(SLOT_LABELS.get(slot)).description(SLOT_LABELS.get(slot) + " \uc815\ub2f5 \uc2ac\ub86f").minClueCount(2).build()).toList();
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> sanitizePlanKeywords(JsonNode node) {
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        for (int i = 0; i < SLOT_IDS.size(); i++) {
            String slot = SLOT_IDS.get(i);
            String value = node != null && node.isArray() && node.size() > i ? defaultIfBlank(node.get(i).path("keyword").asText(""), DEFAULT_ANSWERS.get(i)) : DEFAULT_ANSWERS.get(i);
            result.add(AiEpisodePlanResponse.AnswerKeyword.builder().slotId(slot).type(slot).label(SLOT_LABELS.get(slot)).displayType(SLOT_LABELS.get(slot)).keyword(value).aliases(List.of()).build());
        }
        return result;
    }

    private AiEpisodePlanResponse.FinalAnswers planFinalAnswers(List<AiEpisodePlanResponse.AnswerKeyword> keywords) {
        Map<String, String> values = new LinkedHashMap<>();
        for (AiEpisodePlanResponse.AnswerKeyword keyword : keywords) values.put(normalize(keyword.getSlotId()), keyword.getKeyword());
        return AiEpisodePlanResponse.FinalAnswers.builder().culprit(values.get("CULPRIT")).weapon(values.get("WEAPON")).motive(values.get("MOTIVE")).method(values.get("METHOD")).build();
    }

    private List<String> answerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers != null) return List.of(trim(answers.getCulprit()), trim(answers.getWeapon()), trim(answers.getMotive()), trim(answers.getMethod()));
        return draft.getFinalAnswerKeywords() == null ? List.of() : draft.getFinalAnswerKeywords();
    }

    private boolean containsForbiddenPlaceHint(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = String.join(" ", trim(mission.getMarkerType()), trim(mission.getPublicMarkerType()), trim(mission.getClueRole()), trim(mission.getRewardClueSlotId()), trim(mission.getRewardClue()), trim(mission.getQuestionText()), trim(mission.getStoryText()), trim(mission.getPuzzleAnswerSource()));
        return containsAny(text, "DESTINATION_HINT", "DESTINATION_CLUE", "FINAL_DESTINATION", "PLACE_HINT", "\uc7a5\uc18c \ud78c\ud2b8", "\uc7a5\uc18c \uc815\ub2f5", "\ucd5c\uc885 \uc7a5\uc18c\ub97c \ucc3e", "\ucd5c\uc885 \ubaa9\uc801\uc9c0\ub97c \ucc3e");
    }

    private String defaultTargetKeywordType(int investigationIndex) {
        return SLOT_IDS.get(Math.min(3, Math.max(0, investigationIndex / 2)));
    }

    private String fallbackRewardClue(int order, String target) {
        return order + "\ubc88 \uc870\uc0ac \ub2e8\uc11c\ub294 " + SLOT_LABELS.get(target) + " \ud310\ub2e8\uc5d0 \ud544\uc694\ud55c \uadfc\uac70\ub97c \uc81c\uacf5\ud569\ub2c8\ub2e4.";
    }

    private List<String> ensureThreeHints(List<String> hints) {
        List<String> result = new ArrayList<>(safeList(hints).stream().filter(value -> !blank(value)).limit(3).toList());
        while (result.size() < 3) result.add("\uc2dc\uac04, \uc811\uadfc \uad8c\ud55c, \ubb3c\uc9c8 \ud754\uc801\uc744 \ube44\uad50\ud558\uc138\uc694.");
        return result;
    }

    private List<String> mergeWarnings(List<String> warnings, AiEpisodeDraftValidationResponse validation) {
        List<String> result = new ArrayList<>();
        if (warnings != null) result.addAll(warnings);
        if (validation != null && validation.getFindings() != null) validation.getFindings().stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).forEach(result::add);
        return result;
    }

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder, String fieldPath) {
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder().severity(severity).code(code).message(message).missionOrder(missionOrder).fieldPath(fieldPath).autoFixable(false).fixType("REGENERATE").build());
    }

    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String defaultIfBlank(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}
