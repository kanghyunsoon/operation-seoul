package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminEpisodeGeminiService {
    private static final Set<String> FINAL_ANSWER_TYPES = Set.of("CULPRIT", "WEAPON", "EVIDENCE", "HIDDEN_DOCUMENT", "SECRET_KEYWORD", "HIDDEN_TRUTH");
    private static final Set<String> PUZZLE_TYPES = Set.of("OBSERVATION", "NUMBER_LOCK", "INITIAL_SOUND", "PATTERN", "STORY_COMBINATION");
    private static final Set<String> ANSWER_FORMATS = Set.of("TEXT", "NUMBER", "CHOICE", "CODE");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(geminiRequestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    private static SimpleClientHttpRequestFactory geminiRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(170_000);
        return factory;
    }

    public AiEpisodeDraftResponse createGeminiDraft(AiEpisodeDraftRequest request) {
        validateRequest(request);
        ensureApiKey();
        String prompt = buildPrompt(request);
        String json = callGemini(prompt);
        AiEpisodeDraftResponse.EpisodeDraft draft = parseDraft(json);
        List<String> warnings = normalizeAndValidateDraft(draft, request);
        return AiEpisodeDraftResponse.builder()
                .generatorType("GEMINI_STRUCTURED_DRAFT")
                .message("Gemini created a structured case-file draft. Review all field observations before publishing.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of(
                        "Review final answer, aliases, and forbidden reveals.",
                        "Verify every sign, number, object, and facility condition on site.",
                        "Edit puzzles, hints, reward_payload, suspects, and evidences before saving as DRAFT.",
                        "Save as DRAFT only, then publish after manual inspection."
                ))
                .build();
    }

    public AiEpisodeDraftValidationResponse validateDraft(AiEpisodeDraftValidationRequest request) {
        if (request == null || request.getDraft() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFT_VALIDATION_INPUT", "Draft is required.");
        }
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        validateDraftRules(request.getDraft(), request.getSourceInput(), findings);
        if (request.isUseGemini()) {
            ensureApiKey();
            findings.addAll(validateDraftWithGemini(request).stream()
                    .filter(finding -> !suppressGeminiFinding(finding, request.getDraft()))
                    .toList());
        }
        int riskScore = calculateRiskScore(findings);
        List<String> requiredFixes = findings.stream()
                .filter(finding -> "ERROR".equals(finding.getSeverity()))
                .map(AiEpisodeDraftValidationResponse.Finding::getMessage)
                .toList();
        return AiEpisodeDraftValidationResponse.builder()
                .valid(requiredFixes.isEmpty())
                .riskScore(riskScore)
                .summary(requiredFixes.isEmpty()
                        ? "Draft passed required validation checks. Manual site inspection is still required before publishing."
                        : "Draft has blocking issues. Fix required items before saving or publishing.")
                .findings(findings)
                .requiredFixes(requiredFixes)
                .publishChecklist(List.of(
                        "Confirm every place coordinate and arrival radius on site.",
                        "Confirm every visible element, sign, number, and object used in puzzles.",
                        "Confirm final answer is fictional and not a real place, person, or event.",
                        "Confirm map API will expose only publicMarkerType, never internal finalPlace.",
                        "Save generated content as DRAFT first and publish only after admin review."
                ))
                .build();
    }

    private void validateRequest(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_DRAFT_INPUT", "At least 6 places are required for an episode draft.");
        }
        if (request.getPlaces().size() > 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_DRAFT_INPUT", "Up to 9 places can be used for the MVP draft.");
        }
        for (int i = 0; i < request.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            if (blank(place.getName()) || place.getLatitude() == null || place.getLongitude() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_INPUT", "Every selected place must include name, latitude, and longitude.");
            }
        }
    }

    private void ensureApiKey() {
        if (blank(geminiApiKey) || geminiApiKey.startsWith("YOUR_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GEMINI_API_KEY_MISSING", "gemini.api.key가 설정되지 않았습니다. application.yml 또는 환경변수에 실제 Gemini API 키를 설정하세요.");
        }
    }

    private String buildPrompt(AiEpisodeDraftRequest request) {
        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_INPUT_SERIALIZE_FAILED", "Could not serialize AI draft input.");
        }
        return """
                You are a case-file outdoor escape-room content designer for Operation Korea.
                Return JSON only. Do not wrap it in markdown.
                Write all player-facing story, puzzle, suspect, evidence, clue, and hint text in Korean.

                Hard rules:
                - Use only the provided places and provided field data.
                - Do not invent real signs, plaque text, numbers, stairs, sculptures, murals, access rules, or photo-verifiable objects.
                - NUMBER_LOCK puzzles may use only numbers listed in place.numbers.
                - OBSERVATION puzzles may use only visibleElements, keywords, description, and adminMemo.
                - Never create puzzles that ask for the first/second/nth/last letter, syllable, initial consonant, or substring of a place name or business name.
                - Never use the place name or a fragment of the place name as a puzzle answer.
                - Never make rewardClue by extracting letters, syllables, initials, or substrings from a place name or business name.
                - rewardClue must be a fictional clue token or clue word that does not depend on the spelling of any selected place.
                - If provided field data is too weak for a real puzzle, create an admin-review placeholder puzzle with answer "검수필요" instead of inventing a field observation.
                - Do not make a real historical person the culprit.
                - Do not present a real historical event as a distorted fact.
                - The final answer must be a clear noun phrase inside the fictional case, not a place name, real person, real event, verb, sentence, or abstract single word.
                - The real final place is an internal role only. Public marker for that place must be FINAL_CANDIDATE.
                - A final-place mission story must read like a normal investigation candidate. It must not say or imply "this is the final place", "final deduction starts here", or "the answer location".
                - Do not describe the real final place as the final destination in storyText, rewardClue, destination hints, finalTruthSummary, actualHistorySummary, or deductionSecretFacts.
                - At least one non-final destination candidate should also use publicMarkerType FINAL_CANDIDATE so the real final place is hidden among candidates.
                - Create 4 answer clues when enough ANSWER_HINT places exist, and 2 destination clues when enough DESTINATION_HINT places exist.
                - Hints must have 3 levels and must not directly reveal the answer.
                - deductionForbiddenReveals must include the final answer and direct final place reveal.
                - deductionSecretFacts are for server/admin use and must support yes/no style final deduction.

                JSON schema:
                {
                  "episodeTitle": "string",
                  "subtitle": "string",
                  "genre": "string",
                  "era": "string",
                  "fictionSynopsis": "string",
                  "finalAnswerType": "CULPRIT|WEAPON|EVIDENCE|HIDDEN_DOCUMENT|SECRET_KEYWORD|HIDDEN_TRUTH",
                  "finalAnswer": "string",
                  "finalAnswerAliases": ["string"],
                  "finalQuestion": "string",
                  "finalTruthSummary": "string",
                  "actualHistorySummary": "string",
                  "deductionSecretFacts": ["string"],
                  "deductionForbiddenReveals": ["string"],
                  "maxDeductionQuestions": 20,
                  "missions": [
                    {
                      "order": 1,
                      "placeName": "string",
                      "address": "string",
                      "latitude": 0.0,
                      "longitude": 0.0,
                      "markerType": "START|ANSWER_HINT|DESTINATION_HINT|STORY|FINAL_CANDIDATE|FINAL",
                      "publicMarkerType": "START|ANSWER_HINT|DESTINATION_HINT|STORY|FINAL_CANDIDATE",
                      "clueRole": "START|ANSWER_HINT|DESTINATION_HINT|STORY_CONTEXT|FINAL_PLACE",
                      "finalPlace": false,
                      "storyText": "string",
                      "arrivalRadius": 50,
                      "puzzleType": "OBSERVATION|NUMBER_LOCK|INITIAL_SOUND|PATTERN|STORY_COMBINATION",
                      "questionText": "string",
                      "answer": "string",
                      "answerFormat": "TEXT|NUMBER|CHOICE|CODE",
                      "rewardClue": "string",
                      "hints": ["string", "string", "string"],
                      "groundRule": "string explaining which provided field data was used"
                    }
                  ],
                  "suspects": [{"alias":"string","displayName":"string","suspiciousPoint":"string"}],
                  "evidences": [{"title":"string","type":"PHOTO|MEMO|NOTE|DOCUMENT|EVIDENCE|SUSPECT_CLUE|POST_IT|ANSWER_CLUE|DESTINATION_CLUE|STORY_CLUE","imageUrl":"","textSummary":"string","sourceMissionOrder":1}]
                }

                Input:
                """ + inputJson;
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", Map.of(
                "temperature", 0.6,
                "responseMimeType", "application/json"
        ));
        String url = UriComponentsBuilder
                .fromUriString("https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent")
                .queryParam("key", geminiApiKey)
                .build()
                .toUriString();
        try {
            String response = restTemplate.postForObject(url, body, String.class);
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            if (blank(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_EMPTY_RESPONSE", "Gemini returned an empty draft.");
            }
            return extractJson(text);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 호출에 실패했습니다. gemini.api.key와 gemini.model=" + geminiModel + " 설정을 확인하세요. 원인: " + e.getMessage());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_RESPONSE_PARSE_FAILED", "Gemini 응답을 초안 JSON으로 해석할 수 없습니다. 모델이 JSON 스키마를 지켰는지 확인하세요.");
        }
    }

    private AiEpisodeDraftResponse.EpisodeDraft parseDraft(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("draft")) {
                node = node.get("draft");
            }
            return objectMapper.treeToValue(node, AiEpisodeDraftResponse.EpisodeDraft.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_SCHEMA_INVALID", "Gemini draft does not match the required schema.");
        }
    }

    private void validateDraftRules(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings) {
        if (blank(draft.getEpisodeTitle())) {
            addFinding(findings, "ERROR", "MISSING_TITLE", "Episode title is required.", null);
        }
        if (blank(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "MISSING_FINAL_ANSWER", "Final answer is required.", null);
        }
        if (!FINAL_ANSWER_TYPES.contains(normalize(draft.getFinalAnswerType()))) {
            addFinding(findings, "ERROR", "INVALID_FINAL_ANSWER_TYPE", "Final answer type must be one of the allowed deduction types.", null);
        }
        if (containsBadAbstractAnswer(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "ABSTRACT_FINAL_ANSWER", "Final answer is too abstract or not a concrete fictional noun phrase.", null);
        }
        if (sourceInput != null && sourceInput.getPlaces() != null) {
            for (AiEpisodeDraftRequest.PlaceInput place : sourceInput.getPlaces()) {
                if (same(place.getName(), draft.getFinalAnswer())) {
                    addFinding(findings, "ERROR", "FINAL_ANSWER_IS_PLACE", "Final answer must not be the same as an actual place name.", null);
                }
            }
        }
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions();
        if (missions == null || missions.size() < 6 || missions.size() > 9) {
            addFinding(findings, "ERROR", "INVALID_MISSION_COUNT", "Draft must include 6 to 9 mission spots.", null);
            return;
        }
        int startCount = 0;
        int answerHintCount = 0;
        int destinationHintCount = 0;
        int finalCount = 0;
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            String markerType = normalize(mission.getMarkerType());
            String publicMarkerType = normalize(mission.getPublicMarkerType());
            if ("START".equals(markerType)) startCount++;
            if ("ANSWER_HINT".equals(markerType)) answerHintCount++;
            if ("DESTINATION_HINT".equals(markerType)) destinationHintCount++;
            if ("FINAL".equals(markerType) || Boolean.TRUE.equals(mission.getFinalPlace())) finalCount++;
            if ("FINAL".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "PUBLIC_FINAL_MARKER_EXPOSED", "publicMarkerType must never be FINAL.", order);
            }
            if (Boolean.TRUE.equals(mission.getFinalPlace()) && !"FINAL_CANDIDATE".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "FINAL_PLACE_PUBLIC_TYPE_INVALID", "Actual final place must be exposed only as FINAL_CANDIDATE.", order);
            }
            if (blank(mission.getStoryText())) {
                addFinding(findings, "WARN", "MISSING_STORY_TEXT", "Spot story text is missing.", order);
            }
            if (blank(mission.getQuestionText())) {
                addFinding(findings, "ERROR", "MISSING_PUZZLE_QUESTION", "Puzzle question is required.", order);
            }
            AiEpisodeDraftRequest.PlaceInput sourcePlace = sourcePlace(sourceInput, order);
            if (sourcePlace != null && usesPlaceNameTextPuzzle(mission, sourcePlace)) {
                addFinding(findings, "ERROR", "QUESTION_USES_PLACE_NAME_TEXT", "퍼즐이 장소명/상호명 글자 추출에 의존합니다. 실제 현장 관찰 요소 기반 문제로 교체하세요.", order);
            }
            if (blank(mission.getAnswer())) {
                addFinding(findings, "ERROR", "MISSING_PUZZLE_ANSWER", "Puzzle answer is required for admin validation.", order);
            }
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                addFinding(findings, "ERROR", "MISSING_HINTS", "Each puzzle must have 3 hints.", order);
            }
            if (!blank(draft.getFinalAnswer()) && textContains(mission.getQuestionText(), draft.getFinalAnswer())) {
                addFinding(findings, "ERROR", "FINAL_ANSWER_IN_QUESTION", "Puzzle question directly contains the final answer.", order);
            }
            if (!blank(draft.getFinalAnswer()) && textContains(mission.getRewardClue(), draft.getFinalAnswer())) {
                addFinding(findings, "WARN", "FULL_FINAL_ANSWER_AS_REWARD", "Reward clue contains the full final answer; use partial clues instead.", order);
            }
            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType())) && !hasProvidedNumber(sourceInput, order)) {
                addFinding(findings, "ERROR", "NUMBER_LOCK_WITHOUT_PROVIDED_NUMBER", "NUMBER_LOCK puzzle requires numbers from admin/TourAPI input.", order);
            }
        }
        if (startCount != 1) {
            addFinding(findings, "ERROR", "INVALID_START_COUNT", "Exactly one START spot is required.", null);
        }
        if (answerHintCount < 4) {
            addFinding(findings, "WARN", "LOW_ANSWER_HINT_COUNT", "MVP recommends 4 ANSWER_HINT spots.", null);
        }
        if (destinationHintCount < 2) {
            addFinding(findings, "ERROR", "LOW_DESTINATION_HINT_COUNT", "At least 2 DESTINATION_HINT spots are required.", null);
        }
        if (finalCount != 1) {
            addFinding(findings, "ERROR", "INVALID_FINAL_PLACE_COUNT", "Exactly one internal final place is required.", null);
        }
        if (draft.getDeductionSecretFacts() == null || draft.getDeductionSecretFacts().isEmpty()) {
            addFinding(findings, "ERROR", "MISSING_DEDUCTION_SECRET_FACTS", "Deduction secret facts are required for final deduction chat.", null);
        }
        if (draft.getDeductionForbiddenReveals() == null || draft.getDeductionForbiddenReveals().stream().noneMatch(value -> same(value, draft.getFinalAnswer()))) {
            addFinding(findings, "ERROR", "MISSING_FORBIDDEN_FINAL_REVEAL", "deductionForbiddenReveals must include the final answer.", null);
        }
        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            addFinding(findings, "WARN", "LOW_SUSPECT_COUNT", "At least 3 suspect cards are recommended.", null);
        }
        if (draft.getEvidences() == null || draft.getEvidences().size() < missions.size()) {
            addFinding(findings, "WARN", "LOW_EVIDENCE_COUNT", "Evidence cards should cover most mission spots.", null);
        }
    }

    private List<AiEpisodeDraftValidationResponse.Finding> validateDraftWithGemini(AiEpisodeDraftValidationRequest request) {
        String prompt = buildValidationPrompt(request);
        String json = callGemini(prompt);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode findingsNode = root.path("findings");
            List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
            if (findingsNode.isArray()) {
                for (JsonNode node : findingsNode) {
                    findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                            .severity(normalizeSeverity(node.path("severity").asText("WARN")))
                            .code(node.path("code").asText("GEMINI_REVIEW"))
                            .message(node.path("message").asText("Gemini validation finding."))
                            .missionOrder(node.path("missionOrder").isNumber() ? node.path("missionOrder").asInt() : null)
                            .build());
                }
            }
            return findings;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_VALIDATION_PARSE_FAILED", "Could not parse Gemini validation response.");
        }
    }

    private String buildValidationPrompt(AiEpisodeDraftValidationRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            return """
                    You are a safety and quality reviewer for an outdoor case-file escape-room draft.
                    Return JSON only. Do not rewrite the draft.
                    Validate whether the draft follows these rules:
                    - It must not expose the internal final place publicly.
                    - publicMarkerType must never be FINAL.
                    - publicMarkerType FINAL_CANDIDATE is allowed and required for the internal final place; do not report FINAL_CANDIDATE itself as final place exposure.
                    - The final answer must not be a real place, historical person, real event, verb, sentence, or abstract single word.
                  - Puzzles must use only provided visibleElements, numbers, keywords, description, and adminMemo.
                  - If a puzzle is an admin-review/RAG-required placeholder with answer "검수필요", do not report it as ERROR.
                  - Do not emit one PLACEHOLDER_PUZZLE finding per mission; summarize missing field evidence as one INFO finding named SITE_DATA_REVIEW_REQUIRED.
                  - If unsafe place-name extraction has already been replaced with generic clue keys such as answer-clue-2, destination-clue-5, or story-clue-3, do not report CLUE_USES_PLACE_NAME_TEXT_EXTRACTION.
                  - If a puzzle asks for a letter/syllable/substring from a place or business name, report QUESTION_USES_PLACE_NAME_TEXT, not HINT_REVEALS_ANSWER.
                    - NUMBER_LOCK must not use numbers absent from input.
                    - Hints must not directly reveal puzzle answers or the final answer.
                    - Story must not distort real history as fact or make real historical people culprits.
                    - Final deduction must have secret facts and forbidden reveal terms.

                    Output schema:
                    {
                      "findings": [
                        {"severity":"ERROR|WARN|INFO","code":"string","message":"Korean or English concise message","missionOrder":1}
                      ]
                    }

                    Draft and source input:
                    """ + payload;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_VALIDATION_INPUT_SERIALIZE_FAILED", "Could not serialize validation input.");
        }
    }

    private List<String> normalizeAndValidateDraft(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<String> warnings = new ArrayList<>();
        if (draft == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_SCHEMA_INVALID", "Gemini draft does not match the required schema.");
        }
        reconcileMissionCount(draft, request, warnings);
        if (!FINAL_ANSWER_TYPES.contains(normalize(draft.getFinalAnswerType()))) {
            draft.setFinalAnswerType("HIDDEN_TRUTH");
            warnings.add("finalAnswerType was normalized to HIDDEN_TRUTH.");
        } else {
            draft.setFinalAnswerType(normalize(draft.getFinalAnswerType()));
        }
        if (blank(draft.getSubtitle())) {
            draft.setSubtitle(defaultSubtitle(draft, request));
            warnings.add("subtitle was generated from selected route context.");
        }
        if (draft.getMaxDeductionQuestions() == null || draft.getMaxDeductionQuestions() <= 0) {
            draft.setMaxDeductionQuestions(20);
        }
        if (draft.getDeductionForbiddenReveals() == null) {
            draft.setDeductionForbiddenReveals(new ArrayList<>());
        }
        if (!blank(draft.getFinalAnswer()) && draft.getDeductionForbiddenReveals().stream().noneMatch(v -> same(v, draft.getFinalAnswer()))) {
            draft.getDeductionForbiddenReveals().add(draft.getFinalAnswer());
        }
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            if (!blank(draft.getFinalAnswer()) && same(place.getName(), draft.getFinalAnswer())) {
                warnings.add("finalAnswer matches a place name and must be changed before saving.");
            }
        }
        boolean finalExists = false;
        for (int i = 0; i < request.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(i);
            String role = normalizeRole(place.getRole(), i, request.getPlaces().size());
            boolean isFinal = "FINAL".equals(role);
            finalExists = finalExists || isFinal;
            mission.setOrder(i + 1);
            mission.setPlaceName(place.getName());
            mission.setAddress(place.getAddress());
            mission.setLatitude(place.getLatitude());
            mission.setLongitude(place.getLongitude());
            mission.setMarkerType(role);
            mission.setPublicMarkerType(publicMarkerType(place.getPublicMarkerType(), isFinal, i, request.getPlaces().size(), role));
            mission.setClueRole(isFinal ? "FINAL_PLACE" : toClueRole(role));
            mission.setFinalPlace(isFinal);
            mission.setArrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius());
            sanitizeFinalPlaceNarrative(mission, role, i, warnings);
            mission.setPuzzleType(PUZZLE_TYPES.contains(normalize(mission.getPuzzleType())) ? normalize(mission.getPuzzleType()) : recommendedPuzzleType(place));
            mission.setAnswerFormat(ANSWER_FORMATS.contains(normalize(mission.getAnswerFormat())) ? normalize(mission.getAnswerFormat()) : answerFormat(place));
            if (usesPlaceNameTextPuzzle(mission, place) || shouldUseAdminReviewPuzzle(mission, place)) {
                applyAdminReviewPuzzle(mission, place);
                warnings.add("미션 " + (i + 1) + "은 현장 근거가 부족해 검수용 문제로 대체되었습니다. RAG 보강 또는 관리자 현장 메모로 확인 범위를 좁힌 뒤 공개 전 문제를 확정하세요.");
            }
            if (blank(mission.getAnswer())) {
                mission.setAnswer(fallbackAnswer(place));
                warnings.add("Mission " + (i + 1) + " answer used fallback from provided field data.");
            }
            if (blank(mission.getRewardClue())) {
                mission.setRewardClue(fallbackReward(role, i));
                warnings.add("Mission " + (i + 1) + " reward clue used fallback.");
            }
            sanitizePlaceNameDependentReward(mission, request, role, i, warnings);
            sanitizeFinalAnswerLeaks(draft, mission, role, i, warnings);
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                warnings.add("Mission " + (i + 1) + " has fewer than 3 hints and needs admin editing.");
            }
            if (blank(mission.getGroundRule())) {
                mission.setGroundRule("Uses only provided visibleElements, numbers, keywords, description, and adminMemo.");
            }
        }
        if (!finalExists) {
            warnings.add("No FINAL role was supplied; the last place should be reviewed as the internal final place.");
        }
        ensureMinimumSuspects(draft, warnings);
        ensureMissionEvidences(draft, request, warnings);
        return warnings;
    }

    private void reconcileMissionCount(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null
                ? new ArrayList<>()
                : new ArrayList<>(draft.getMissions());
        int expected = request.getPlaces().size();
        if (missions.size() > expected) {
            missions = new ArrayList<>(missions.subList(0, expected));
            warnings.add("Gemini returned extra missions; extras were removed to match selected places.");
        }
        while (missions.size() < expected) {
            int index = missions.size();
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(index);
            String role = normalizeRole(place.getRole(), index, expected);
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(index + 1)
                    .placeName(place.getName())
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), index, expected, role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription()) ? "관리자 검수 후 현장 스토리를 입력하세요." : place.getDescription())
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendedPuzzleType(place))
                    .questionText("관리자 검수 후 실제 현장 요소 기반 문제를 입력하세요.")
                    .answer(fallbackAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(fallbackReward(role, index))
                    .hints(List.of(
                            "현장 검수 전 임시 힌트입니다.",
                            "visibleElements, numbers, keywords, adminMemo를 기반으로 구체화하세요.",
                            "정답을 직접 노출하지 않는 최종 힌트로 교체하세요."
                    ))
                    .groundRule("Generated locally because Gemini omitted this selected place.")
                    .build());
        }
        if (draft.getMissions() == null || draft.getMissions().size() != expected) {
            warnings.add("Mission count was normalized to exactly one mission per selected place.");
        }
        draft.setMissions(missions);
    }

    private String defaultSubtitle(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        String start = places.isEmpty() ? "첫 단서" : places.get(0).getName();
        String finalPlace = places.stream()
                .filter(place -> "FINAL".equals(normalize(place.getRole())))
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> places.isEmpty() ? "마지막 후보지" : places.get(places.size() - 1).getName());
        String genre = blank(draft.getGenre()) ? "사건파일" : draft.getGenre();
        return start + "에서 " + finalPlace + "로 이어지는 " + genre;
    }

    private String extractJson(String text) {
        String value = text.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_JSON_NOT_FOUND", "Gemini response did not contain a JSON object.");
        }
        return value.substring(start, end + 1);
    }

    private String normalizeRole(String role, int index, int total) {
        String normalized = normalize(role);
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY", "FINAL_CANDIDATE", "FINAL").contains(normalized)) {
            return normalized;
        }
        if (index == 0) return "START";
        if (index == total - 1) return "FINAL";
        if (index >= total - 3) return "DESTINATION_HINT";
        return "ANSWER_HINT";
    }

    private String toPublicMarker(String markerType) {
        return "FINAL".equals(markerType) ? "FINAL_CANDIDATE" : markerType;
    }

    private String publicMarkerType(String requested, boolean finalPlace, int index, int total, String markerType) {
        if (finalPlace || index == total - 2) {
            return "FINAL_CANDIDATE";
        }
        String normalized = normalize(requested);
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY", "FINAL_CANDIDATE").contains(normalized)) {
            return normalized;
        }
        return toPublicMarker(markerType);
    }

    private String toClueRole(String markerType) {
        return switch (markerType) {
            case "START" -> "START";
            case "ANSWER_HINT" -> "ANSWER_HINT";
            case "DESTINATION_HINT", "FINAL_CANDIDATE", "FINAL" -> "DESTINATION_HINT";
            default -> "STORY_CONTEXT";
        };
    }

    private String recommendedPuzzleType(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return "NUMBER_LOCK";
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) return "OBSERVATION";
        return "STORY_COMBINATION";
    }

    private String answerFormat(AiEpisodeDraftRequest.PlaceInput place) {
        return place.getNumbers() != null && !place.getNumbers().isEmpty() ? "NUMBER" : "TEXT";
    }

    private String fallbackAnswer(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return place.getNumbers().get(0);
        if (place.getKeywords() != null && !place.getKeywords().isEmpty()) return place.getKeywords().get(0);
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) return place.getVisibleElements().get(0);
        return place.getName();
    }

    private String fallbackReward(String role, int index) {
        return switch (role) {
            case "ANSWER_HINT" -> "answer-clue-" + (index + 1);
            case "DESTINATION_HINT", "FINAL_CANDIDATE", "FINAL" -> "destination-clue-" + (index + 1);
            default -> "story-clue-" + (index + 1);
        };
    }

    private void sanitizeFinalPlaceNarrative(
            AiEpisodeDraftResponse.MissionDraft mission,
            String role,
            int index,
            List<String> warnings) {
        boolean finalRole = "FINAL".equals(role) || Boolean.TRUE.equals(mission.getFinalPlace());
        boolean revealsFinal = containsAny(mission.getStoryText(),
                "최종 장소", "최종장소", "최종 목적지", "최종목적지", "마지막 장소", "마지막장소",
                "정답 장소", "정답장소", "최종 추리", "최종추리", "final place", "final destination",
                "answer location");
        boolean repeatsFinalPlaceName = finalRole && textContains(mission.getStoryText(), mission.getPlaceName());
        if (!finalRole && !revealsFinal) {
            return;
        }
        if (revealsFinal || repeatsFinalPlaceName || blank(mission.getStoryText())) {
            mission.setStoryText("이 장소는 여러 단서가 겹치는 조사 후보지입니다. 현장에서는 사건 메모와 주변 분위기만 조용히 확인하세요.");
            warnings.add("Mission " + (index + 1) + " story was sanitized so the internal final place is not revealed.");
        }
    }

    private void sanitizePlaceNameDependentReward(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest request,
            String role,
            int index,
            List<String> warnings) {
        if (mission == null || request == null || request.getPlaces() == null) {
            return;
        }
        String reward = compact(mission.getRewardClue());
        if (reward.isBlank() || isGenericRewardKey(reward)) {
            return;
        }
        boolean rewardFromPlaceName = request.getPlaces().stream()
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(name -> !blank(name))
                .map(this::compact)
                .anyMatch(placeName -> reward.length() <= 4 && placeName.contains(reward));
        boolean textExtractionQuestion = containsAny(mission.getQuestionText(),
                "글자", "음절", "초성", "첫 글자", "첫글자", "마지막 글자", "마지막글자",
                "몇 번째", "몇번째", "n번째", "substring", "syllable", "initial");
        if (rewardFromPlaceName || textExtractionQuestion) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("Mission " + (index + 1) + " reward clue depended on place-name text and was replaced with a generic clue key.");
        }
    }

    private boolean isGenericRewardKey(String reward) {
        return reward.startsWith("answer-clue-")
                || reward.startsWith("destination-clue-")
                || reward.startsWith("story-clue-");
    }

    private boolean usesPlaceNameTextPuzzle(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftRequest.PlaceInput place) {
        String placeName = place.getName();
        if (blank(placeName)) {
            return false;
        }
        String compactPlaceName = compact(placeName);
        String question = compact(mission.getQuestionText());
        String answer = compact(mission.getAnswer());
        boolean referencesPlaceName = question.contains(compactPlaceName);
        boolean asksCharacterExtraction = containsAny(question,
                "\uAE00\uC790", "\uC74C\uC808", "\uCD08\uC131", "\uCCAB\uAE00\uC790",
                "\uCCAB\uBC88\uC9F8", "\uB450\uBC88\uC9F8", "\uC138\uBC88\uC9F8",
                "\uB124\uBC88\uC9F8", "\uB9C8\uC9C0\uB9C9", "\uBA87\uBC88\uC9F8",
                "n\uBC88\uC9F8", "substring", "syllable", "initial");
        boolean answerFromPlaceName = !answer.isBlank() && compactPlaceName.contains(answer);
        return referencesPlaceName && (asksCharacterExtraction || answerFromPlaceName);
    }

    private boolean shouldUseAdminReviewPuzzle(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftRequest.PlaceInput place) {
        if (hasWeakFieldData(place)) {
            return true;
        }
        String question = compact(mission.getQuestionText());
        return containsAny(question,
                "알수없는기호", "특이한표식", "오래된그림", "조형물의특징", "표지판에적힌글귀",
                "특별한장식물", "작은상자", "현장간판", "계단수", "벽화");
    }

    private boolean hasWeakFieldData(AiEpisodeDraftRequest.PlaceInput place) {
        boolean hasRealVisibleElement = place.getVisibleElements() != null && place.getVisibleElements().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::compact)
                .anyMatch(value -> !value.contains("관리자현장메모필요") && !value.contains("현장검수필요"));
        boolean hasNumber = place.getNumbers() != null && !place.getNumbers().isEmpty();
        boolean hasAdminMemo = !blank(place.getAdminMemo()) && !compact(place.getAdminMemo()).contains("운영공개전검수");
        return !hasRealVisibleElement && !hasNumber && !hasAdminMemo;
    }

    private void applyAdminReviewPuzzle(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftRequest.PlaceInput place) {
        mission.setPuzzleType("STORY_COMBINATION");
        mission.setQuestionText("관리자 현장 검수 후, 이 장소에서 실제로 확인 가능한 단서와 사건 메모가 연결되는 키워드를 입력하세요.");
        mission.setAnswer("검수필요");
        mission.setAnswerFormat("TEXT");
        mission.setHints(List.of(
                "아직 현장 검수 전 초안입니다. 장소명 글자 추출은 사용하지 마세요.",
                "실제 간판 문구, 숫자, 조형물은 현장 확인 후에만 문제 근거로 사용하세요.",
                "운영 공개 전 관리자 편집에서 정답과 힌트를 교체하세요."
        ));
        mission.setGroundRule("Gemini가 장소명 글자 추출형 문제를 생성해 운영 불가한 초안을 검수용 placeholder로 교체했습니다.");
        if (blank(mission.getRewardClue())) {
            mission.setRewardClue(fallbackReward(normalizeRole(place.getRole(), 0, 1), 0));
        }
    }

    private void sanitizeFinalAnswerLeaks(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftResponse.MissionDraft mission,
            String role,
            int index,
            List<String> warnings) {
        if (blank(draft.getFinalAnswer())) {
            return;
        }
        if (textContains(mission.getRewardClue(), draft.getFinalAnswer())) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("Mission " + (index + 1) + " reward clue contained the final answer and was replaced with a partial clue key.");
        }
        if (mission.getHints() == null || mission.getHints().isEmpty()) {
            return;
        }
        List<String> sanitizedHints = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < mission.getHints().size(); i++) {
            String hint = mission.getHints().get(i);
            if (textContains(hint, draft.getFinalAnswer()) || textContains(hint, mission.getAnswer())) {
                sanitizedHints.add(safeHint(i));
                changed = true;
            } else {
                sanitizedHints.add(hint);
            }
        }
        if (changed) {
            mission.setHints(sanitizedHints);
            warnings.add("Mission " + (index + 1) + " hints directly revealed an answer and were replaced with safe hints.");
        }
    }

    private String safeHint(int index) {
        return switch (index) {
            case 0 -> "사건파일에 이미 열린 단서와 이 장소의 역할을 먼저 대조하세요.";
            case 1 -> "정답을 직접 찾기보다, 이 장소가 제공하는 단서 유형을 확인하세요.";
            default -> "운영 공개 전 관리자 검수에서 실제 현장 근거가 있는 힌트로 교체하세요.";
        };
    }

    private void ensureMinimumSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> warnings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = draft.getSuspects() == null ? new ArrayList<>() : new ArrayList<>(draft.getSuspects());
        List<AiEpisodeDraftResponse.SuspectDraft> defaults = List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 A").displayName("붉은 장갑의 목격자").suspiciousPoint("사건 경로 주변에서 반복적으로 언급된 인물입니다.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 B").displayName("사라진 기록 담당자").suspiciousPoint("기록과 단서 보관 경로를 알고 있는 인물입니다.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 C").displayName("검은 외투의 전달자").suspiciousPoint("목적지 힌트와 연결된 이동 기록이 남아 있습니다.").build()
        );
        for (AiEpisodeDraftResponse.SuspectDraft fallback : defaults) {
            if (suspects.size() >= 3) {
                break;
            }
            suspects.add(fallback);
        }
        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            warnings.add("Suspect cards were fewer than 3 and were completed with fictional placeholder suspects.");
        }
        draft.setSuspects(suspects);
    }

    private void ensureMissionEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = draft.getEvidences() == null ? new ArrayList<>() : new ArrayList<>(draft.getEvidences());
        int targetCount = request.getPlaces().size();
        for (int i = evidences.size(); i < targetCount; i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            evidences.add(AiEpisodeDraftResponse.EvidenceDraft.builder()
                    .title("검수 필요 사건자료 " + (i + 1))
                    .type(i % 3 == 0 ? "PHOTO" : i % 3 == 1 ? "MEMO" : "NOTE")
                    .imageUrl(generatedEvidenceImage(i % 3 == 0 ? "PHOTO" : i % 3 == 1 ? "MEMO" : "NOTE"))
                    .textSummary(blank(place.getName()) ? "현장 검수 후 작성할 사건자료 초안입니다." : place.getName() + " 조사 후 해금되는 사건자료 초안입니다.")
                    .sourceMissionOrder(i + 1)
                    .build());
        }
        if (draft.getEvidences() == null || draft.getEvidences().size() < targetCount) {
            warnings.add("증거/메모/사진 카드가 부족해 기본 사건자료 카드가 자동 추가되었습니다.");
        }
        draft.setEvidences(evidences);
    }

    private boolean containsAny(String text, String... targets) {
        if (text == null) {
            return false;
        }
        for (String target : targets) {
            if (text.contains(compact(target))) {
                return true;
            }
        }
        return false;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean same(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceAll("\\s+", "").equalsIgnoreCase(b.replaceAll("\\s+", ""));
    }

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder) {
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                .severity(severity)
                .code(code)
                .message(message)
                .missionOrder(missionOrder)
                .build());
    }

    private boolean suppressGeminiFinding(AiEpisodeDraftValidationResponse.Finding finding, AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (finding == null || finding.getCode() == null || draft == null || draft.getMissions() == null) {
            return false;
        }
        String code = normalize(finding.getCode());
        if ("SITE_DATA_REVIEW_REQUIRED".equals(code) && allMissionsAreAdminReviewPlaceholders(draft)) {
            return true;
        }
        if (finding.getMissionOrder() == null) {
            return false;
        }
        if (finding.getMissionOrder() <= 0 || finding.getMissionOrder() > draft.getMissions().size()) {
            return false;
        }
        AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(finding.getMissionOrder() - 1);
        if (List.of("INSUFFICIENT_PUZZLE_DATA", "PLACEHOLDER_PUZZLE").contains(code) && isAdminReviewPlaceholder(mission)) {
            return true;
        }
        if ("CLUE_USES_PLACE_NAME_TEXT_EXTRACTION".equals(code)
                && (isGenericRewardKey(compact(mission.getRewardClue())) || isAdminReviewPlaceholder(mission))) {
            return true;
        }
        if ("FINAL_PLACE_REVEALED_IN_STORY".equals(code) && !storyRevealsFinalPlace(mission)) {
            return true;
        }
        if ("FINAL_PLACE_EXPOSED_PUBLICLY".equals(code)
                && Boolean.TRUE.equals(mission.getFinalPlace())
                && "FINAL_CANDIDATE".equals(normalize(mission.getPublicMarkerType()))) {
            return true;
        }
        if (List.of("FULL_FINAL_ANSWER_AS_REWARD", "HINT_REVEALS_ANSWER").contains(code)) {
            boolean rewardContainsFinal = textContains(mission.getRewardClue(), draft.getFinalAnswer());
            boolean hintContainsFinal = mission.getHints() != null && mission.getHints().stream()
                    .anyMatch(hint -> textContains(hint, draft.getFinalAnswer()));
            return !rewardContainsFinal && !hintContainsFinal;
        }
        return false;
    }

    private boolean allMissionsAreAdminReviewPlaceholders(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft != null
                && draft.getMissions() != null
                && !draft.getMissions().isEmpty()
                && draft.getMissions().stream().allMatch(this::isAdminReviewPlaceholder);
    }

    private boolean storyRevealsFinalPlace(AiEpisodeDraftResponse.MissionDraft mission) {
        if (mission == null) {
            return false;
        }
        return containsAny(mission.getStoryText(),
                "최종 장소", "최종장소", "최종 목적지", "최종목적지", "마지막 장소", "마지막장소",
                "정답 장소", "정답장소", "최종 추리", "최종추리", "final place", "final destination",
                "answer location")
                || (Boolean.TRUE.equals(mission.getFinalPlace()) && textContains(mission.getStoryText(), mission.getPlaceName()));
    }

    private boolean isAdminReviewPlaceholder(AiEpisodeDraftResponse.MissionDraft mission) {
        return mission != null
                && ("검수필요".equals(mission.getAnswer()) || compact(mission.getQuestionText()).contains("관리자현장검수"));
    }

    private String generatedEvidenceImage(String type) {
        return switch (normalize(type)) {
            case "PHOTO" -> "/generated-case-card-photo.svg";
            case "MEMO", "POST_IT" -> "/generated-case-card-memo.svg";
            case "DOCUMENT", "EVIDENCE", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE" -> "/generated-case-card-document.svg";
            case "SUSPECT_CLUE" -> "/generated-case-card-suspect.svg";
            default -> "/generated-case-card-note.svg";
        };
    }

    private int calculateRiskScore(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        int score = 0;
        for (AiEpisodeDraftValidationResponse.Finding finding : findings) {
            score += switch (finding.getSeverity()) {
                case "ERROR" -> 30;
                case "WARN" -> 10;
                default -> 2;
            };
        }
        return Math.min(score, 100);
    }

    private String normalizeSeverity(String value) {
        String normalized = normalize(value);
        if (List.of("ERROR", "WARN", "INFO").contains(normalized)) {
            return normalized;
        }
        return "WARN";
    }

    private boolean containsBadAbstractAnswer(String answer) {
        if (blank(answer)) {
            return false;
        }
        String compact = answer.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return List.of("역사", "진실", "죽음", "기록하다", "진실을밝히다", "truth", "history").contains(compact);
    }

    private boolean textContains(String text, String target) {
        if (blank(text) || blank(target)) {
            return false;
        }
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                .contains(target.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
    }

    private boolean hasProvidedNumber(AiEpisodeDraftRequest sourceInput, int missionOrder) {
        if (sourceInput == null || sourceInput.getPlaces() == null || missionOrder <= 0 || missionOrder > sourceInput.getPlaces().size()) {
            return false;
        }
        AiEpisodeDraftRequest.PlaceInput place = sourceInput.getPlaces().get(missionOrder - 1);
        return place.getNumbers() != null && !place.getNumbers().isEmpty();
    }

    private AiEpisodeDraftRequest.PlaceInput sourcePlace(AiEpisodeDraftRequest sourceInput, int missionOrder) {
        if (sourceInput == null || sourceInput.getPlaces() == null || missionOrder <= 0 || missionOrder > sourceInput.getPlaces().size()) {
            return null;
        }
        return sourceInput.getPlaces().get(missionOrder - 1);
    }
}
