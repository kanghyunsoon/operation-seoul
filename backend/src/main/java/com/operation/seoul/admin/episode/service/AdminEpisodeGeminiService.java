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

@SuppressWarnings("SpellCheckingInspection")
@Service
@RequiredArgsConstructor
public class AdminEpisodeGeminiService {
    private static final Set<String> FINAL_ANSWER_TYPES = Set.of("CULPRIT", "WEAPON", "EVIDENCE", "HIDDEN_DOCUMENT", "SECRET_KEYWORD", "HIDDEN_TRUTH");
    private static final Set<String> PUZZLE_TYPES = Set.of("OBSERVATION", "NUMBER_LOCK", "INITIAL_SOUND", "PATTERN", "STORY_COMBINATION");
    private static final Set<String> ANSWER_FORMATS = Set.of("TEXT", "NUMBER", "CHOICE", "CODE");
    private static final Set<String> BLOCKED_HISTORICAL_NAMES = Set.of(
            "고종", "순종", "명성황후", "이완용", "박제순", "이지용", "이근택", "권중현", "을사오적",
            "안중근", "윤봉길", "김구", "이토 히로부미", "이토히로부미"
    );

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(geminiRequestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.1-flash-lite}")
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
                .message("Gemini created a structured case-file draft with AI/site-data verification targets.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of(
                        "Review final answer, aliases, and forbidden reveals.",
                        "Verify each puzzle is grounded in selected place data, admin memo, AI/site enrichment, or fiction-safe clues.",
                        "Edit puzzles, hints, reward_payload, suspects, and evidences before saving as DRAFT.",
                        "Save as DRAFT first, then publish when AI/site-data checks pass."
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
                        ? "Draft passed required validation checks for AI/site-data operation."
                        : "Draft has blocking issues. Fix required items before saving or publishing.")
                .findings(findings)
                .requiredFixes(requiredFixes)
                .publishChecklist(List.of(
                        "Confirm every place coordinate and arrival radius from selected place data or admin GPS QA.",
                        "Confirm every puzzle uses provided candidate data, admin memo, AI/site enrichment, or generated fiction-safe clues.",
                        "Confirm final answer is fictional and not a real place, person, or event.",
                        "Confirm map API will expose only publicMarkerType, never internal finalPlace.",
                        "Save generated content as DRAFT first and publish when blocking issues are resolved."
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
                - If provided field data is too weak for a real observation puzzle, create a playable STORY_COMBINATION or PATTERN puzzle using keywords/description/adminMemo. Use answer "검수필요" only when there is no usable keyword, description, adminMemo, visibleElement, or number.
                - Do not mark every mission as placeholder just because on-site inspection is still required. If RAG/site enrichment narrowed the verification scope, create a playable draft puzzle and state the verification basis in groundRule.
                - Do not make a real historical person the culprit.
                - Prefer finalAnswerType EVIDENCE, WEAPON, HIDDEN_DOCUMENT, SECRET_KEYWORD, or HIDDEN_TRUTH. Use CULPRIT only when the answer is a fictional title/role, never a real-name-like person.
                - Never include real historical names such as 고종, 이완용, 을사오적, 안중근, 김구, 이토 히로부미 in finalAnswer, suspect names, culprit labels, or answer aliases.
                - Do not make finalAnswer a construction like "real historical person + fictional assistant/secretary". That still exposes real history as culprit context.
                - Do not present a real historical event as a distorted fact.
                - The final answer must be a clear noun phrase inside the fictional case, not a place name, real person, real event, verb, sentence, or abstract single word.
                - The real final place is an internal role only. Normal user map responses will hide that spot; use publicMarkerType DESTINATION_HINT for generated final-place data.
                - A final-place mission story must read like a normal investigation candidate. It must not say or imply "this is the final place", "final deduction starts here", or "the answer location".
                - Do not describe the real final place as the final destination in storyText, rewardClue, destination hints, finalTruthSummary, actualHistorySummary, or deductionSecretFacts.
                - Use exactly 9 missions when possible: 1 START mission that gives the story premise, 4 ANSWER_HINT missions, 3 DESTINATION_HINT missions, and 1 internal FINAL mission.
                - Create 4 answer clues and 3 destination clues when enough places exist.
                - Make the draft feel like a real printed detective kit: each mission must reveal a different document, photo, memo, suspect contradiction, route note, or cipher card.
                - Build one coherent mystery chain. The synopsis, finalQuestion, suspects, evidences, mission rewardClue, and hints must all point to the same final answer and hidden destination logic.
                - If finalAnswerType is not CULPRIT, suspects are not "the criminal". They are stakeholders, handlers, witnesses, couriers, archivists, brokers, or false leads whose statements reveal contradictions about the hidden document/object/location.
                - Every suspect card must explain "what this person might have hidden or distorted" and "which clue type can confirm or weaken that suspicion" in natural Korean.
                - At least one evidence card must support each suspect's suspiciousPoint or alibiSummary. Evidence summaries should name the relevant suspect title/displayName when useful.
                - Hints must be usable for deduction. Do not write vague mood hints only. Each hint should narrow one of these: final object/document identity, who moved it, why it was moved, route direction, or destination feature.
                - The player must be able to infer the final answer by combining 3 to 5 unlocked cards without knowing the real final place name.
                - Suspects must have concrete fictional names/titles, relationToVictim, suspiciousPoint, and alibiSummary. Do not return generic "AI draft" or "admin review" suspect text.
                - Evidences must have specific case-file titles and summaries tied to mission rewardClue. Do not return generic "case sketch", "draft card", or "admin review" evidence text.
                - For every suspect and every evidence card, provide a separate imagePrompt field. Each imagePrompt must be a copy-ready English prompt for an external image generator.
                - imagePrompt must describe the subject, visual style, mood, composition, and negative constraints. It must not say "same as above" and must not depend on another card.
                - Every suspect imagePrompt must explicitly say that the subject is a fictional Korean person from Seoul, South Korea. Preserve the character's intended age, gender, occupation, and historical era. Explicitly prevent the image generator from casting a Western or European-looking model or changing the character's Korean identity.
                - Every evidence imagePrompt that may contain a person, hand, portrait, reflection, or silhouette must explicitly require a fictional Korean person and Seoul-appropriate styling for the story era.
                - Do not put generated image URLs in imageUrl unless the admin provided one. Leave imageUrl empty by default and use imagePrompt for manual generation.
                - Every mission puzzle must have a clear question, answer, three hints, and a rewardClue that advances either the final answer or destination inference.
                - Vary puzzle reasoning patterns across missions. Mix number locks, word composition, color/order logic, memory cues, pattern locks, direction sequences, switch decisions, shadow/shape matching, quick-tap urgency, and small sliding-order puzzles.
                - The UI will render from fixed minigame components; you should generate story-matched text, answer, clue labels, and reasoning, not arbitrary UI instructions.
                - Brain-teaser style is encouraged: use shape counts, hidden letter meaning, zodiac/semantic mapping, sequence gaps, grid order, route direction, color-symbol code, and contradiction matching. Do not repeat the same puzzle mechanic more than twice in one episode.
                - Do not include the real final place name in episodeTitle, subtitle, fictionSynopsis, finalTruthSummary, actualHistorySummary, destination clues, or evidence summaries.
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
                      "markerType": "START|ANSWER_HINT|DESTINATION_HINT|FINAL",
                      "publicMarkerType": "START|ANSWER_HINT|DESTINATION_HINT",
                      "clueRole": "START|ANSWER_HINT|DESTINATION_HINT|FINAL_PLACE",
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
                  "suspects": [{"alias":"string","displayName":"string","portraitImageUrl":"","imagePrompt":"string","shortDescription":"string","relationToVictim":"string","suspiciousPoint":"string","alibiSummary":"string"}],
                  "evidences": [{"title":"string","type":"PHOTO|MEMO|NOTE|DOCUMENT|EVIDENCE|SUSPECT_CLUE|POST_IT|ANSWER_CLUE|DESTINATION_CLUE|STORY_CLUE","imageUrl":"","imagePrompt":"string","textSummary":"string","sourceMissionOrder":1}]
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
        if (containsBlockedHistoricalName(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ANSWER", "Final answer must not use a real historical person as the culprit or answer.", null);
        }
        if ("CULPRIT".equals(normalize(draft.getFinalAnswerType())) && looksLikeRealNameCulprit(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_NAME_LIKE_CULPRIT", "CULPRIT answer must be a fictional role or alias, not a real person name.", null);
        }
        if (draft.getFinalAnswerAliases() != null && draft.getFinalAnswerAliases().stream().anyMatch(this::containsBlockedHistoricalName)) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ALIAS", "Final answer aliases must not include real historical person names.", null);
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
            if (Boolean.TRUE.equals(mission.getFinalPlace()) && "START".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "FINAL_PLACE_PUBLIC_TYPE_INVALID", "Actual final place must not be exposed as START.", order);
            }
            if (blank(mission.getStoryText())) {
                addFinding(findings, "WARN", "MISSING_STORY_TEXT", "Spot story text is missing.", order);
            }
            if (blank(mission.getQuestionText())) {
                addFinding(findings, "ERROR", "MISSING_PUZZLE_QUESTION", "Puzzle question is required.", order);
            }
            AiEpisodeDraftRequest.PlaceInput sourcePlace = sourcePlace(sourceInput, order);
            if (sourcePlace != null && usesPlaceNameTextPuzzle(mission, sourcePlace)) {
                addFinding(findings, "ERROR", "QUESTION_USES_PLACE_NAME_TEXT", "Puzzle must not depend on extracting letters from a place or business name.", order);
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
            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType())) && lacksProvidedNumber(sourceInput, order)) {
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
                      - Internal final places must use publicMarkerType DESTINATION_HINT; do not expose FINAL publicly.
                      - Puzzles must use only provided visibleElements, numbers, keywords, description, and adminMemo.
                    - If a puzzle is an admin-review/RAG-required placeholder with answer "검수필요", do not report it as ERROR.
                    - If RAG/site enrichment provides keywords, description, visibleElements, or adminMemo, do not demand a placeholder; allow playable draft puzzles with a groundRule that says admin verification is required.
                    - Do not emit one PLACEHOLDER_PUZZLE finding per mission; summarize missing field evidence as one INFO finding named SITE_DATA_REVIEW_REQUIRED.
                    - If unsafe place-name extraction has already been replaced with generic clue keys such as answer-clue-2, destination-clue-5, or story-clue-3, do not report CLUE_USES_PLACE_NAME_TEXT_EXTRACTION.
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
            warnings.add("Draft normalization changed a field; review before publishing.");
        } else {
            draft.setFinalAnswerType(normalize(draft.getFinalAnswerType()));
        }
        if (containsBlockedHistoricalName(draft.getFinalAnswer())
                || ("CULPRIT".equals(normalize(draft.getFinalAnswerType())) && looksLikeRealNameCulprit(draft.getFinalAnswer()))) {
            draft.setFinalAnswerType("HIDDEN_DOCUMENT");
            draft.setFinalAnswer("봉인된 사진 봉투");
            draft.setFinalAnswerAliases(new ArrayList<>(List.of("봉인된사진봉투", "사진 봉투", "봉인 봉투")));
            draft.setFinalQuestion("What hidden case object do the unlocked evidence cards identify?");
            draft.setFinalTruthSummary("수집한 단서들은 사라진 기록 문서가 아니라 봉인된 사진 봉투를 가리킨다. 봉투 안에는 사건 당일 이동 경로와 조작된 증언을 뒤집는 자료가 들어 있다.");
            draft.setDeductionSecretFacts(new ArrayList<>(List.of(
                    "최종 정답은 실제 인물이나 장소명이 아니라 픽션 사건 안의 숨겨진 문서다.",
                    "봉인된 사진 봉투는 사진 기록과 목격자 진술을 함께 묶는 핵심 증거다.",
                    "정답 힌트는 봉인, 사진, 봉투, 조작된 기록이라는 방향으로 조합된다."
            )));
            warnings.add("Draft normalization changed a field; review before publishing.");
        }
        if (blank(draft.getSubtitle())) {
            draft.setSubtitle(defaultSubtitle(draft, request));
            warnings.add("Draft normalization changed a field; review before publishing.");
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
        sanitizeForbiddenReveals(draft, request, warnings);
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            if (!blank(draft.getFinalAnswer()) && same(place.getName(), draft.getFinalAnswer())) {
                warnings.add("Draft normalization changed a field; review before publishing.");
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
            mission.setPublicMarkerType(publicMarkerType(place.getPublicMarkerType(), isFinal, role));
            mission.setClueRole(isFinal ? "FINAL_PLACE" : toClueRole(role));
            mission.setFinalPlace(isFinal);
            mission.setArrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius());
            sanitizeFinalPlaceNarrative(mission, role, i, warnings);
            mission.setPuzzleType(PUZZLE_TYPES.contains(normalize(mission.getPuzzleType())) ? normalize(mission.getPuzzleType()) : recommendedPuzzleType(place));
            mission.setAnswerFormat(ANSWER_FORMATS.contains(normalize(mission.getAnswerFormat())) ? normalize(mission.getAnswerFormat()) : answerFormat(place));
            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType())) && lacksProvidedNumber(request, i + 1)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (usesPlaceNameTextPuzzle(mission, place) || usesWeakTextExtractionPuzzle(mission) || shouldUseReviewFallback(mission, place)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (hasInvalidPuzzleAnswer(mission, place, request)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("Mission " + (i + 1) + " answer was a place name or invalid fallback; review before publishing.");
            }
            if (blank(mission.getAnswer())) {
                mission.setAnswer(fallbackAnswer(place));
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (blank(mission.getRewardClue())) {
                mission.setRewardClue(fallbackReward(role, i));
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            sanitizePlaceNameDependentReward(mission, request, role, i, warnings);
            sanitizeForbiddenRevealReward(mission, draft, role, i, warnings);
            sanitizeFinalAnswerLeaks(draft, mission, role, i, warnings);
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (blank(mission.getGroundRule())) {
                mission.setGroundRule("Uses only provided visibleElements, numbers, keywords, description, and adminMemo.");
            }
        }
        if (!finalExists) {
            warnings.add("Final place and final answer require admin review.");
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
            warnings.add("Draft normalization changed a field; review before publishing.");
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
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription()) ? "Open the case file and identify how this spot supports the story premise." : place.getDescription())
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendedPuzzleType(place))
                    .questionText("Enter the clue keyword that connects this spot to the case file.")
                    .answer(fallbackAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(fallbackReward(role, index))
                    .hints(List.of(
                            "Review this generated draft before publishing.",
                            "Review this generated draft before publishing.",
                            "Review this generated draft before publishing."
                    ))
                    .groundRule("Generated locally because Gemini omitted this selected place.")
                    .build());
        }
        if (draft.getMissions() == null || draft.getMissions().size() != expected) {
            warnings.add("Draft normalization changed a field; review before publishing.");
        }
        draft.setMissions(missions);
    }

    private String defaultSubtitle(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        String start = places.isEmpty() ? "case start" : places.get(0).getName();
        String genre = blank(draft.getGenre()) ? "case mystery" : draft.getGenre();
        return start + " case-file route: " + genre;
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
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT", "FINAL").contains(normalized)) {
            return normalized;
        }
        if (index == 0) return "START";
        if (index == total - 1) return "FINAL";
        if (index >= total - 4) return "DESTINATION_HINT";
        return "ANSWER_HINT";
    }

    private String toPublicMarker(String markerType) {
        return "FINAL".equals(markerType) ? "DESTINATION_HINT" : markerType;
    }

    private String publicMarkerType(String requested, boolean finalPlace, String markerType) {
        if (finalPlace) {
            return "DESTINATION_HINT";
        }
        String normalized = normalize(requested);
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT").contains(normalized)) {
            return normalized;
        }
        return toPublicMarker(markerType);
    }

    private String toClueRole(String markerType) {
        return switch (markerType) {
            case "START" -> "START";
            case "DESTINATION_HINT" -> "DESTINATION_HINT";
            case "FINAL" -> "FINAL_PLACE";
            default -> "ANSWER_HINT"; // "ANSWER_HINT"가 들어와도 여기로 빠져서 정상 동작함
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
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty())
            return place.getVisibleElements().get(0);
        String basis = bestPuzzleBasis(place);
        return isReviewRequiredBasis(basis) ? "\ud604\uc7a5\ub2e8\uc11c" : basis;
    }

    private String fallbackReward(String role, int index) {
        return switch (role) {
            case "ANSWER_HINT" -> List.of("찢긴 흔적", "렌즈의 곡면", "차가운 유리", "반사된 그림자").get(Math.min(index, 3));
            case "DESTINATION_HINT", "FINAL" -> index % 2 == 0 ? "붉은 벽의 침묵" : "기록이 닫힌 문";
            default -> List.of("마지막 사진", "봉인된 봉투", "엇갈린 진술", "사라진 시간").get(Math.min(index % 4, 3));
        };
    }

    private void sanitizeForbiddenReveals(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<String> sourcePlaceNames = request.getPlaces() == null
                ? List.of()
                : request.getPlaces().stream()
                  .map(AiEpisodeDraftRequest.PlaceInput::getName)
                  .filter(name -> !blank(name))
                  .toList();
        List<String> sanitized = new ArrayList<>();
        for (String reveal : draft.getDeductionForbiddenReveals()) {
            if (blank(reveal)) {
                continue;
            }
            boolean isPlaceName = sourcePlaceNames.stream().anyMatch(place -> same(place, reveal) || looksLikePlaceNameFragment(place, reveal));
            boolean finalPlaceRevealPhrase = containsFinalRevealTerms(reveal);
            boolean genericRevealPhrase = containsAny(reveal, "review required", "admin review", "field review", "placeholder");
            if (isPlaceName || finalPlaceRevealPhrase || genericRevealPhrase) {
                warnings.add("Forbidden reveal removed: " + reveal);
                continue;
            }
            if (sanitized.stream().noneMatch(existing -> same(existing, reveal))) {
                sanitized.add(reveal);
            }
        }
        if (!blank(draft.getFinalAnswer()) && sanitized.stream().noneMatch(value -> same(value, draft.getFinalAnswer()))) {
            sanitized.add(draft.getFinalAnswer());
        }
        if (sanitized.stream().noneMatch(value -> same(value, "actualFinalPlace"))) {
            sanitized.add("actualFinalPlace");
        }
        if (sanitized.stream().noneMatch(value -> same(value, "realPersonAsCulprit"))) {
            sanitized.add("realPersonAsCulprit");
        }
        draft.setDeductionForbiddenReveals(sanitized);
    }


    private void sanitizeFinalPlaceNarrative(
            AiEpisodeDraftResponse.MissionDraft mission,
            String role,
            int index,
            List<String> warnings) {
        boolean finalRole = "FINAL".equals(role) || Boolean.TRUE.equals(mission.getFinalPlace());
        boolean revealsFinal = containsFinalRevealTerms(mission.getStoryText());
        boolean repeatsFinalPlaceName = finalRole && textContains(mission.getStoryText(), mission.getPlaceName());
        if (!finalRole && !revealsFinal) {
            return;
        }
        if (revealsFinal || repeatsFinalPlaceName || blank(mission.getStoryText())) {
            mission.setStoryText("Review this generated draft before publishing.");
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
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
                "substring", "syllable", "initial", "letter", "nth", "first", "last");
        if (rewardFromPlaceName || textExtractionQuestion) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
        }
    }


    private void sanitizeForbiddenRevealReward(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            String role,
            int index,
            List<String> warnings) {
        if (mission == null || draft.getDeductionForbiddenReveals() == null || blank(mission.getRewardClue())) {
            return;
        }
        String reward = mission.getRewardClue();
        boolean exposesForbiddenReveal = draft.getDeductionForbiddenReveals().stream()
                .filter(value -> !blank(value))
                .anyMatch(value -> same(value, reward) || textContains(reward, value));
        if (exposesForbiddenReveal) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
        }
    }


    private boolean isGenericRewardKey(String reward) {
        return reward.startsWith("answer-clue-")
                || reward.startsWith("destination-clue-")
                || reward.startsWith("story-clue-");
    }

    private boolean looksLikePlaceNameFragment(String placeName, String value) {
        String compactPlace = compact(placeName);
        String compactValue = compact(value);
        if (compactPlace.isBlank() || compactValue.length() < 3) return false;
        if (compactPlace.contains(compactValue) || compactValue.contains(compactPlace)) return true;
        String normalizedPlace = removeCommonLocationSuffix(compactPlace);
        String normalizedValue = removeCommonLocationSuffix(compactValue);
        return normalizedPlace.length() >= 3 && normalizedValue.length() >= 3
                && (normalizedPlace.contains(normalizedValue) || normalizedValue.contains(normalizedPlace));
    }


    private String removeCommonLocationSuffix(String value) {
        return value.replace("main branch", "")
                .replace("park", "")
                .replace("museum", "")
                .replace("gallery", "")
                .replace("history hall", "");
    }

    private boolean usesPlaceNameTextPuzzle(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftRequest.PlaceInput place) {
        String placeName = place.getName();
        if (blank(placeName)) return false;
        String compactPlaceName = compact(placeName);
        String question = compact(mission.getQuestionText());
        String answer = compact(mission.getAnswer());
        boolean referencesPlaceName = question.contains(compactPlaceName);
        boolean asksCharacterExtraction = containsAny(question, "letter", "syllable", "initial", "first", "second", "third", "fourth", "last", "substring", "nth");
        boolean answerFromPlaceName = !answer.isBlank() && answer.length() <= 4 && compactPlaceName.contains(answer);
        return referencesPlaceName && (asksCharacterExtraction || answerFromPlaceName);
    }


    private boolean shouldUseReviewFallback(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftRequest.PlaceInput place) {
        String question = compact(mission.getQuestionText());
        return containsAny(question, "unverifiable", "placeholder", "admin review", "field review", "review required") && hasWeakFieldData(place);
    }


    private boolean hasInvalidPuzzleAnswer(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request) {
        if (mission == null || place == null || blank(mission.getAnswer())) {
            return false;
        }
        String answer = compact(mission.getAnswer());
        if (answer.isBlank() || "review-required".equals(answer) || answer.contains("\uac80\uc218\ud544\uc694")) {
            return false;
        }
        if (isGenericBasisLabel(answer) || isPlaceNameAnswer(answer, place.getName())) {
            return true;
        }
        if (request != null && request.getPlaces() != null && request.getPlaces().stream()
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .anyMatch(name -> isPlaceNameAnswer(answer, name))) {
            return true;
        }
        if ("NUMBER".equals(normalize(mission.getAnswerFormat())) || "NUMBER_LOCK".equals(normalize(mission.getPuzzleType()))) {
            return place.getNumbers() == null || place.getNumbers().stream()
                    .filter(value -> !blank(value))
                    .noneMatch(value -> same(value, mission.getAnswer()));
        }
        return false;
    }

    private boolean isPlaceNameAnswer(String compactAnswer, String placeName) {
        if (blank(compactAnswer) || blank(placeName)) {
            return false;
        }
        String compactPlaceName = compact(placeName);
        return compactPlaceName.equals(compactAnswer)
                || compactAnswer.equals(compactPlaceName)
                || (compactPlaceName.length() >= 4 && compactAnswer.contains(compactPlaceName));
    }

    private boolean isGenericBasisLabel(String compactAnswer) {
        return Set.of(
                "placedescription", "adminmemo", "casememo", "selectedoperationspot",
                "selected", "operation", "spot", "nearby", "verification", "focus",
                "place", "address", "entrance", "area", "siteverificationfocus", "nearbyfamousplacesignal"
        ).contains(compactAnswer);
    }


    private boolean usesWeakTextExtractionPuzzle(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = compact(String.join(" ", blank(mission.getQuestionText()) ? "" : mission.getQuestionText(), blank(mission.getAnswer()) ? "" : mission.getAnswer(), mission.getHints() == null ? "" : String.join(" ", mission.getHints())));
        return containsAny(text, "letter count", "nth letter", "syllable", "initial only", "first letter", "last letter", "combine in order", "substring");
    }


    private void applyPlayableStoryPuzzle(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            String role,
            int index) {
        String reward = blank(mission.getRewardClue()) || isGenericRewardKey(compact(mission.getRewardClue()))
                ? fallbackReward(role, index)
                : mission.getRewardClue();
        String basis = bestPuzzleBasis(place);
        if (isReviewRequiredBasis(basis)) {
            basis = fallbackPuzzleBasis(role, index);
        }
        if (!textContains(mission.getStoryText(), basis)) {
            String story = blank(mission.getStoryText()) ? "\uacf5\uac1c \uc804 \uad00\ub9ac\uc790 \uac80\uc218\uac00 \ud544\uc694\ud55c \ucd08\uc548\uc785\ub2c8\ub2e4." : mission.getStoryText();
            mission.setStoryText(story + " \uac80\uc99d \uae30\uc900 \ub2e8\uc11c: " + basis + ".");
        }
        mission.setPuzzleType("STORY_COMBINATION");
        mission.setQuestionText("\uc81c\uacf5\ub41c \ud604\uc7a5 \uadfc\uac70 [" + basis + "]\ub97c \uc0ac\uac74\ud30c\uc77c\uacfc \uc5f0\uacb0\ud55c \ud575\uc2ec \ub2e8\uc5b4\ub97c \uc785\ub825\ud558\uc138\uc694.");
        mission.setAnswer(basis);
        mission.setAnswerFormat("TEXT");
        mission.setRewardClue(reward);
        mission.setHints(List.of(
                "\ubb38\uc81c\uc5d0 \uc81c\uc2dc\ub41c [" + basis + "] \ub2e8\uc11c\ub97c \uba3c\uc800 \ud655\uc778\ud558\uc138\uc694.",
                "\uc774 \ub2e8\uc11c\ub294 " + markerRoleLabel(role) + " \ud750\ub984\uc744 \ubcf4\uac15\ud569\ub2c8\ub2e4.",
                "\ub2e4\ub978 \uc7a5\uc18c\uc758 \uc9c4\ud589 \uc21c\uc11c\uac00 \uc544\ub2c8\ub77c \ud604\uc7ac \uc7a5\uc18c\uc758 \uadfc\uac70\ub9cc \uc0ac\uc6a9\ud558\uc138\uc694."
        ));
        mission.setGroundRule("\uc81c\uacf5\ub41c \ud604\uc7a5 \uadfc\uac70 [" + basis + "]\ub97c \uc0ac\uac74\ud30c\uc77c\uacfc \uc5f0\uacb0\ud569\ub2c8\ub2e4.");
    }


    private String bestPuzzleBasis(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null) return "\uac80\uc218\ud544\uc694";
        if (place.getKeywords() != null) {
            String keyword = place.getKeywords().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!blank(keyword)) return keyword;
        }
        if (place.getVisibleElements() != null) {
            String visible = place.getVisibleElements().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!blank(visible)) return visible;
        }
        String memoBasis = extractBasisPhrase(place.getAdminMemo(), place.getName());
        if (!blank(memoBasis)) return memoBasis;
        String descriptionBasis = extractBasisPhrase(place.getDescription(), place.getName());
        if (!blank(descriptionBasis)) return descriptionBasis;
        return "\uac80\uc218\ud544\uc694";
    }

    private boolean isReviewRequiredBasis(String basis) {
        String compactBasis = compact(basis);
        return compactBasis.isBlank() || compactBasis.contains("\uac80\uc218\ud544\uc694") || "review-required".equals(compactBasis);
    }

    private String fallbackPuzzleBasis(String role, int index) {
        return switch (role) {
            case "START" -> "\uccab\uae30\ub85d";
            case "ANSWER_HINT" -> List.of("\ubd09\uc778", "\uc0ac\uc9c4", "\ubb38\uc11c", "\uadf8\ub9bc\uc790").get(Math.min(Math.max(index - 1, 0), 3));
            case "DESTINATION_HINT", "FINAL" -> index % 2 == 0 ? "\ubd89\uc740\ubcbd" : "\ub2eb\ud78c\ubb38";
            default -> "\ud604\uc7a5\ub2e8\uc11c";
        };
    }

    private boolean isUsableAnswerBasis(String value, String placeName) {
        if (blank(value)) {
            return false;
        }
        String compactValue = compact(value);
        return !isGenericBasisLabel(compactValue) && !isPlaceNameAnswer(compactValue, placeName);
    }

    private String extractBasisPhrase(String text, String placeName) {
        if (blank(text)) {
            return null;
        }
        String compactPlaceName = compact(placeName);
        String cleaned = text
                .replaceAll("[\\[\\]{}()\"'`.,:;!?/\\\\|<>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        for (String token : cleaned.split(" ")) {
            String candidate = token.trim();
            String compactCandidate = compact(candidate);
            if (candidate.length() < 2 || candidate.length() > 12) {
                continue;
            }
            if (!compactPlaceName.isBlank() && (compactPlaceName.contains(compactCandidate) || compactCandidate.contains(compactPlaceName))) {
                continue;
            }
            if (isGenericBasisLabel(compactCandidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }


    private String markerRoleLabel(String role) {
        return switch (role) {
            case "ANSWER_HINT" -> "\uc815\ub2f5 \ub2e8\uc11c";
            case "DESTINATION_HINT", "FINAL" -> "\ubaa9\uc801\uc9c0 \ub2e8\uc11c";
            case "START" -> "\uc2dc\uc791 \ub2e8\uc11c";
            default -> "\uc2a4\ud1a0\ub9ac \ub2e8\uc11c";
        };
    }


    private boolean hasWeakFieldData(AiEpisodeDraftRequest.PlaceInput place) {
        boolean hasRealVisibleElement = place.getVisibleElements() != null && place.getVisibleElements().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::compact)
                .anyMatch(value -> !value.contains("관리자현장메모필요") && !value.contains("현장검수필요"));
        boolean hasNumber = place.getNumbers() != null && !place.getNumbers().isEmpty();
        boolean hasKeyword = place.getKeywords() != null && place.getKeywords().stream().anyMatch(value -> value != null && !value.isBlank());
        boolean hasDescription = !blank(place.getDescription()) && !compact(place.getDescription()).contains("selected operation spot");
        boolean hasAdminMemo = !blank(place.getAdminMemo()) && !compact(place.getAdminMemo()).contains("운영공개전검수");
        return !hasRealVisibleElement && !hasNumber && !hasKeyword && !hasDescription && !hasAdminMemo;
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
        if (textContains(mission.getQuestionText(), draft.getFinalAnswer()) || containsFinalAnswerAlias(mission.getQuestionText(), draft)) {
            mission.setQuestionText(safeQuestionText(role));
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
        }
        if (textContains(mission.getRewardClue(), draft.getFinalAnswer())) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
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
            warnings.add("Mission " + (index + 1) + " was normalized; review before publishing.");
        }
    }

    private boolean containsFinalAnswerAlias(String text, AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (blank(text) || draft.getFinalAnswerAliases() == null) {
            return false;
        }
        return draft.getFinalAnswerAliases().stream()
                .filter(alias -> !blank(alias))
                .anyMatch(alias -> textContains(text, alias));
    }

    private String safeQuestionText(String role) {
        if ("FINAL".equals(role)) {
            return "Use the unlocked case-file cards to infer the hidden truth without naming the location directly.";
        }
        return "Compare this spot's verified clue with the case memo and enter the clue keyword.";
    }

    private String safeHint(int index) {
        return switch (index) {
            case 0 -> "Separate object clues from route clues before guessing.";
            case 1 -> "Use suspect statements only as contradiction checks.";
            default -> "Combine the latest reward clue with previously unlocked evidence cards.";
        };
    }


    private void ensureMinimumSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> warnings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = draft.getSuspects() == null ? new ArrayList<>() : new ArrayList<>(draft.getSuspects());
        List<AiEpisodeDraftResponse.SuspectDraft> defaults = List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("의뢰인")
                        .displayName("봉투를 맡긴 기록 중개인")
                        .shortDescription("사건을 의뢰했지만 자신이 받은 봉투의 출처를 끝까지 숨기는 인물입니다.")
                        .relationToVictim("사라진 문서의 최초 전달자")
                        .suspiciousPoint("문서가 사라지기 전 마지막으로 봉투의 봉인을 확인했고, 봉투 안 물건의 정확한 이름을 알고 있습니다.")
                        .alibiSummary("의뢰 시간에는 다른 장소에 있었다고 주장하지만, 정답 힌트 카드의 봉인 문양 설명과 그의 진술이 맞물립니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("정리관")
                        .displayName("기록 순서를 바꾼 보관 담당자")
                        .shortDescription("문서와 사진의 순서를 정리하던 중 일부 자료를 다른 파일철로 옮긴 인물입니다.")
                        .relationToVictim("사건 자료를 분류하던 내부 협력자")
                        .suspiciousPoint("사진, 메모, 목격 기록의 시간 순서를 바꾸면 최종 목적지가 전혀 다른 곳처럼 보이게 만들 수 있습니다.")
                        .alibiSummary("자료실에만 있었다고 주장하지만, 목적지 힌트 카드 하나가 그의 이동 경로와 충돌합니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("전달자")
                        .displayName("마지막 쪽지를 옮긴 연락책")
                        .shortDescription("최종 장소를 직접 말하지 않고 방향과 물건의 특징만 남긴 연락책입니다.")
                        .relationToVictim("마지막 단서를 운반한 증언자")
                        .suspiciousPoint("정답을 훔친 범인이라기보다, 정답을 보호하기 위해 일부 힌트를 일부러 흐리게 남겼을 가능성이 있습니다.")
                        .alibiSummary("가방만 전달했다고 주장하지만, 스토리 단서와 목적지 힌트를 함께 보면 그가 숨긴 방향성이 드러납니다.")
                        .build()
        );
        for (AiEpisodeDraftResponse.SuspectDraft fallback : defaults) {
            if (suspects.size() >= 3) {
                break;
            }
            suspects.add(fallback);
        }
        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            warnings.add("용의자 카드가 3개 미만이라 추리 역할이 분명한 기본 용의자 카드로 보강했습니다.");
        }
        draft.setSuspects(suspects);
    }

    private void ensureMissionEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = draft.getEvidences() == null ? new ArrayList<>() : new ArrayList<>(draft.getEvidences());
        int targetCount = request.getPlaces().size();
        for (int i = evidences.size(); i < targetCount; i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            String evidenceType = defaultEvidenceType(i);
            evidences.add(AiEpisodeDraftResponse.EvidenceDraft.builder()
                    .title(defaultEvidenceTitle(i + 1))
                    .type(evidenceType)
                    .imageUrl(generatedEvidenceImage(evidenceType))
                    .textSummary(defaultEvidenceSummary(i + 1, place))
                    .sourceMissionOrder(i + 1)
                    .build());
        }
        if (draft.getEvidences() == null || draft.getEvidences().size() < targetCount) {
            warnings.add("Draft normalization changed a field; review before publishing.");
        }
        draft.setEvidences(evidences);
    }

    private boolean containsAny(String text, String... targets) {
        if (text == null) {
            return false;
        }
        String source = compact(text);
        for (String target : targets) {
            if (source.contains(compact(target))) {
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
        if ("SITE_DATA_REVIEW_REQUIRED".equals(code) && allMissionsAreReviewPlaceholders(draft)) {
            return true;
        }
        if (finding.getMissionOrder() == null) {
            return false;
        }
        if (finding.getMissionOrder() <= 0 || finding.getMissionOrder() > draft.getMissions().size()) {
            return false;
        }
        AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(finding.getMissionOrder() - 1);
        if (List.of("INSUFFICIENT_PUZZLE_DATA", "PLACEHOLDER_PUZZLE").contains(code) && isReviewPlaceholder(mission)) {
            return true;
        }
        if ("CLUE_USES_PLACE_NAME_TEXT_EXTRACTION".equals(code)
                && (isGenericRewardKey(compact(mission.getRewardClue())) || isReviewPlaceholder(mission))) {
            return true;
        }
        if ("FINAL_PLACE_REVEALED_IN_STORY".equals(code) && !storyRevealsFinalPlace(mission)) {
            return true;
        }
        if (List.of("FINAL_PLACE_EXPOSED_PUBLICLY", "PUBLIC_MARKER_TYPE_FINAL_EXPOSED", "FINAL_PLACE_PUBLIC_MARKER_EXPOSED").contains(code)
                && Boolean.TRUE.equals(mission.getFinalPlace())
                && "DESTINATION_HINT".equals(normalize(mission.getPublicMarkerType()))) {
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

    private boolean allMissionsAreReviewPlaceholders(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return draft != null
                && draft.getMissions() != null
                && !draft.getMissions().isEmpty()
                && draft.getMissions().stream().allMatch(this::isReviewPlaceholder);
    }

    private boolean storyRevealsFinalPlace(AiEpisodeDraftResponse.MissionDraft mission) {
        if (mission == null) {
            return false;
        }
        return containsFinalRevealTerms(mission.getStoryText())
                || (Boolean.TRUE.equals(mission.getFinalPlace()) && textContains(mission.getStoryText(), mission.getPlaceName()));
    }

    private boolean containsFinalRevealTerms(String text) {
        return containsAny(text,
                "최종 장소", "최종장소", "최종 목적지", "최종목적지", "마지막 장소", "마지막장소",
                "정답 장소", "정답장소", "최종 추리", "최종추리", "final place", "final destination",
                "answer location");
    }

    private boolean isReviewPlaceholder(AiEpisodeDraftResponse.MissionDraft mission) {
        return mission != null && ("review-required".equalsIgnoreCase(mission.getAnswer()) || compact(mission.getQuestionText()).contains("field review"));
    }


    private String defaultEvidenceType(int index) {
        return switch (index % 3) {
            case 0 -> "PHOTO";
            case 1 -> "MEMO";
            default -> "NOTE";
        };
    }

    private String generatedEvidenceImage(String type) {
        return switch (normalize(type)) {
            case "PHOTO" -> "/generated-case-card-photo.svg";
            case "MEMO", "POST_IT" -> "/generated-case-card-memo.svg";
            case "DOCUMENT", "EVIDENCE", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE" ->
                    "/generated-case-card-document.svg";
            case "SUSPECT_CLUE" -> "/generated-case-card-suspect.svg";
            default -> "/generated-case-card-note.svg";
        };
    }

    private String defaultEvidenceTitle(int order) {
        return switch (order) {
            case 1 -> "first field photo envelope";
            case 2 -> "torn route memo";
            case 3 -> "conflicting witness note";
            case 4 -> "lens fragment record";
            case 5 -> "red seal sketch";
            case 6 -> "destination cipher memo";
            case 7 -> "final route log";
            case 8 -> "sealed name card";
            default -> "final deduction support file";
        };
    }


    private String defaultEvidenceSummary(int order, AiEpisodeDraftRequest.PlaceInput place) {
        String name = place == null || blank(place.getName()) ? "this spot" : place.getName();
        return switch (order) {
            case 1 -> name + " marks the opening point of the case.";
            case 2 -> name + " links route movement with a missing trace.";
            case 3 -> "A witness record that exposes a contradiction.";
            case 4 -> "Evidence that narrows the nature of the final object.";
            case 5 -> "A clue connecting suspect motive to the case.";
            case 6 -> "A memo that narrows the destination without naming it.";
            case 7 -> "A log for reconstructing the final movement path.";
            case 8 -> "A sealed file to check before final deduction.";
            default -> "Support material for combining collected clues.";
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
        if (blank(answer)) return false;
        String compact = answer.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return List.of("history", "truth", "death", "record", "reveal truth").contains(compact);
    }


    private boolean containsBlockedHistoricalName(String text) {
        if (blank(text)) {
            return false;
        }
        String compactText = compact(text);
        return BLOCKED_HISTORICAL_NAMES.stream()
                .map(this::compact)
                .anyMatch(compactText::contains);
    }

    private boolean looksLikeRealNameCulprit(String answer) {
        if (blank(answer)) return false;
        boolean hasCulpritContext = containsAny(answer, "secretary", "assistant", "aide", "courier", "agent", "official", "delivery", "traitor", "culprit");
        boolean hasLatinFullName = answer.matches(".*[A-Z][a-z]+\\s+[A-Z][a-z]+.*");
        boolean hasHangulNameShape = answer.codePoints().filter(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3).count() >= 2;
        boolean hasPersonNameShape = hasLatinFullName || hasHangulNameShape;
        return hasCulpritContext && hasPersonNameShape;
    }


    private boolean textContains(String text, String target) {
        if (blank(text) || blank(target)) {
            return false;
        }
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                .contains(target.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
    }

    private boolean lacksProvidedNumber(AiEpisodeDraftRequest sourceInput, int missionOrder) {
        if (sourceInput == null || sourceInput.getPlaces() == null || missionOrder <= 0 || missionOrder > sourceInput.getPlaces().size()) {
            return true;
        }
        AiEpisodeDraftRequest.PlaceInput place = sourceInput.getPlaces().get(missionOrder - 1);
        return place.getNumbers() == null || place.getNumbers().isEmpty();
    }

    private AiEpisodeDraftRequest.PlaceInput sourcePlace(AiEpisodeDraftRequest sourceInput, int missionOrder) {
        if (sourceInput == null || sourceInput.getPlaces() == null || missionOrder <= 0 || missionOrder > sourceInput.getPlaces().size()) {
            return null;
        }
        return sourceInput.getPlaces().get(missionOrder - 1);
    }
}
