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
    private static final Set<String> BLOCKED_HISTORICAL_NAMES = Set.of(
            "고종", "순종", "명성황후", "이완용", "박제순", "이지용", "이근택", "권중현", "을사오적",
            "안중근", "윤봉길", "유관순", "김구", "이토 히로부미", "이토히로부미"
    );

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
                - If provided field data is too weak for a real observation puzzle, create a playable STORY_COMBINATION or PATTERN puzzle using keywords/description/adminMemo. Use answer "검수필요" only when there is no usable keyword, description, adminMemo, visibleElement, or number.
                - Do not mark every mission as placeholder just because on-site inspection is still required. If RAG/site enrichment narrowed the verification scope, create a playable draft puzzle and state the verification basis in groundRule.
                - Do not make a real historical person the culprit.
                - Prefer finalAnswerType EVIDENCE, WEAPON, HIDDEN_DOCUMENT, SECRET_KEYWORD, or HIDDEN_TRUTH. Use CULPRIT only when the answer is a fictional title/role, never a real-name-like person.
                - Never include real historical names such as 고종, 이완용, 을사오적, 안중근, 김구, 이토 히로부미 in finalAnswer, suspect names, culprit labels, or answer aliases.
                - Do not make finalAnswer a construction like "real historical person + fictional assistant/secretary". That still exposes real history as culprit context.
                - Do not present a real historical event as a distorted fact.
                - The final answer must be a clear noun phrase inside the fictional case, not a place name, real person, real event, verb, sentence, or abstract single word.
                - The real final place is an internal role only. Public marker for that place must be FINAL_CANDIDATE.
                - A final-place mission story must read like a normal investigation candidate. It must not say or imply "this is the final place", "final deduction starts here", or "the answer location".
                - Do not describe the real final place as the final destination in storyText, rewardClue, destination hints, finalTruthSummary, actualHistorySummary, or deductionSecretFacts.
                - At least one non-final destination candidate should also use publicMarkerType FINAL_CANDIDATE so the real final place is hidden among candidates.
                - Create 4 answer clues when enough ANSWER_HINT places exist, and 2 destination clues when enough DESTINATION_HINT places exist.
                - Make the draft feel like a real printed detective kit: each mission must reveal a different document, photo, memo, suspect contradiction, route note, or cipher card.
                - Suspects must have concrete fictional names/titles, relationToVictim, suspiciousPoint, and alibiSummary. Do not return generic "AI draft" or "admin review" suspect text.
                - Evidences must have specific case-file titles and summaries tied to mission rewardClue. Do not return generic "case sketch", "draft card", or "admin review" evidence text.
                - Every mission puzzle must have a clear question, answer, three hints, and a rewardClue that advances either the final answer or destination inference.
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
                  "suspects": [{"alias":"string","displayName":"string","portraitImageUrl":"","shortDescription":"string","relationToVictim":"string","suspiciousPoint":"string","alibiSummary":"string"}],
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
        if (containsBlockedHistoricalName(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ANSWER", "최종 정답에는 실존 역사 인물명이나 실제 역사 인물을 범인처럼 보이게 하는 표현을 사용할 수 없습니다.", null);
        }
        if ("CULPRIT".equals(normalize(draft.getFinalAnswerType())) && looksLikeRealNameCulprit(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_NAME_LIKE_CULPRIT", "CULPRIT 정답은 실명형 인물이 아니라 '검은 봉투의 전달자'처럼 픽션 역할/별칭으로 작성해야 합니다.", null);
        }
        if (draft.getFinalAnswerAliases() != null && draft.getFinalAnswerAliases().stream().anyMatch(this::containsBlockedHistoricalName)) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ALIAS", "정답 alias에도 실존 역사 인물명을 사용할 수 없습니다.", null);
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
                  - If RAG/site enrichment provides keywords, description, visibleElements, or adminMemo, do not demand a placeholder; allow playable draft puzzles with a groundRule that says admin verification is required.
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
            warnings.add("최종 정답 유형이 허용값이 아니어서 HIDDEN_TRUTH로 보정되었습니다.");
        } else {
            draft.setFinalAnswerType(normalize(draft.getFinalAnswerType()));
        }
        if (containsBlockedHistoricalName(draft.getFinalAnswer())
                || ("CULPRIT".equals(normalize(draft.getFinalAnswerType())) && looksLikeRealNameCulprit(draft.getFinalAnswer()))) {
            draft.setFinalAnswerType("HIDDEN_DOCUMENT");
            draft.setFinalAnswer("봉인된 사진 봉투");
            draft.setFinalAnswerAliases(new ArrayList<>(List.of("봉인된사진봉투", "사진 봉투", "봉인된 봉투")));
            draft.setFinalQuestion("피해자가 마지막까지 숨기려 한 사건의 핵심 증거는 무엇인가?");
            draft.setFinalTruthSummary("수집한 단서들은 범인의 이름보다 먼저 사라진 기록물 하나를 가리킨다. 봉인된 사진 봉투 안에는 사건 당일의 이동 경로와 조작된 증언이 함께 남아 있었다.");
            draft.setDeductionSecretFacts(new ArrayList<>(List.of(
                    "최종 정답은 실존 인물이나 장소명이 아니라 픽션 사건 안의 숨겨진 문서다.",
                    "봉인된 사진 봉투는 사진 기록과 목격자 진술을 함께 묶은 핵심 증거다.",
                    "정답 힌트는 봉인, 사진, 봉투, 조작된 기록이라는 방향으로 조합된다."
            )));
            warnings.add("최종 정답이 실존 역사 인물 또는 실명형 범인처럼 보여 HIDDEN_DOCUMENT 정답으로 자동 보정했습니다.");
        }
        if (blank(draft.getSubtitle())) {
            draft.setSubtitle(defaultSubtitle(draft, request));
            warnings.add("부제가 비어 있어 선택한 루트 정보를 기준으로 자동 생성했습니다.");
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
                warnings.add("최종 정답이 실제 장소명과 같아 저장 전 수정이 필요합니다.");
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
            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType())) && !hasProvidedNumber(request, i + 1)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("미션 " + (i + 1) + "의 NUMBER_LOCK은 제공 숫자가 없어 스토리 결합형 퍼즐로 자동 보정했습니다.");
            }
            if (usesPlaceNameTextPuzzle(mission, place) || usesWeakTextExtractionPuzzle(mission) || shouldUseAdminReviewPuzzle(mission, place)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("미션 " + (i + 1) + "의 글자 추출/상상 현장 요소 기반 문제를 스토리 결합형 퍼즐로 자동 보정했습니다.");
            }
            if (blank(mission.getAnswer())) {
                mission.setAnswer(fallbackAnswer(place));
                warnings.add("미션 " + (i + 1) + "의 정답이 비어 있어 제공된 현장 데이터에서 임시 정답을 채웠습니다.");
            }
            if (blank(mission.getRewardClue())) {
                mission.setRewardClue(fallbackReward(role, i));
                warnings.add("미션 " + (i + 1) + "의 보상 단서가 비어 있어 사건 단서 문구를 채웠습니다.");
            }
            sanitizePlaceNameDependentReward(mission, request, role, i, warnings);
            sanitizeForbiddenRevealReward(mission, draft, role, i, warnings);
            sanitizeFinalAnswerLeaks(draft, mission, role, i, warnings);
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                warnings.add("미션 " + (i + 1) + "의 힌트가 3개 미만입니다. 관리자 편집에서 보강하세요.");
            }
            if (blank(mission.getGroundRule())) {
                mission.setGroundRule("Uses only provided visibleElements, numbers, keywords, description, and adminMemo.");
            }
        }
        if (!finalExists) {
            warnings.add("FINAL 역할 장소가 없습니다. 마지막 장소를 내부 최종 장소로 지정할지 검토하세요.");
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
            warnings.add("Gemini가 선택 장소보다 많은 미션을 반환해 초과 미션을 제거했습니다.");
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
            warnings.add("선택한 장소마다 미션이 1개씩 매칭되도록 미션 수를 보정했습니다.");
        }
        draft.setMissions(missions);
    }

    private String defaultSubtitle(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        String start = places.isEmpty() ? "첫 단서" : places.get(0).getName();
        String genre = blank(draft.getGenre()) ? "사건파일" : draft.getGenre();
        return start + "에서 시작되는 " + genre + " 현장 조사";
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
            case "ANSWER_HINT" -> List.of("깨진 흔적", "렌즈의 곡면", "차가운 유리", "반사된 그림자").get(Math.min(index, 3));
            case "DESTINATION_HINT", "FINAL_CANDIDATE", "FINAL" -> index % 2 == 0 ? "붉은 벽의 침묵" : "기록이 닫힌 문";
            default -> List.of("마지막 사진", "봉인된 봉투", "어긋난 진술", "사라진 시간").get(Math.min(index % 4, 3));
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
            boolean genericRevealPhrase = containsAny(reveal, "사건의 진범", "진범", "정답 장소", "최종 목적지");
            if (isPlaceName || finalPlaceRevealPhrase || genericRevealPhrase) {
                warnings.add("deductionForbiddenReveals에서 실제 장소나 최종 정답을 유추할 수 있는 항목 '" + reveal + "'을 제거했습니다.");
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
            mission.setStoryText("이 장소는 여러 단서가 겹치는 조사 후보지입니다. 현장에서는 사건 메모와 주변 분위기만 조용히 확인하세요.");
            warnings.add("미션 " + (index + 1) + "의 스토리에서 내부 최종 장소를 암시하는 표현을 제거했습니다.");
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
            warnings.add("미션 " + (index + 1) + "의 보상 단서가 장소명 글자 추출에 의존해 안전한 단서 키로 교체되었습니다.");
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
            warnings.add("미션 " + (index + 1) + "의 보상 단서가 최종 추리 금지어와 충돌해 안전한 단서 키로 교체되었습니다.");
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
        if (compactValue.length() < 3) {
            return false;
        }
        if (compactPlace.contains(compactValue) || compactValue.contains(compactPlace)) {
            return true;
        }
        String normalizedPlace = compactPlace
                .replace("제일", "")
                .replace("본점", "")
                .replace("공원", "")
                .replace("박물관", "")
                .replace("역사", "");
        String normalizedValue = compactValue
                .replace("제일", "")
                .replace("본점", "")
                .replace("공원", "")
                .replace("박물관", "")
                .replace("역사", "");
        boolean likelyPlaceAlias = compactValue.length() >= 3
                && hasLocationSuffix(compactValue)
                && compactPlace.length() >= 2
                && compactValue.startsWith(compactPlace.substring(0, Math.min(2, compactPlace.length())));
        if (likelyPlaceAlias) {
            return true;
        }
        return normalizedPlace.length() >= 3
                && normalizedValue.length() >= 3
                && (normalizedPlace.contains(normalizedValue) || normalizedValue.contains(normalizedPlace));
    }

    private boolean hasLocationSuffix(String value) {
        return value.endsWith("교회")
                || value.endsWith("궁")
                || value.endsWith("공원")
                || value.endsWith("박물관")
                || value.endsWith("미술관")
                || value.endsWith("극장")
                || value.endsWith("공사관")
                || value.endsWith("터")
                || value.endsWith("장");
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
        String question = compact(mission.getQuestionText());
        return containsAny(question,
                "알수없는기호", "특이한표식", "오래된그림", "조형물의특징", "표지판에적힌글귀",
                "특별한장식물", "작은상자", "현장간판", "계단수", "벽화");
    }

    private boolean usesWeakTextExtractionPuzzle(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = compact(String.join(" ",
                blank(mission.getQuestionText()) ? "" : mission.getQuestionText(),
                blank(mission.getAnswer()) ? "" : mission.getAnswer(),
                mission.getHints() == null ? "" : String.join(" ", mission.getHints())));
        return containsAny(text,
                "글자수", "몇글자", "단어수", "첫글자", "마지막글자", "초성만", "세단어",
                "네단어", "순서대로조합", "글자를순서대로", "글자수를순서대로");
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
        if (!textContains(mission.getStoryText(), basis)) {
            String story = blank(mission.getStoryText()) ? "이 장소는 사건 메모를 확인하는 조사 지점입니다." : mission.getStoryText();
            mission.setStoryText(story + " 검수 대상 키워드: " + basis + ".");
        }
        mission.setPuzzleType("STORY_COMBINATION");
        mission.setQuestionText("사건파일의 메모와 이 장소의 검수 대상 키워드 '" + basis + "'를 대조하세요. 이 지점에서 확인해야 할 단서 키워드를 입력하세요.");
        mission.setAnswer(basis);
        mission.setAnswerFormat("TEXT");
        mission.setRewardClue(reward);
        mission.setHints(List.of(
                "장소명 글자 수가 아니라 사건파일 메모와 현장 검수 키워드의 의미를 비교하세요.",
                "이 장소는 '" + markerRoleLabel(role) + "' 역할입니다. 얻을 단서가 정답 단서인지 목적지 단서인지 먼저 구분하세요.",
                "정답은 이 미션을 풀면 얻는 단서 문구입니다. 공개 전 관리자가 실제 현장 근거로 문항을 더 구체화해야 합니다."
        ));
        mission.setGroundRule("RAG/관리자 입력 기반 검수 대상 키워드 '" + basis + "'만 사용했습니다. 실제 간판, 숫자, 조형물은 운영 전 현장 확인 후 확정해야 합니다.");
    }

    private String bestPuzzleBasis(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null) {
            return "사건 메모";
        }
        if (place.getKeywords() != null && !place.getKeywords().isEmpty()) {
            return place.getKeywords().stream().filter(value -> !blank(value)).findFirst().orElse("사건 메모");
        }
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) {
            return place.getVisibleElements().stream().filter(value -> !blank(value)).findFirst().orElse("사건 메모");
        }
        if (!blank(place.getDescription())) {
            return "장소 설명";
        }
        if (!blank(place.getAdminMemo())) {
            return "관리자 메모";
        }
        return "사건 메모";
    }

    private String markerRoleLabel(String role) {
        return switch (role) {
            case "ANSWER_HINT" -> "최종 정답 힌트";
            case "DESTINATION_HINT", "FINAL_CANDIDATE", "FINAL" -> "목적지 힌트";
            case "START" -> "시작 단서";
            default -> "스토리 단서";
        };
    }

    private boolean hasWeakFieldData(AiEpisodeDraftRequest.PlaceInput place) {
        boolean hasRealVisibleElement = place.getVisibleElements() != null && place.getVisibleElements().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::compact)
                .anyMatch(value -> !value.contains("관리자현장메모필요") && !value.contains("현장검수필요"));
        boolean hasNumber = place.getNumbers() != null && !place.getNumbers().isEmpty();
        boolean hasKeyword = place.getKeywords() != null && place.getKeywords().stream().anyMatch(value -> value != null && !value.isBlank());
        boolean hasDescription = !blank(place.getDescription()) && !compact(place.getDescription()).contains("selectedoperationspot");
        boolean hasAdminMemo = !blank(place.getAdminMemo()) && !compact(place.getAdminMemo()).contains("운영공개전검수");
        return !hasRealVisibleElement && !hasNumber && !hasKeyword && !hasDescription && !hasAdminMemo;
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
        if (textContains(mission.getQuestionText(), draft.getFinalAnswer()) || containsFinalAnswerAlias(mission.getQuestionText(), draft)) {
            mission.setQuestionText(safeQuestionText(role));
            warnings.add("미션 " + (index + 1) + "의 퍼즐 질문이 최종 정답을 직접 노출해 안전한 질문으로 교체했습니다.");
        }
        if (textContains(mission.getRewardClue(), draft.getFinalAnswer())) {
            mission.setRewardClue(fallbackReward(role, index));
            warnings.add("미션 " + (index + 1) + "의 보상 단서가 최종 정답을 직접 포함해 안전한 부분 단서로 교체했습니다.");
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
            warnings.add("미션 " + (index + 1) + "의 힌트가 정답을 직접 노출해 안전한 힌트로 교체했습니다.");
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
        if ("FINAL".equals(role) || "FINAL_CANDIDATE".equals(role)) {
            return "수집한 단서와 질문 기록을 바탕으로 사건의 핵심 진실을 추리해 입력하세요. 정답은 클리어 전까지 직접 노출하지 않습니다.";
        }
        return "이 장소의 실제 관찰 요소와 사건 메모를 연결해 단서 키워드를 입력하세요. 최종 정답 자체를 쓰는 문제는 허용되지 않습니다.";
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
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("용의자 A")
                        .displayName("검은 우산을 든 의뢰인")
                        .shortDescription("사건 직전 피해자에게 첫 봉투를 건넨 가상 인물입니다.")
                        .relationToVictim("사건 의뢰를 가장 먼저 전달한 인물")
                        .suspiciousPoint("피해자가 사라진 시간대에 조사 경로 근처에서 반복적으로 목격되었습니다.")
                        .alibiSummary("비가 오기 전 카페 골목에 있었다고 주장하지만 목적지 힌트와 동선이 일부 겹칩니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("용의자 B")
                        .displayName("사라진 필름의 조수")
                        .shortDescription("피해자의 기록과 사진 순서를 알고 있던 가상 조수입니다.")
                        .relationToVictim("피해자의 기록 정리를 맡았던 조수")
                        .suspiciousPoint("사진과 메모의 순서를 알고 있어 단서를 바꿔치기할 수 있는 위치에 있었습니다.")
                        .alibiSummary("자료실에 있었다고 말하지만 정답 힌트 단서 중 하나가 그의 진술과 충돌합니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("용의자 C")
                        .displayName("회색 봉투의 전달자")
                        .shortDescription("마지막 문서를 전달한 익명의 가상 중개인입니다.")
                        .relationToVictim("마지막 문서를 전달한 중개인")
                        .suspiciousPoint("목적지 힌트 두 개가 모두 이 인물의 이동 방향을 가리킵니다.")
                        .alibiSummary("봉투만 전달했다고 주장하지만 봉투 안쪽에 사건의 핵심 단어가 남아 있습니다.")
                        .build()
        );
        for (AiEpisodeDraftResponse.SuspectDraft fallback : defaults) {
            if (suspects.size() >= 3) {
                break;
            }
            suspects.add(fallback);
        }
        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            warnings.add("용의자 카드가 3개 미만이라 가상 용의자 카드로 보강했습니다.");
        }
        draft.setSuspects(suspects);
    }

    private void ensureMissionEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = draft.getEvidences() == null ? new ArrayList<>() : new ArrayList<>(draft.getEvidences());
        int targetCount = request.getPlaces().size();
        for (int i = evidences.size(); i < targetCount; i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            evidences.add(AiEpisodeDraftResponse.EvidenceDraft.builder()
                    .title(defaultEvidenceTitle(i + 1))
                    .type(i % 3 == 0 ? "PHOTO" : i % 3 == 1 ? "MEMO" : "NOTE")
                    .imageUrl(generatedEvidenceImage(i % 3 == 0 ? "PHOTO" : i % 3 == 1 ? "MEMO" : "NOTE"))
                    .textSummary(defaultEvidenceSummary(i + 1, place))
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
        if (List.of("FINAL_PLACE_EXPOSED_PUBLICLY", "PUBLIC_MARKER_TYPE_FINAL_EXPOSED", "FINAL_PLACE_PUBLIC_MARKER_EXPOSED").contains(code)
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
        return containsFinalRevealTerms(mission.getStoryText())
                || (Boolean.TRUE.equals(mission.getFinalPlace()) && textContains(mission.getStoryText(), mission.getPlaceName()));
    }

    private boolean containsFinalRevealTerms(String text) {
        return containsAny(text,
                "최종 장소", "최종장소", "최종 목적지", "최종목적지", "마지막 장소", "마지막장소",
                "정답 장소", "정답장소", "최종 추리", "최종추리", "final place", "final destination",
                "answer location");
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

    private String defaultEvidenceTitle(int order) {
        return switch (order) {
            case 1 -> "첫 현장 사진 봉투";
            case 2 -> "찢어진 이동 메모";
            case 3 -> "흐릿한 목격 진술";
            case 4 -> "렌즈 파편 기록";
            case 5 -> "붉은 인장 스케치";
            case 6 -> "목적지 암호 메모";
            case 7 -> "마지막 동선 기록";
            case 8 -> "봉인된 필름 카드";
            default -> "최종 추리 보조 자료";
        };
    }

    private String defaultEvidenceSummary(int order, AiEpisodeDraftRequest.PlaceInput place) {
        String name = place == null || blank(place.getName()) ? "이 장소" : place.getName();
        return switch (order) {
            case 1 -> name + "에서 사건의 시작점을 확인하게 하는 현장 기록입니다.";
            case 2 -> name + " 주변 동선과 누락된 흔적을 이어 주는 메모입니다.";
            case 3 -> "서로 맞지 않는 진술을 비교하게 만드는 목격 기록입니다.";
            case 4 -> "최종 정답의 물성에 접근하게 하는 증거 카드입니다.";
            case 5 -> "용의자의 상징과 사건 동기를 연결하는 단서입니다.";
            case 6 -> "최종 목적지를 직접 말하지 않고 방향을 좁히는 암호 메모입니다.";
            case 7 -> "마지막 이동 경로를 재구성하게 하는 조사 기록입니다.";
            case 8 -> "최종 추리 직전 확인해야 하는 봉인 자료입니다.";
            default -> "수집한 단서를 조합해 최종 질문에 접근하게 하는 보조 자료입니다.";
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
        if (blank(answer)) {
            return false;
        }
        String compactAnswer = compact(answer);
        boolean hasCulpritContext = containsAny(answer, "비서", "조수", "측근", "후손", "자객", "관료", "밀정", "매국노", "범인");
        boolean hasKoreanFullName = answer.matches(".*[가-힣]{2,4}\\s+[가-힣]{2,4}.*")
                || answer.matches(".*[가-힣]{2,4},\\s*[가-힣]{2,4}.*")
                || compactAnswer.matches(".*[가-힣]{2,4}(김|이|박|최|정|조|윤|장|임|한|오|서|신|권|황|안|송|류|홍|고|문|양|손|배|백|허|유)[가-힣]{1,3}.*");
        return hasCulpritContext && hasKoreanFullName;
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
