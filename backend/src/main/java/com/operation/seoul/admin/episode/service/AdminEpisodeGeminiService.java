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
                .message("Gemini가 관리자 검수용 스토리 미션 초안을 생성했습니다. 아직 DB에 저장되지 않았습니다.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of(
                        "최종 정답, 별칭, 노출 금지어를 검수하세요.",
                        "각 퍼즐이 선택 장소 데이터, 현장 메모, 사이트 보강 정보, 또는 안전한 픽션 단서에 근거하는지 확인하세요.",
                        "저장 전 퍼즐, 힌트, reward_payload, 관계자 카드, 해금 자료 카드를 수정하세요.",
                        "먼저 DRAFT로 저장한 뒤 공개 검증을 통과하면 PUBLISHED로 변경하세요."
                ))
                .build();
    }

    public AiEpisodePlanResponse createAnswerPlan(AiEpisodeDraftRequest request) {
        validateRequest(request);
        ensureApiKey();

        String prompt = buildPlanPrompt(request);
        String json = callGemini(prompt);

        try {
            JsonNode root = objectMapper.readTree(json);

            String genreName = root.path("selectedGenreName").asText(
                    root.path("selectedGenre").asText("야외 스토리 미션")
            );

            String genreId = root.path("selectedGenreId").asText("");
            if (blank(genreId)) {
                genreId = resolveGenreIdFromCatalog(request, genreName);
            }

            List<AiEpisodePlanResponse.AnswerKeyword> keywords =
                    parsePlanKeywords(root.path("finalAnswerKeywords"));

            if (keywords.size() < 2) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "GEMINI_PLAN_SCHEMA_INVALID",
                        "Gemini 정답 계획에는 최소 2개 이상의 최종 정답 키워드가 필요합니다."
                );
            }

            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots =
                    parsePlanAnswerSlots(root.path("answerSlots"));

            if (answerSlots.isEmpty()) {
                answerSlots = keywords.stream()
                        .map(keyword -> AiEpisodePlanResponse.AnswerSlotPlan.builder()
                                .slotId(blank(keyword.getSlotId()) ? keyword.getLabel() : keyword.getSlotId())
                                .label(keyword.getLabel())
                                .description("")
                                .minClueCount(2)
                                .build())
                        .toList();
            }

            return AiEpisodePlanResponse.builder()
                    .selectedGenreId(blank(genreId) ? "CUSTOM" : genreId)
                    .selectedGenreName(blank(genreName) ? "야외 스토리 미션" : genreName)
                    .answerSlots(answerSlots)
                    .finalAnswerKeywords(keywords)
                    .finalQuestionGuide(root.path("finalQuestionGuide").asText(""))
                    .rationale(root.path("rationale").asText(""))
                    .rejectedGenreReasons(readStringArray(root.path("rejectedGenreReasons")))
                    .validationWarnings(readStringArray(root.path("validationWarnings")))
                    .nextSteps(List.of(
                            "관리자가 장르와 정답 키워드를 확인합니다.",
                            "확인 후 이 키워드 전체가 포함되도록 Gemini 전체 초안을 생성합니다."
                    ))
                    .build();

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GEMINI_PLAN_PARSE_FAILED",
                    "Gemini 장르/정답 키워드 계획을 해석할 수 없습니다."
            );
        }
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> parsePlanKeywords(JsonNode keywordsNode) {
        List<AiEpisodePlanResponse.AnswerKeyword> keywords = new ArrayList<>();

        if (keywordsNode == null || !keywordsNode.isArray()) {
            return keywords;
        }

        for (JsonNode node : keywordsNode) {
            String label = node.path("label").asText("");
            String keyword = normalizeAnswerKeywordValue(node.path("keyword").asText(""));

            boolean duplicate = keywords.stream()
                    .anyMatch(existing -> same(existing.getKeyword(), keyword));

            if (!blank(label) && !blank(keyword) && !duplicate) {
                String slotId = node.path("slotId").asText("");
                if (blank(slotId)) {
                    slotId = label.trim();
                }

                keywords.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                        .slotId(slotId.trim())
                        .label(label.trim())
                        .keyword(keyword.trim())
                        .aliases(readStringArray(node.path("aliases")))
                        .sourcePlaceOrder(node.path("sourcePlaceOrder").isNumber()
                                ? node.path("sourcePlaceOrder").asInt()
                                : null)
                        .sourceBasis(node.path("sourceBasis").asText(""))
                        .difficulty(node.path("difficulty").asText("NORMAL"))
                        .risk(node.path("risk").asText("OK"))
                        .build());
            }
        }

        return keywords;
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> parsePlanAnswerSlots(JsonNode slotsNode) {
        List<AiEpisodePlanResponse.AnswerSlotPlan> slots = new ArrayList<>();

        if (slotsNode == null || !slotsNode.isArray()) {
            return slots;
        }

        for (JsonNode node : slotsNode) {
            String label = node.path("label").asText("");
            String slotId = node.path("slotId").asText(label);

            if (!blank(label)) {
                slots.add(AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId(blank(slotId) ? label.trim() : slotId.trim())
                        .label(label.trim())
                        .description(node.path("description").asText(""))
                        .minClueCount(node.path("minClueCount").isNumber()
                                ? node.path("minClueCount").asInt()
                                : 2)
                        .build());
            }
        }

        return slots;
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();

        for (JsonNode item : node) {
            String value = item.asText("");
            if (!blank(value)) {
                values.add(value.trim());
            }
        }

        return values;
    }

    private String resolveGenreIdFromCatalog(AiEpisodeDraftRequest request, String genreName) {
        if (request == null || request.getGenreCatalog() == null || request.getGenreCatalog().isEmpty()) {
            return "CUSTOM";
        }

        return request.getGenreCatalog().stream()
                .filter(genre -> genre != null && !blank(genre.getGenreName()))
                .filter(genre -> same(genre.getGenreName(), genreName))
                .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreId)
                .filter(value -> !blank(value))
                .findFirst()
                .orElse("CUSTOM");
    }

    public AiEpisodeDraftValidationResponse validateDraft(AiEpisodeDraftValidationRequest request) {
        if (request == null || request.getDraft() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DRAFT_VALIDATION_INPUT",
                    "검증할 AI 초안 데이터가 필요합니다."
            );
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
                        ? "AI 초안이 필수 검증 항목을 통과했습니다."
                        : "AI 초안에 차단 이슈가 있습니다. 저장 또는 공개 전 필수 수정 항목을 처리하세요.")
                .findings(findings)
                .requiredFixes(requiredFixes)
                .publishChecklist(List.of(
                        "모든 장소 좌표와 도착 반경이 선택 장소 데이터 또는 현장 QA 기준으로 맞는지 확인하세요.",
                        "모든 퍼즐이 제공된 장소 데이터, 관리자 메모, 사이트 보강 정보, 또는 안전한 픽션 단서에 근거하는지 확인하세요.",
                        "최종 정답이 실제 장소명, 실존 인물명, 실제 사건명을 그대로 사용하지 않는지 확인하세요.",
                        "지도 API에는 publicMarkerType만 노출하고 내부 finalPlace는 절대 노출하지 마세요.",
                        "생성된 초안은 먼저 DRAFT로 저장하고, 차단 이슈가 모두 해결된 뒤 공개하세요."
                ))
                .build();
    }

    private void validateRequest(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AI_DRAFT_INPUT",
                    "AI 에피소드 초안을 생성하려면 최소 1개 이상의 장소 입력이 필요합니다."
            );
        }

        AiEpisodeDraftRequest.MissionPolicyInput policy = request.getMissionPolicy();

        int min = policy != null && policy.getMinMissionCount() != null
                ? policy.getMinMissionCount()
                : 3;

        int max = policy != null && policy.getMaxMissionCount() != null
                ? policy.getMaxMissionCount()
                : 30;

        if (request.getPlaces().size() < min) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AI_DRAFT_INPUT",
                    "현재 미션 정책에서는 최소 " + min + "개 이상의 장소가 필요합니다."
            );
        }

        if (request.getPlaces().size() > max) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AI_DRAFT_INPUT",
                    "현재 미션 정책에서는 최대 " + max + "개 장소까지만 사용할 수 있습니다."
            );
        }

        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            if (blank(place.getName()) || place.getLatitude() == null || place.getLongitude() == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PLACE_INPUT",
                        "선택한 모든 장소에는 장소명, 위도, 경도가 필요합니다."
                );
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
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI_INPUT_SERIALIZE_FAILED",
                    "AI 초안 생성 입력값을 JSON으로 변환할 수 없습니다."
            );
        }

        return """
                You are an outdoor story-mission and clue-route content designer for Operation Korea.
                Return JSON only. Do not wrap it in markdown.
                Write all player-facing story, puzzle, clue, hint, character, and evidence text in Korean.
                
                Core role:
                - Create a full outdoor escape-room style episode draft from the admin-approved genre and answer plan.
                - The episode must be playable as a fictional story mission based on field clues, movement, observation, and deduction.
                - The draft will be reviewed by an admin before saving or publishing.
                - Do not force the story into crime, murder, detective-noir, culprit-reveal, or weapon-reveal format unless the selected genre explicitly requires it.
            
            Source of truth:
            - Use only the provided input JSON.
            - Treat input.places, visibleElements, numbers, keywords, description, adminMemo, role, and publicMarkerType as source material.
            - Do not invent real signs, plaque text, numbers, stairs, murals, statues, access rules, or photo-verifiable objects.
            - Do not use outside facts unless they are explicitly present in the input.
            
            Admin-approved genre and answer contract:
            - input.selectedGenreId and input.selectedGenreName are admin-approved if present.
            - input.finalAnswerKeywordItems are admin-approved if present.
            - If input.finalAnswerKeywordItems exists, you must use every item as a mandatory final answer component.
            - Each finalAnswerKeywordItem contains slotId, label, keyword, and aliases.
            - Do not rename, remove, merge, or replace approved slotIds, labels, or keywords.
            - finalAnswerKeywords in the output must contain the keyword values from input.finalAnswerKeywordItems.
            - finalAnswer must be one natural Korean sentence that includes every approved keyword value exactly or near-exactly.
            - finalAnswerAliases must include one item prefixed with "KW:" followed by all approved keyword values joined by "|".
            - Example: "KW:화공|붓|후원".
            - If legacy input.finalAnswerKeywords exists instead of finalAnswerKeywordItems, treat those values as mandatory final answer keywords.
            
            Final question rules:
            - finalQuestion must ask for the complete final answer implied by all approved slots.
            - finalQuestion must use slot labels or mystery roles, not exact keyword values.
            - Good: "정체, 핵심 물건, 마지막 조건을 종합하면 어떤 결론인가?"
            - Bad: "화공, 붓, 후원을 종합하면 무엇인가?"
            - Do not ask for only one keyword if multiple final answer keywords are approved.
            
            Keyword leak rules:
            - Do not reveal final answer keyword values in episodeTitle, subtitle, fictionSynopsis, or finalQuestion.
            - Exact final answer keyword values must remain hidden in every pre-clear player-facing field.
            - Character cards, evidence cards, rewardClue, storyText, puzzle questions, puzzle answers, and hints must use indirect descriptions that support inference without printing an exact keyword value.
            - Example: if an approved keyword is "봉인된 필름", do not print it before final submission. Use an indirect clue such as "빛에 약한 얇은 기록물", "사진 기록의 흔적", or "봉투 안에 숨겨진 매체".
            - episodeTitle, subtitle, fictionSynopsis, finalQuestion, questionText, answer, hints, storyText, rewardClue, character card text, and evidence card text must not contain an exact final answer keyword.
            
            Story contract:
            - First make a clear fictionSynopsis objective.
            - finalQuestion and finalAnswer must match that exact objective.
            - If the synopsis asks for identity + object + location, finalQuestion and finalAnswer must cover all three.
            - If the synopsis asks for hidden object + storage place + unlock condition, finalQuestion and finalAnswer must cover all three.
            - Do not let a MacGuffin object such as 설계도, 장부, 밀서, 기록, 문서 become the whole final answer unless the approved answer slots say so.
            - Prefer finalAnswerType HIDDEN_TRUTH when the final answer has multiple slots.
            
            Fiction Mode / Fact Mode:
            - Gameplay is Fiction Mode.
            - In Fiction Mode, player-facing text must feel like an agent operation, not a history lecture.
            - Do not directly explain the real historical motif during gameplay.
            - Do not make real historical people perpetrators, villains, false leads, character cards, or final answer values.
            - actualHistorySummary and finalTruthSummary are post-clear Fact Mode fields.
            - actualHistorySummary must include:
              "1. 모티브 공개"
              "2. 실제 배경 해설"
            - finalTruthSummary must include:
              "3. 픽션과 역사의 매칭 (디브리핑)"
             - finalTruthSummary must include at least 4 mapping lines:
               "스토리 속 [장치/지령/관계자/암호] -> 실제 배경 속 [관련 인물/배경/물건/장소 맥락]: [차용 및 각색 설명]"
            
            Mission policy:
            - Create exactly one mission for each provided place.
            - The output missions array length must equal input.places length.
            - Mission order N must use input.places[N - 1]. Do not merge places, skip places, or create extra missions.
            - Respect input.missionPolicy.startCount if present.
            - Respect input.missionPolicy.finalCount if present.
            - Respect input.missionPolicy.minCluesPerAnswerSlot if present.
            - Every approved answer slot must be supported by at least minCluesPerAnswerSlot separate rewardClues when possible.
            - START missions introduce the operation.
            - ANSWER_HINT missions narrow non-location answer slots.
            - DESTINATION_HINT missions narrow route, hideout, storage, or destination-related slots.
            - FINAL mission is internal only and ties all answer slots together.
            - publicMarkerType must never be FINAL.
            - If markerType is FINAL, publicMarkerType must be DESTINATION_HINT.
            - The final-place mission must not say or imply "final place", "final answer location", or "final deduction starts here".
            
            Puzzle policy:
            - Use input.puzzlePolicy if present.
            - If input.puzzlePolicy.allowedPuzzleTypes exists, use only those puzzle types.
            - Do not repeat the same puzzle mechanic more than input.puzzlePolicy.maxSamePuzzleTypeCount when present.
            - If input.puzzlePolicy.forbidPlaceNameTextExtraction is true, never use place-name letter extraction.
            - If input.puzzlePolicy.forbidFinalKeywordAsPuzzleAnswer is true, puzzle answer must not equal any final answer keyword.
            - If input.puzzlePolicy.requireUniquePuzzleAnswer is true, every mission.answer must be unique.
            - Do not use blockedGenericAnswers as mission.answer.
            
            Puzzle grounding:
            - NUMBER_LOCK puzzles may use only numbers listed in place.numbers.
            - OBSERVATION puzzles may use only visibleElements, keywords, description, and adminMemo.
            - STORY_COMBINATION and PATTERN puzzles may use provided keywords, description, adminMemo, and fictional story-mission logic
            - Never create puzzles that ask for first/second/nth/last letter, syllable, initial consonant, substring, or spelling extraction from a place name or business name.
            - Never use a place name or fragment of a place name as puzzle answer.
            - If field data is weak, create a STORY_COMBINATION puzzle using available keywords/description/adminMemo.
            - Use answer "검수필요" only when there is no usable number, keyword, visibleElement, description, or adminMemo.
            
            Mission output requirements:
            - Every mission must have order, placeName, address, latitude, longitude.
            - Every mission must have markerType, publicMarkerType, clueRole, and finalPlace.
            - Every mission must have storyText, puzzleType, questionText, answer, answerFormat, rewardClue, hints, and groundRule.
            - Every mission must have exactly 3 hints.
            - hints must become progressively clearer but must not directly reveal answer.
            - rewardClue must advance at least one final answer slot or destination inference through an indirect clue and must never print an exact final answer keyword or the full final answer.
            - rewardClueSlotId must identify which final answer slot this clue supports when applicable.
            - rewardClueLabel must be a Korean label such as "정체 단서", "핵심 물건 단서", "동선 단서", "보관 장소 단서", "해금 조건 단서", or the selected genre's own slot label.
            - supportsKeywordSlots must list the slotIds this mission supports.
            - puzzleAnswerSource must be one of NUMBER, VISIBLE_ELEMENT, KEYWORD, ADMIN_MEMO, DESCRIPTION, FICTION_SAFE.
            - puzzleAnswerRisk must be one of OK, GENERIC, PLACE_NAME_RISK, FINAL_KEYWORD_RISK, REVIEW_REQUIRED.
            - verificationLevel must be one of AUTO_OK, ADMIN_REVIEW, FIELD_REQUIRED.
            - groundRule must explain which provided input field was used.
            
            Character and evidence rules:
            - Character cards are fictional stakeholders, handlers, witnesses, couriers, archivists, brokers, guides, or false leads.
            - If finalAnswerType is not CULPRIT, character cards are not "the criminal".
            - Every character card must have alias, displayName, shortDescription, relationToVictim, suspiciousPoint, alibiSummary, and imagePrompt.
            - Every evidence card must connect to a mission rewardClue, character contradiction, route condition, or story clue without printing an exact final answer keyword.
            - Evidence cards must have title, type, textSummary, sourceMissionOrder, and imagePrompt.
            - Character and evidence text must state a specific action, contradiction, trace, condition, or relationship that helps deduction.
            - Do not use generic descriptions such as "helps the next decision", "narrows the key material", or "connects the mission flow".
            - imageUrl must be empty unless the admin provided a real URL.
            - imagePrompt must be in English and copy-ready for an external image generator.
            - imagePrompt style: flat 2D Korean webtoon / printed storybook illustration, muted earth-tone palette, matte paper grain, archival texture, clean dark ink outlines, gentle cel shading, calm documentary adventure mood
            - For any visible person, require a fictional Korean person from Seoul, South Korea.
            - Every imagePrompt must include: no readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark.
            - Maps, notes, letters, and documents must use only abstract route lines, stains, folds, torn edges, and non-text shapes.
            - No photorealism, no 3D render, no glossy game art, no Western comic style.
            
            Deduction rules:
            - deductionSecretFacts are server/admin-only facts for final deduction chat.
            - deductionForbiddenReveals must include the finalAnswer.
            - deductionForbiddenReveals must include direct final place reveal protection.
            - deductionForbiddenReveals must include every exact final answer keyword.
            
            Output JSON schema:
            {
              "episodeTitle": "string",
              "subtitle": "string",
              "genre": "string",
              "era": "string",
              "fictionSynopsis": "string",
              "selectedGenre": "string",
              "finalAnswerKeywords": ["actual keyword value"],
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
                  "puzzleAnswerSource": "NUMBER|VISIBLE_ELEMENT|KEYWORD|ADMIN_MEMO|DESCRIPTION|FICTION_SAFE",
                  "puzzleAnswerRisk": "OK|GENERIC|PLACE_NAME_RISK|FINAL_KEYWORD_RISK|REVIEW_REQUIRED",
                  "rewardClue": "string",
                  "rewardClueSlotId": "string",
                  "rewardClueLabel": "string",
                  "supportsKeywordSlots": ["slotId"],
                  "hints": ["string", "string", "string"],
                  "groundRule": "string",
                  "verificationLevel": "AUTO_OK|ADMIN_REVIEW|FIELD_REQUIRED"
                }
              ],
              "suspects": [
                {
                  "alias": "string",
                  "displayName": "string",
                  "portraitImageUrl": "",
                  "imagePrompt": "string",
                  "shortDescription": "string",
                  "relationToVictim": "string",
                  "suspiciousPoint": "string",
                  "alibiSummary": "string"
                }
              ],
              "evidences": [
                {
                  "title": "string",
                  "type": "PHOTO|MEMO|NOTE|DOCUMENT|EVIDENCE|SUSPECT_CLUE|POST_IT|ANSWER_CLUE|DESTINATION_CLUE|STORY_CLUE",
                  "imageUrl": "",
                  "imagePrompt": "string",
                  "textSummary": "string",
                  "sourceMissionOrder": 1
                }
              ]
            }
            
            Input:
            """ + inputJson;
    }

    private String buildPlanPrompt(AiEpisodeDraftRequest request) {
        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI_INPUT_SERIALIZE_FAILED",
                    "AI 정답 계획 입력값을 JSON으로 변환할 수 없습니다."
            );
        }

        return """
            You are the episode planning director for Operation Korea.
            Return JSON only. Do not wrap it in markdown.
            Write all text fields in Korean unless the schema field is an ID or enum.
            
            Your job:
            - Choose one episode genre.
            - Use that genre's answer slots.
            - Generate short final answer keywords for each slot.
            - The result will be reviewed by an admin before full draft generation.
            
            Source rules:
            - Use only the provided input JSON.
            - Use selected places, descriptions, keywords, visibleElements, numbers, adminMemo, and place roles as source material.
            - Do not invent real historical facts that are not present in the input.
            - Do not use real historical people as perpetrators, villains, false leads, or final answer values.
            
            Genre rules:
            - If input.genreCatalog exists and is not empty, it is the source of truth.
            - Choose exactly one genre from input.genreCatalog.
            - Return the chosen genre's genreId as selectedGenreId.
            - Return the chosen genre's genreName as selectedGenreName.
            - Do not create a genre that is not in input.genreCatalog when genreCatalog is provided.
            - Do not rename genreId, genreName, slotId, or slot labels from genreCatalog.
            
            Fallback genre rules:
            - If input.genreCatalog is missing or empty, infer a safe temporary genre.
            - In that case, selectedGenreId must be "CUSTOM".
            - Also add "GENRE_CATALOG_MISSING" to validationWarnings.
            - The inferred genre must still be playable as an outdoor clue-based episode.
            
            Answer slot rules:
            - If the selected genre has answerSlots, use those answerSlots exactly.
            - For each answerSlot, generate exactly one final answer keyword.
            - Do not add, remove, rename, or merge slots from the selected genre.
            - If genreCatalog is missing and you create a CUSTOM genre, create 2 to 4 answerSlots.
            - Each answerSlot must represent a final deduction target, such as identity, object, location, condition, number, route, motive, phrase, or hidden truth component.
            
            Final answer keyword rules:
            - Each keyword must be an actual answer value, not a slot label.
            - Bad: label=정체, keyword=정체
            - Good: label=정체, keyword=기록 중개인
            - Bad: label=핵심 물건, keyword=핵심 물건
            - Good: label=핵심 물건, keyword=봉인된 붓
            - Bad: label=마지막 조건, keyword=마지막 조건
            - Good: label=마지막 조건, keyword=붉은 인장
            
            Keyword quality rules:
            - Keep each keyword short, concrete, atomic, and easy to type.
            - Prefer nouns, fictional role names, object names, short place features, short conditions, numbers, or short phrases.
            - A keyword should usually be 1 to 6 Korean words.
            - Do not output poetic phrases, full sentences, or title-like expressions as one keyword.
            - Do not use vague generic words alone.
            - Forbidden generic keywords: 단서, 기록, 문서, 메모, 진실, 비밀, 장소, 물건, 사건, 흔적, 정보.
            - Do not use adjective-heavy forms unless the adjective is truly the answer.
            - Avoid: 잊혀진, 숨겨진, 가려진, 봉인된, 사라진, 오래된, 비밀스러운.
            - Avoid possessive forms using "의" unless absolutely necessary.
            - Do not use a full selected place name as a final answer keyword.
            - Do not use a real person's name as a final answer keyword.
            
            Clue grounding rules:
            - Each final answer keyword must have a sourceBasis.
            - sourceBasis must briefly explain which input data inspired the keyword.
            - If possible, include sourcePlaceOrder.
            - Do not claim there is a sign, plaque, number, sculpture, stair, mural, or visible object unless it exists in visibleElements, numbers, description, keywords, or adminMemo.
            
            Difficulty and risk rules:
            - difficulty must be one of EASY, NORMAL, HARD.
            - risk must be one of OK, REVIEW_REQUIRED, TOO_ABSTRACT, TOO_LONG, REAL_PERSON_RISK, PLACE_NAME_RISK, WEAK_SOURCE.
            - Use REVIEW_REQUIRED or WEAK_SOURCE if the keyword is useful but the source data is thin.
            - Use TOO_ABSTRACT if the keyword is too vague.
            - Use PLACE_NAME_RISK if the keyword resembles a selected place name.
            - Use REAL_PERSON_RISK if the keyword might be interpreted as a real person.
            
            Final question guide rules:
            - finalQuestionGuide must explain what the player will ultimately submit.
            - It must refer to slot labels, not exact keyword values.
            - Good: "정체, 핵심 물건, 마지막 조건을 종합해 최종 결론을 보고하게 한다."
            - Bad: "화공, 붓, 후원을 그대로 맞히게 한다."
            
            Output schema:
            {
              "selectedGenreId": "string",
              "selectedGenreName": "string",
              "answerSlots": [
                {
                  "slotId": "string",
                  "label": "string",
                  "description": "string",
                  "minClueCount": 2
                }
              ],
              "finalAnswerKeywords": [
                {
                  "slotId": "string",
                  "label": "string",
                  "keyword": "actual short answer value",
                  "aliases": ["optional alias"],
                  "sourcePlaceOrder": 1,
                  "sourceBasis": "string",
                  "difficulty": "EASY|NORMAL|HARD",
                  "risk": "OK|REVIEW_REQUIRED|TOO_ABSTRACT|TOO_LONG|REAL_PERSON_RISK|PLACE_NAME_RISK|WEAK_SOURCE"
                }
              ],
              "finalQuestionGuide": "string",
              "rationale": "string",
              "rejectedGenreReasons": ["string"],
              "validationWarnings": ["string"]
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
                throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_EMPTY_RESPONSE", "Gemini가 빈 초안을 반환했습니다.");
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_SCHEMA_INVALID", "Gemini 초안이 필수 JSON 스키마와 일치하지 않습니다.");
        }
    }

    private void validateDraftRules(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
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
            addFinding(findings, "ERROR", "ABSTRACT_FINAL_ANSWER", "Final answer is too abstract or not a concrete fictional answer.", null);
        }

        if (containsBlockedHistoricalName(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ANSWER", "Final answer must not use a real historical person.", null);
        }

        if ("CULPRIT".equals(normalize(draft.getFinalAnswerType())) && looksLikeRealNameCulprit(draft.getFinalAnswer())) {
            addFinding(findings, "ERROR", "REAL_NAME_LIKE_CULPRIT", "CULPRIT answer must be a fictional role or alias, not a real person name.", null);
        }

        if (draft.getFinalAnswerAliases() != null
                && draft.getFinalAnswerAliases().stream().anyMatch(this::containsBlockedHistoricalName)) {
            addFinding(findings, "ERROR", "REAL_HISTORICAL_PERSON_IN_FINAL_ALIAS", "Final answer aliases must not include real historical person names.", null);
        }

        validateApprovedKeywordContract(draft, sourceInput, findings);
        validateStoryObjectiveAlignment(draft, sourceInput, findings);

        if (sourceInput != null && sourceInput.getPlaces() != null) {
            for (AiEpisodeDraftRequest.PlaceInput place : sourceInput.getPlaces()) {
                if (same(place.getName(), draft.getFinalAnswer())) {
                    addFinding(findings, "ERROR", "FINAL_ANSWER_IS_PLACE", "Final answer must not be the same as an actual place name.", null);
                }
            }
        }

        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions();

        if (missions == null || missions.isEmpty()) {
            addFinding(findings, "ERROR", "MISSING_MISSIONS", "Draft must include missions.", null);
            return;
        }

        validateMissionPolicyCounts(missions, sourceInput, findings);

        int startCount = 0;
        int finalCount = 0;
        int answerHintCount = 0;
        int destinationHintCount = 0;

        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();

            String markerType = normalize(mission.getMarkerType());
            String publicMarkerType = normalize(mission.getPublicMarkerType());

            if ("START".equals(markerType)) {
                startCount++;
            }
            if ("ANSWER_HINT".equals(markerType)) {
                answerHintCount++;
            }
            if ("DESTINATION_HINT".equals(markerType)) {
                destinationHintCount++;
            }
            if ("FINAL".equals(markerType) || Boolean.TRUE.equals(mission.getFinalPlace())) {
                finalCount++;
            }

            if ("FINAL".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "PUBLIC_FINAL_MARKER_EXPOSED", "publicMarkerType must never be FINAL.", order);
            }

            if ("FINAL".equals(markerType) && !"DESTINATION_HINT".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "FINAL_PUBLIC_MARKER_INVALID", "Internal FINAL mission must use publicMarkerType DESTINATION_HINT.", order);
            }

            if (Boolean.TRUE.equals(mission.getFinalPlace()) && "START".equals(publicMarkerType)) {
                addFinding(findings, "ERROR", "FINAL_PLACE_PUBLIC_TYPE_INVALID", "Actual final place must not be exposed as START.", order);
            }

            if (blank(mission.getStoryText())) {
                addFinding(findings, "WARN", "MISSING_STORY_TEXT", "Spot story text is missing.", order);
            }

            if (containsFinalRevealTerms(mission.getStoryText())) {
                addFinding(findings, "ERROR", "FINAL_PLACE_REVEAL_IN_STORY", "Mission story must not reveal final-place wording.", order);
            }

            if (blank(mission.getQuestionText())) {
                addFinding(findings, "ERROR", "MISSING_PUZZLE_QUESTION", "Puzzle question is required.", order);
            }

            if (blank(mission.getAnswer())) {
                addFinding(findings, "ERROR", "MISSING_PUZZLE_ANSWER", "Puzzle answer is required.", order);
            }

            if (mission.getHints() == null || mission.getHints().size() < 3) {
                addFinding(findings, "ERROR", "MISSING_HINTS", "Each puzzle must have 3 hints.", order);
            }

            if (blank(mission.getRewardClue())) {
                addFinding(findings, "ERROR", "MISSING_REWARD_CLUE", "Reward clue is required.", order);
            }
            if (isLowQualityGenericValue(mission.getRewardClue()) || isTooShortRewardClue(mission.getRewardClue())) {
                addFinding(findings, "ERROR", "GENERIC_REWARD_CLUE", "Reward clue is too generic to support player deduction.", order);
            }

            if (blank(mission.getGroundRule())) {
                addFinding(findings, "WARN", "MISSING_GROUND_RULE", "groundRule should explain which input field was used.", order);
            }

            AiEpisodeDraftRequest.PlaceInput sourcePlace = sourcePlace(sourceInput, order);

            if (sourcePlace != null && usesPlaceNameTextPuzzle(mission, sourcePlace)) {
                addFinding(findings, "ERROR", "QUESTION_USES_PLACE_NAME_TEXT", "Puzzle must not depend on extracting letters from a place or business name.", order);
            }

            if (sourcePlace != null && isPlaceNameAnswer(compact(mission.getAnswer()), sourcePlace.getName())) {
                addFinding(findings, "ERROR", "PUZZLE_ANSWER_IS_PLACE_NAME", "Puzzle answer must not be a place name or place-name fragment.", order);
            }

            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType()))) {
                if (lacksProvidedNumber(sourceInput, order)) {
                    addFinding(findings, "ERROR", "NUMBER_LOCK_WITHOUT_PROVIDED_NUMBER", "NUMBER_LOCK puzzle requires numbers from admin/TourAPI input.", order);
                } else if (!usesProvidedNumber(mission, sourcePlace)) {
                    addFinding(findings, "ERROR", "NUMBER_LOCK_ANSWER_NOT_PROVIDED", "NUMBER_LOCK answer must match a number provided for the source place.", order);
                }
            }

            if (!blank(draft.getFinalAnswer()) && textContains(mission.getQuestionText(), draft.getFinalAnswer())) {
                addFinding(findings, "ERROR", "FINAL_ANSWER_IN_QUESTION", "Puzzle question directly contains the final answer.", order);
            }

            if (!blank(draft.getFinalAnswer()) && textContains(mission.getRewardClue(), draft.getFinalAnswer())) {
                addFinding(findings, "ERROR", "FULL_FINAL_ANSWER_AS_REWARD", "Reward clue contains the full final answer.", order);
            }

            if (containsAnyApprovedFinalKeyword(mission.getAnswer(), sourceInput)) {
                addFinding(findings, "ERROR", "PUZZLE_ANSWER_IS_FINAL_KEYWORD", "Puzzle answer must not be the same as a final answer keyword.", order);
            }

            if (containsAnyApprovedFinalKeyword(mission.getQuestionText(), sourceInput)) {
                addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_QUESTION", "Puzzle question must not expose a final answer keyword.", order);
            }

            if (mission.getHints() != null && mission.getHints().stream().anyMatch(hint -> containsAnyApprovedFinalKeyword(hint, sourceInput))) {
                addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_HINT", "Hints must not directly reveal final answer keywords.", order);
            }

            if (isBlockedGenericPuzzleAnswer(mission.getAnswer(), sourceInput)) {
                addFinding(findings, "ERROR", "GENERIC_PUZZLE_ANSWER", "Puzzle answer is too generic or blocked by puzzlePolicy.", order);
            }

            validatePuzzlePolicyRules(mission, sourceInput, order, findings);
        }

        validateMissionRoleCounts(startCount, answerHintCount, destinationHintCount, finalCount, missions.size(), sourceInput, findings);
        validateUniquePuzzleAnswers(missions, sourceInput, findings);
        validateSlotClueCoverage(missions, sourceInput, findings);

        if (draft.getDeductionSecretFacts() == null || draft.getDeductionSecretFacts().isEmpty()) {
            addFinding(findings, "ERROR", "MISSING_DEDUCTION_SECRET_FACTS", "Deduction secret facts are required for final deduction chat.", null);
        }

        if (draft.getDeductionForbiddenReveals() == null
                || draft.getDeductionForbiddenReveals().stream().noneMatch(value -> same(value, draft.getFinalAnswer()))) {
            addFinding(findings, "ERROR", "MISSING_FORBIDDEN_FINAL_REVEAL", "deductionForbiddenReveals must include the final answer.", null);
        }

        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            addFinding(findings, "WARN", "LOW_SUSPECT_COUNT", "관계자 카드는 최소 3개 이상을 권장합니다.", null);
        }
        if (draft.getSuspects() != null && draft.getSuspects().stream()
                .anyMatch(card -> isGenericCardText(card.getShortDescription()))) {
            addFinding(findings, "ERROR", "GENERIC_CHARACTER_CARD_TEXT", "Character card text must include a specific action or contradiction.", null);
        }
        if (draft.getSuspects() != null && draft.getSuspects().stream()
                .anyMatch(card -> !hasTextFreeImageConstraints(card.getImagePrompt()))) {
            addFinding(findings, "ERROR", "IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING", "Image prompts must explicitly forbid readable text, Korean letters, labels, and handwriting.", null);
        }

        if (draft.getEvidences() == null || draft.getEvidences().size() < Math.max(1, missions.size() - 1)) {
            addFinding(findings, "WARN", "LOW_EVIDENCE_COUNT", "Evidence cards should cover most mission spots.", null);
        }
        if (draft.getEvidences() != null && draft.getEvidences().stream()
                .anyMatch(card -> isGenericCardText(card.getTextSummary()))) {
            addFinding(findings, "ERROR", "GENERIC_EVIDENCE_CARD_TEXT", "Evidence card text must include a specific trace, condition, or relationship.", null);
        }
        if (draft.getEvidences() != null && draft.getEvidences().stream()
                .anyMatch(card -> !hasTextFreeImageConstraints(card.getImagePrompt()))) {
            addFinding(findings, "ERROR", "IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING", "Image prompts must explicitly forbid readable text, Korean letters, labels, and handwriting.", null);
        }
    }

    private void validateApprovedKeywordContract(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        List<String> approvedKeywords = approvedFinalKeywords(sourceInput);

        if (approvedKeywords.isEmpty()) {
            return;
        }

        if (draft.getFinalAnswerKeywords() == null || draft.getFinalAnswerKeywords().isEmpty()) {
            addFinding(findings, "ERROR", "MISSING_FINAL_KEYWORDS", "Output finalAnswerKeywords must include approved keywords.", null);
            return;
        }

        for (String keyword : approvedKeywords) {
            boolean inOutputKeywords = draft.getFinalAnswerKeywords().stream()
                    .anyMatch(value -> same(value, keyword));

            if (!inOutputKeywords) {
                addFinding(findings, "ERROR", "APPROVED_KEYWORD_MISSING_IN_OUTPUT", "Approved final keyword is missing from output finalAnswerKeywords: " + keyword, null);
            }

            if (!textContains(draft.getFinalAnswer(), keyword)) {
                addFinding(findings, "ERROR", "APPROVED_KEYWORD_MISSING_IN_FINAL_ANSWER", "Final answer must include approved keyword: " + keyword, null);
            }
        }

        boolean hasKeywordAlias = draft.getFinalAnswerAliases() != null
                && draft.getFinalAnswerAliases().stream()
                .filter(value -> !blank(value))
                .anyMatch(alias -> alias.trim().startsWith("KW:")
                        && approvedKeywords.stream().allMatch(keyword -> textContains(alias, keyword)));

        if (!hasKeywordAlias) {
            addFinding(findings, "ERROR", "MISSING_KEYWORD_CONTRACT_ALIAS", "finalAnswerAliases must include KW:keyword1|keyword2 contract.", null);
        }

        if (containsAnyApprovedFinalKeyword(draft.getEpisodeTitle(), sourceInput)) {
            addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_TITLE", "Episode title must not expose final answer keywords.", null);
        }

        if (containsAnyApprovedFinalKeyword(draft.getSubtitle(), sourceInput)) {
            addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_SUBTITLE", "Subtitle must not expose final answer keywords.", null);
        }

        if (containsAnyApprovedFinalKeyword(draft.getFictionSynopsis(), sourceInput)) {
            addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_SYNOPSIS", "fictionSynopsis must not expose final answer keywords.", null);
        }

        if (containsAnyApprovedFinalKeyword(draft.getFinalQuestion(), sourceInput)) {
            addFinding(findings, "ERROR", "FINAL_KEYWORD_IN_FINAL_QUESTION", "finalQuestion must ask by slot labels, not exact keyword values.", null);
        }
    }

    private boolean containsAnyApprovedFinalKeyword(String text, AiEpisodeDraftRequest sourceInput) {
        if (blank(text)) {
            return false;
        }

        List<String> keywords = approvedFinalKeywordVariants(sourceInput);

        if (keywords.isEmpty()) {
            return false;
        }

        return keywords.stream()
                .filter(value -> !blank(value))
                .anyMatch(keyword -> containsExactAnswerValue(text, keyword));
    }

    private void validateMissionPolicyCounts(
            List<AiEpisodeDraftResponse.MissionDraft> missions,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        int missionCount = missions.size();
        int placeCount = sourceInput == null || sourceInput.getPlaces() == null
                ? 0
                : sourceInput.getPlaces().size();

        if (missionCount != placeCount) {
            addFinding(findings, "ERROR", "INVALID_MISSION_COUNT", "Draft mission count must match the selected place count.", null);
        }

        if (placeCount == 0) {
            return;
        }

        AiEpisodeDraftRequest.MissionPolicyInput policy = sourceInput.getMissionPolicy();
        int min = policy != null && policy.getMinMissionCount() != null
                ? policy.getMinMissionCount()
                : 3;

        int max = policy != null && policy.getMaxMissionCount() != null
                ? policy.getMaxMissionCount()
                : 30;

        if (missionCount < min) {
            addFinding(findings, "ERROR", "MISSION_COUNT_BELOW_POLICY_MIN", "Mission count is below missionPolicy minimum.", null);
        }

        if (missionCount > max) {
            addFinding(findings, "ERROR", "MISSION_COUNT_ABOVE_POLICY_MAX", "Mission count is above missionPolicy maximum.", null);
        }
    }

    private void validateMissionRoleCounts(
            int startCount,
            int answerHintCount,
            int destinationHintCount,
            int finalCount,
            int totalMissionCount,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        AiEpisodeDraftRequest.MissionPolicyInput policy =
                sourceInput == null ? null : sourceInput.getMissionPolicy();

        int requiredStartCount = policy != null && policy.getStartCount() != null
                ? policy.getStartCount()
                : 1;

        int requiredFinalCount = policy != null && policy.getFinalCount() != null
                ? policy.getFinalCount()
                : 1;

        if (startCount != requiredStartCount) {
            addFinding(findings, "ERROR", "INVALID_START_COUNT", "START mission count must match missionPolicy.startCount.", null);
        }

        if (finalCount != requiredFinalCount) {
            addFinding(findings, "ERROR", "INVALID_FINAL_PLACE_COUNT", "Internal FINAL mission count must match missionPolicy.finalCount.", null);
        }

        if (policy != null && policy.getAnswerHintRatio() != null) {
            int minAnswerHints = (int) Math.ceil(totalMissionCount * policy.getAnswerHintRatio());
            if (answerHintCount < minAnswerHints) {
                addFinding(findings, "WARN", "LOW_ANSWER_HINT_COUNT", "ANSWER_HINT count is lower than missionPolicy.answerHintRatio.", null);
            }
        }

        if (totalMissionCount >= 5 && policy != null && policy.getDestinationHintRatio() != null) {
            int minDestinationHints = (int) Math.ceil(totalMissionCount * policy.getDestinationHintRatio());
            if (destinationHintCount < minDestinationHints) {
                addFinding(findings, "WARN", "LOW_DESTINATION_HINT_COUNT", "DESTINATION_HINT count is lower than missionPolicy.destinationHintRatio.", null);
            }
        }
    }

    private void validatePuzzlePolicyRules(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest sourceInput,
            int order,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        AiEpisodeDraftRequest.PuzzlePolicyInput policy =
                sourceInput == null ? null : sourceInput.getPuzzlePolicy();

        if (policy == null) {
            return;
        }

        if (policy.getAllowedPuzzleTypes() != null
                && !policy.getAllowedPuzzleTypes().isEmpty()
                && !policy.getAllowedPuzzleTypes().contains(normalize(mission.getPuzzleType()))) {
            addFinding(findings, "ERROR", "PUZZLE_TYPE_NOT_ALLOWED", "Puzzle type is not allowed by puzzlePolicy.", order);
        }

        if (Boolean.TRUE.equals(policy.getForbidFinalKeywordAsPuzzleAnswer())
                && containsAnyApprovedFinalKeyword(mission.getAnswer(), sourceInput)) {
            addFinding(findings, "ERROR", "PUZZLE_ANSWER_IS_FINAL_KEYWORD", "Puzzle answer must not equal final answer keyword.", order);
        }

        if (Boolean.TRUE.equals(policy.getForbidPlaceNameTextExtraction())
                && usesWeakTextExtractionPuzzle(mission)) {
            addFinding(findings, "ERROR", "TEXT_EXTRACTION_PUZZLE_FORBIDDEN", "Puzzle must not use letter/syllable/substring extraction.", order);
        }
    }

    private boolean isBlockedGenericPuzzleAnswer(String answer, AiEpisodeDraftRequest sourceInput) {
        if (blank(answer)) {
            return false;
        }

        String compactAnswer = compact(answer);

        Set<String> defaultBlocked = Set.of(
                "memo", "메모",
                "record", "기록",
                "document", "문서",
                "clue", "단서",
                "info", "정보",
                "truth", "진실",
                "secret", "비밀",
                "place", "장소",
                "object", "물건",
                "event", "사건",
                "현장단서",
                "검수필요"
        );

        if (defaultBlocked.contains(compactAnswer)) {
            return true;
        }

        AiEpisodeDraftRequest.PuzzlePolicyInput policy =
                sourceInput == null ? null : sourceInput.getPuzzlePolicy();

        if (policy == null || policy.getBlockedGenericAnswers() == null) {
            return false;
        }

        return policy.getBlockedGenericAnswers().stream()
                .filter(value -> !blank(value))
                .map(this::compact)
                .anyMatch(value -> value.equals(compactAnswer));
    }

    private void validateUniquePuzzleAnswers(
            List<AiEpisodeDraftResponse.MissionDraft> missions,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        AiEpisodeDraftRequest.PuzzlePolicyInput policy =
                sourceInput == null ? null : sourceInput.getPuzzlePolicy();

        if (policy == null || !Boolean.TRUE.equals(policy.getRequireUniquePuzzleAnswer())) {
            return;
        }

        Map<String, List<Integer>> ordersByAnswer = new LinkedHashMap<>();

        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();

            if (blank(mission.getAnswer())) {
                continue;
            }

            String key = compact(mission.getAnswer());
            ordersByAnswer.computeIfAbsent(key, ignored -> new ArrayList<>()).add(order);
        }

        for (Map.Entry<String, List<Integer>> entry : ordersByAnswer.entrySet()) {
            if (entry.getValue().size() > 1) {
                addFinding(
                        findings,
                        "ERROR",
                        "DUPLICATE_PUZZLE_ANSWER",
                        "Puzzle answer is duplicated: " + entry.getKey() + " / missions=" + entry.getValue(),
                        null
                );
            }
        }
    }

    private void validateSlotClueCoverage(
            List<AiEpisodeDraftResponse.MissionDraft> missions,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings
    ) {
        if (sourceInput == null
                || sourceInput.getFinalAnswerKeywordItems() == null
                || sourceInput.getFinalAnswerKeywordItems().isEmpty()) {
            return;
        }

        AiEpisodeDraftRequest.MissionPolicyInput policy = sourceInput.getMissionPolicy();

        int minClueCount = policy != null && policy.getMinCluesPerAnswerSlot() != null
                ? policy.getMinCluesPerAnswerSlot()
                : 1;

        Map<String, Integer> clueCountBySlot = new LinkedHashMap<>();

        for (AiEpisodeDraftRequest.AnswerKeywordInput item : sourceInput.getFinalAnswerKeywordItems()) {
            if (item != null && !blank(item.getSlotId())) {
                clueCountBySlot.put(item.getSlotId(), 0);
            }
        }

        for (AiEpisodeDraftResponse.MissionDraft mission : missions) {
            if (mission.getSupportsKeywordSlots() == null) {
                continue;
            }

            for (String slotId : mission.getSupportsKeywordSlots()) {
                if (clueCountBySlot.containsKey(slotId)) {
                    clueCountBySlot.put(slotId, clueCountBySlot.get(slotId) + 1);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : clueCountBySlot.entrySet()) {
            if (entry.getValue() < minClueCount) {
                addFinding(
                        findings,
                        "WARN",
                        "LOW_SLOT_CLUE_COVERAGE",
                        "Answer slot has too few supporting reward clues: " + entry.getKey(),
                        null
                );
            }
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
                            .message(node.path("message").asText("Gemini 검증 항목을 확인해야 합니다."))
                            .missionOrder(node.path("missionOrder").isNumber() ? node.path("missionOrder").asInt() : null)
                            .build());
                }
            }
            return findings;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_VALIDATION_PARSE_FAILED", "Gemini 검증 응답을 해석할 수 없습니다.");
        }
    }

    private String buildValidationPrompt(AiEpisodeDraftValidationRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            return """
                      You are a safety and quality reviewer for an outdoor story-mission escape-room draft.
                      Return JSON only. Do not rewrite the draft.
                      All finding.message values must be written in Korean.
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
                    - episodeTitle, subtitle, fictionSynopsis, and finalQuestion must not directly reveal admin-approved final answer keywords before clear.
                    - Mission clues, character cards, and evidence cards may reveal individual keyword candidates progressively, but must not list every approved keyword together as the complete final answer.
                      - Story must not distort real history as fact or make real historical people perpetrators, villains, or final answer values.
                      - The finalQuestion and finalAnswer must satisfy the fictionSynopsis objective. If the synopsis asks for multiple slots such as identity plus hideout, the finalQuestion and finalAnswer must cover every slot. Report STORY_OBJECTIVE_MISMATCH if the finalQuestion asks about a different object or only one required slot.
                      - Final deduction must have secret facts and forbidden reveal terms.
                    
                      Output schema:
                      {
                        "findings": [
                          {"severity":"ERROR|WARN|INFO","code":"string","message":"Korean concise message","missionOrder":1}
                        ]
                      }
                    
                      Draft and source input:
                    """ + payload;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_VALIDATION_INPUT_SERIALIZE_FAILED", "AI 검증 입력값을 JSON으로 변환할 수 없습니다.");
        }
    }

    private List<String> normalizeAndValidateDraft(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<String> warnings = new ArrayList<>();
        if (draft == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_SCHEMA_INVALID", "Gemini 초안이 필수 JSON 스키마와 일치하지 않습니다.");
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

            boolean routeAndRoleObjective = containsAny(
                    draft.getFictionSynopsis(),
                    "정체", "누구", "배후", "조직", "세력", "은신처", "숨어든", "거점", "아지트", "동선", "보관", "조건"
            );

            draft.setFinalAnswerType("HIDDEN_TRUTH");

            if (routeAndRoleObjective) {
                draft.setFinalAnswer("가상의 기록 전달자가 봉인된 자료를 숨겨진 보관 지점으로 옮긴 것이다");
                draft.setFinalAnswerAliases(new ArrayList<>(List.of(
                        "가상의기록전달자가봉인된자료를숨겨진보관지점으로옮긴것이다",
                        "가상의 기록 전달자와 숨겨진 보관 지점"
                )));
                draft.setFinalQuestion("관계자의 역할, 핵심 자료, 마지막 보관 조건을 종합하면 어떤 결론인가?");
            } else {
                draft.setFinalAnswer("가상의 기록 전달자가 핵심 자료의 이동 경로를 숨긴 것이다");
                draft.setFinalAnswerAliases(new ArrayList<>(List.of(
                        "가상의기록전달자가핵심자료의이동경로를숨긴것이다",
                        "가상의 기록 전달자와 숨겨진 이동 경로"
                )));
                draft.setFinalQuestion("미션 파일의 단서가 공통으로 가리키는 최종 결론은 무엇인가?");
            }

            draft.setFinalTruthSummary("""
            3. 픽션과 역사의 매칭 (디브리핑)
            스토리 속 [가상의 기록 전달자] -> 실제 배경 속 [기록을 남기고 전한 사람들]: 실존 인물을 악역으로 만들지 않고 역할 구조만 차용했습니다.
            스토리 속 [엇갈린 기록] -> 실제 배경 속 [서로 다른 해설과 현장 자료]: 자료를 비교하며 의미를 찾는 구조로 각색했습니다.
            스토리 속 [이동 경로] -> 실제 배경 속 [선택 장소들의 동선]: 현장 이동을 통해 단서가 이어지도록 변환했습니다.
            스토리 속 [숨겨진 자료] -> 실제 배경 속 [장소에 남은 기억과 기록]: 직접 해설 대신 클리어 후 공개되는 배경 설명으로 분리했습니다.
            """.trim());

            draft.setActualHistorySummary("""
            1. 모티브 공개
            이 임무는 실제 [관리자 검수 필요 최종 장소]의 역사적 배경과 현장 자료를 모티브로 제작되었습니다.
            
            2. 실제 배경 해설
            Gemini 초안 정규화 과정에서 실존 인물 노출 위험이 감지되어 안전한 픽션 역할과 자료 중심 구조로 대체했습니다. 공개 전 관리자는 TourAPI 설명, 현장 표지, 공식 해설 자료를 확인해 최종 장소의 실제 배경과 의의를 정확히 보강해야 합니다.
            """.trim());

            draft.setDeductionSecretFacts(new ArrayList<>(List.of(
                    "최종 정답은 시놉시스가 요구한 해결 조건을 모두 포함해야 한다.",
                    "단서 물건이나 문서 위치만 맞히는 답은 최종 정답이 아니다.",
                    "정답은 실제 장소명이나 실존 인물명이 아니라 픽션 미션 안의 완결된 결론이다."
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

        applyApprovedFinalAnswerContract(draft, request, warnings);

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
                String replacement = fallbackAnswer(place, request);
                mission.setAnswer(replacement);
                mission.setAnswerFormat("TEXT");
                mission.setPuzzleAnswerSource(resolvePuzzleAnswerSource(replacement, place));
                mission.setPuzzleAnswerRisk("검수필요".equals(replacement) ? "REVIEW_REQUIRED" : "OK");
                mission.setVerificationLevel("검수필요".equals(replacement) ? "FIELD_REQUIRED" : "ADMIN_REVIEW");
                if ("검수필요".equals(replacement)) {
                    mission.setQuestionText("이 지점은 현장 근거를 확인한 뒤 퍼즐 정답을 확정해야 합니다.");
                    mission.setHints(reviewRequiredHints());
                }
                warnings.add("Mission " + (i + 1) + " answer was a place name or invalid fallback; review before publishing.");
            }
            if (blank(mission.getAnswer())) {
                mission.setAnswer(fallbackAnswer(place, request));
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (blank(mission.getRewardClue())) {
                mission.setRewardClue(fallbackReward(role, i));
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            sanitizePlaceNameDependentReward(mission, request, role, i, warnings);
            sanitizeForbiddenRevealReward(mission, draft, role, i, warnings);
            sanitizeFinalAnswerLeaks(draft, mission, place, request, role, i, warnings);
            sanitizeGenericRewardClue(mission, place, request, role, i, warnings);
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (blank(mission.getGroundRule())) {
                mission.setGroundRule("제공된 visibleElements, numbers, keywords, description, adminMemo 안의 정보만 근거로 사용합니다.");
            }
            sanitizeCategoryCodes(mission);
        }
        if (!finalExists) {
            warnings.add("Final place and final answer require admin review.");
        }
        ensureMinimumSuspects(draft, warnings);
        ensureMissionEvidences(draft, request, warnings);
        strengthenDeductionCards(draft, request, warnings);
        sanitizeCardKeywordLeaks(draft, request, warnings);
        strengthenDeductionCards(draft, request, warnings);
        return normalizeWarningMessages(warnings);
    }

    private List<String> normalizeWarningMessages(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }

        return warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeWarningMessage)
                .distinct()
                .toList();
    }

    private String normalizeWarningMessage(String warning) {
        if (blank(warning)) {
            return "AI 초안 보정 내용을 검수해야 합니다.";
        }

        if ("Draft normalization changed a field; review before publishing.".equals(warning)) {
            return "AI 초안의 일부 필드가 안전한 기본값으로 보정되었습니다. 공개 전 검수하세요.";
        }

        if ("Final place and final answer require admin review.".equals(warning)) {
            return "최종 장소와 최종 정답은 관리자 검수가 필요합니다.";
        }

        if ("Final answer keywords were removed during normalization; review answer plan before publishing.".equals(warning)) {
            return "정규화 과정에서 최종 정답 키워드가 누락되었습니다. 공개 전 정답 계획을 다시 확인하세요.";
        }

        if (warning.startsWith("Forbidden reveal removed:")) {
            return "금지 노출 항목이 제거되었습니다: " + warning.substring("Forbidden reveal removed:".length()).trim();
        }

        String missionPrefix = "Mission ";
        String normalizedSuffix = " was normalized; review before publishing.";
        String invalidAnswerSuffix = " answer was a place name or invalid fallback; review before publishing.";
        String leakSuffix = " final answer leak was normalized; review before publishing.";

        if (warning.startsWith(missionPrefix) && warning.endsWith(normalizedSuffix)) {
            String missionOrder = warning.substring(
                    missionPrefix.length(),
                    warning.length() - normalizedSuffix.length()
            ).trim();

            return "미션 " + missionOrder + "의 일부 필드가 안전한 기본값으로 보정되었습니다. 공개 전 검수하세요.";
        }

        if (warning.startsWith(missionPrefix) && warning.endsWith(invalidAnswerSuffix)) {
            String missionOrder = warning.substring(
                    missionPrefix.length(),
                    warning.length() - invalidAnswerSuffix.length()
            ).trim();

            return "미션 " + missionOrder + "의 정답이 장소명 또는 부적절한 기본값이라 보정되었습니다. 공개 전 검수하세요.";
        }

        if (warning.startsWith(missionPrefix) && warning.endsWith(leakSuffix)) {
            String missionOrder = warning.substring(
                    missionPrefix.length(),
                    warning.length() - leakSuffix.length()
            ).trim();

            return "미션 " + missionOrder + "에서 최종 정답 노출 위험이 감지되어 보정되었습니다. 공개 전 검수하세요.";
        }

        return warning;
    }

    private void applyApprovedFinalAnswerContract(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings
    ) {
        String selectedGenre = selectedGenreName(request);

        draft.setSelectedGenre(selectedGenre);
        draft.setGenre(selectedGenre);

        List<String> keywords = approvedFinalKeywords(request);

        if (keywords.isEmpty()) {
            if (hasAnyApprovedFinalKeywordInput(request)) {
                warnings.add("Final answer keywords were removed during normalization; review answer plan before publishing.");
            }
            return;
        }

        draft.setFinalAnswerKeywords(keywords);
        List<String> aliases = new ArrayList<>();
        if (draft.getFinalAnswerAliases() != null) {
            aliases.addAll(draft.getFinalAnswerAliases());
        }
        approvedFinalKeywordVariants(request).stream()
                .filter(value -> keywords.stream().noneMatch(keyword -> same(keyword, value)))
                .forEach(aliases::add);
        draft.setFinalAnswerAliases(withKeywordContract(aliases, keywords));
        List<String> slotLabels = approvedFinalSlotLabels(request, keywords);

        draft.setFinalAnswerType(keywords.size() > 1 ? "HIDDEN_TRUTH" : draft.getFinalAnswerType());
        draft.setFictionSynopsis(naturalFinalSynopsis(slotLabels));
        draft.setFinalQuestion(naturalFinalQuestion(slotLabels));
        draft.setFinalAnswer(naturalFinalAnswer(keywords));

        maskFinalAnswerKeywordLeaks(draft, keywords, selectedGenre);
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
                    .storyText(blank(place.getDescription())
                            ? "미션 파일을 확인하고 이 지점이 스토리 단서와 어떻게 연결되는지 찾으세요."
                            : place.getDescription())
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendedPuzzleType(place))
                    .questionText("이 지점과 미션 파일을 연결하는 단서 키워드를 입력하세요.")
                    .answer(fallbackAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(fallbackReward(role, index))
                    .hints(List.of(
                            "미션 파일의 관련 단서를 먼저 확인하세요.",
                            "현장 정보와 미션 메모에 반복되는 키워드를 찾으세요.",
                            "정답은 이 지점을 설명하는 짧은 단서입니다."
                    ))
                    .groundRule("Gemini가 선택 장소를 누락해 로컬 규칙으로 생성한 보정 미션입니다.")
                    .build());
        }
        if (draft.getMissions() == null || draft.getMissions().size() != expected) {
            warnings.add("Draft normalization changed a field; review before publishing.");
        }
        draft.setMissions(missions);
    }

    private String defaultSubtitle(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null
                ? List.of()
                : request.getPlaces();

        String start = places.isEmpty()
                ? "첫 조사 지점"
                : places.get(0).getName();

        String genre = blank(draft.getGenre())
                ? "야외 스토리 미션"
                : draft.getGenre();

        return start + " 미션 동선: " + genre;
    }

    private void sanitizeCategoryCodes(AiEpisodeDraftResponse.MissionDraft mission) {
        mission.setStoryText(sanitizeCategoryCodes(mission.getStoryText()));
        mission.setQuestionText(sanitizeCategoryCodes(mission.getQuestionText()));
        mission.setAnswer(sanitizeCategoryCodes(mission.getAnswer()));
        mission.setRewardClue(sanitizeCategoryCodes(mission.getRewardClue()));
        mission.setGroundRule(sanitizeCategoryCodes(mission.getGroundRule()));
        if (mission.getHints() != null) {
            mission.setHints(mission.getHints().stream().map(this::sanitizeCategoryCodes).toList());
        }
    }

    private void validateStoryObjectiveAlignment(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput,
            List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String synopsis = draft.getFictionSynopsis();
        String question = draft.getFinalQuestion();
        String answer = draft.getFinalAnswer();
        if (blank(synopsis) || blank(question) || blank(answer)) {
            return;
        }
        List<String> keywords = approvedFinalKeywords(sourceInput);
        if (!keywords.isEmpty()) {
            List<String> labels = approvedFinalSlotLabels(sourceInput, keywords);
            List<String> missingSynopsisSlots = labels.stream()
                    .filter(label -> !textContains(synopsis, label))
                    .toList();
            List<String> missingQuestionSlots = naturalQuestionCoversSlots(question, labels)
                    ? List.of()
                    : labels.stream()
                            .filter(label -> !textContains(question, label))
                            .toList();
            List<String> missingAnswerSlots = keywords.stream()
                    .filter(keyword -> !textContains(answer, keyword))
                    .toList();
            if (!missingSynopsisSlots.isEmpty() || !missingQuestionSlots.isEmpty() || !missingAnswerSlots.isEmpty()) {
                addFinding(findings, "ERROR", "STORY_OBJECTIVE_MISMATCH",
                        "Approved answer slots must align across synopsis, final question, and final answer.",
                        null);
            }
            return;
        }
        List<String> missingQuestionSlots = new ArrayList<>();
        List<String> missingAnswerSlots = new ArrayList<>();
        checkObjectiveSlot(synopsis, question, answer, missingQuestionSlots, missingAnswerSlots,
                "정체", "정체", "누구", "배후", "조직", "세력", "관계자", "역할", "전달자", "중개인");
        checkObjectiveSlot(synopsis, question, answer, missingQuestionSlots, missingAnswerSlots,
                "은신처", "은신처", "숨어든", "숨은곳", "숨은 곳", "거점", "아지트");
        checkObjectiveSlot(synopsis, question, answer, missingQuestionSlots, missingAnswerSlots,
                "동기", "동기", "이유", "왜");
        if (!missingQuestionSlots.isEmpty() || !missingAnswerSlots.isEmpty()) {
            addFinding(findings, "ERROR", "STORY_OBJECTIVE_MISMATCH",
                    "Final question/answer must cover every objective required by fictionSynopsis. Missing in question: "
                            + String.join(", ", missingQuestionSlots)
                            + "; missing in answer: " + String.join(", ", missingAnswerSlots),
                    null);
        }
    }

    private void checkObjectiveSlot(
            String synopsis,
            String question,
            String answer,
            List<String> missingQuestionSlots,
            List<String> missingAnswerSlots,
            String label,
            String... keywords) {
        if (!containsAny(synopsis, keywords)) {
            return;
        }
        if (!containsAny(question, keywords)) {
            missingQuestionSlots.add(label);
        }
        if (!containsAny(answer, keywords)) {
            missingAnswerSlots.add(label);
        }
    }

    private void maskFinalAnswerKeywordLeaks(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> keywords, String selectedGenre) {
        if (draft == null || keywords == null || keywords.isEmpty()) {
            return;
        }
        draft.setEpisodeTitle(maskKeywords(draft.getEpisodeTitle(), keywords));
        draft.setSubtitle(maskKeywords(draft.getSubtitle(), keywords));
        if (containsKeywordLeak(draft.getFictionSynopsis(), keywords) || containsMaskPlaceholder(draft.getFictionSynopsis())) {
            draft.setFictionSynopsis(safeFictionSynopsis(draft, selectedGenre));
        }
        draft.setFinalQuestion(maskKeywords(draft.getFinalQuestion(), keywords));
    }

    private String maskKeywords(String text, List<String> keywords) {
        if (blank(text)) {
            return text;
        }
        String result = text;
        for (String keyword : keywords) {
            if (blank(keyword)) {
                continue;
            }
            String compactKeyword = compact(keyword);
            if (compactKeyword.length() <= 2) {
                result = result.replaceAll(
                        "(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(keyword.trim()) + "(?![\\p{L}\\p{N}])",
                        clueMask()
                );
            } else {
                result = result.replace(keyword, clueMask());
                result = result.replace(keyword.replaceAll("\\s+", ""), clueMask());
            }
        }
        return result;
    }

    private String normalizeAnswerKeywordValue(String keyword) {
        if (blank(keyword)) {
            return "";
        }

        return keyword.trim()
                .replaceAll("[\\[\\]\"'`]", "")
                .replaceAll("\\s+", " ");
    }

    private boolean containsMaskPlaceholder(String text) {
        if (blank(text)) {
            return false;
        }
        String compactText = compact(text);
        return compactText.contains("가려진단서")
                || compactText.matches(".*가려진\\d+자단서.*")
                || compactText.matches(".*\\d+자단서.*")
                || compactText.contains("정답키워드")
                || compactText.contains("핵심키워드")
                || compactText.contains("핵심단서");
    }

    private String safeFictionSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, String selectedGenre) {
        String source = String.join(" ",
                blank(draft.getEpisodeTitle()) ? "" : draft.getEpisodeTitle(),
                blank(draft.getSubtitle()) ? "" : draft.getSubtitle(),
                blank(draft.getFictionSynopsis()) ? "" : draft.getFictionSynopsis(),
                blank(selectedGenre) ? "" : selectedGenre
        );

        if (containsAny(source, "항구", "항해", "개항", "항로", "일지", "목포")) {
            return "오래된 항구 기록이 발견되며, 공식 기록에 남지 않은 이동 경로와 그 길을 가로막은 흔적이 드러난다. 요원은 항구 일대에 흩어진 암호와 기록을 대조해 숨겨진 경로의 의미, 마지막 자료의 행방, 최종 결론을 밝혀야 한다.";
        }

        if (containsAny(source, "정체", "조직", "세력", "은신처", "거점", "아지트")) {
            return "도시 곳곳에 남은 표식이 하나의 숨겨진 역할을 가리킨다. 요원은 현장 기록과 엇갈린 단서를 대조해 관계자의 역할, 숨겨진 거점의 조건, 마지막 자료가 가리키는 결론을 밝혀야 한다.";
        }

        if (containsAny(source, "보물", "상자", "봉인", "열쇠", "해금")) {
            return "오래 봉인된 물건의 행방을 둘러싸고 서로 다른 기록이 발견된다. 요원은 현장에 남은 암호와 보관 흔적을 따라가며 물건의 정체, 보관된 장소의 특징, 봉인을 푸는 조건을 밝혀야 한다.";
        }

        if (containsAny(source, "암호", "문장", "숫자", "해독")) {
            return "낡은 기록 속 암호문이 여러 조사 지점에서 서로 다른 형태로 반복된다. 요원은 숫자, 문장, 상징의 연결 규칙을 찾아 마지막 암호가 전달하려던 의미를 밝혀야 한다.";
        }

        if (containsAny(source, "실종", "사라진", "마지막")) {
            return "한 인물 또는 기록이 사라진 뒤, 마지막 동선을 둘러싼 자료들이 서로 어긋난다. 요원은 현장 단서와 남겨진 물건을 대조해 사라진 이유와 마지막 흔적이 가리키는 결론을 밝혀야 한다.";
        }

        String genre = blank(selectedGenre) ? "이 미션" : selectedGenre;

        return "선택된 장소 일대에서 오래된 기록과 서로 어긋나는 단서가 발견된다. 요원은 현장 단서, 암호, 미션 파일을 차례로 대조해 "
                + genre + "의 핵심 역할과 마지막 단서가 가리키는 결론을 밝혀야 한다.";
    }

    private boolean containsKeywordLeak(String text, List<String> keywords) {
        if (blank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (blank(keyword)) {
                continue;
            }
            if (containsExactAnswerValue(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private String clueMask() {
        return "미확정 역할";
    }

    private String sanitizeCategoryCodes(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("KakaoLocal:CE7", "카페/커피 휴식 지점")
                .replace("KakaoLocal:FD6", "음식점/식당 상권")
                .replace("KakaoLocal:CT1", "문화시설/전시 지점")
                .replace("KakaoLocal:AT4", "관광명소/명소 지점")
                .replace("CE7", "카페/커피 휴식 지점")
                .replace("FD6", "음식점/식당 상권")
                .replace("CT1", "문화시설/전시 지점")
                .replace("AT4", "관광명소/명소 지점");
    }


    private String extractJson(String text) {
        String value = text.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_JSON_NOT_FOUND", "Gemini 응답에서 JSON 객체를 찾을 수 없습니다.");
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
        String number = firstUsablePuzzleAnswer(place == null ? null : place.getNumbers(), place);
        if (!blank(number)) {
            return number;
        }

        String visible = firstUsablePuzzleAnswer(place == null ? null : place.getVisibleElements(), place);
        if (!blank(visible)) {
            return visible;
        }

        String keyword = firstUsablePuzzleAnswer(place == null ? null : place.getKeywords(), place);
        if (!blank(keyword)) {
            return keyword;
        }

        String basis = bestPuzzleBasis(place);
        if (isBadPuzzleAnswerBasis(basis, place)) {
            return "검수필요";
        }

        return basis;
    }

    private boolean naturalQuestionCoversSlots(String question, List<String> labels) {
        if (labels.stream().allMatch(label -> textContains(question, label))) {
            return true;
        }
        if (labels.size() >= 3) {
            return containsAny(question, "관계자의 행동", "관계자 행동")
                    && containsAny(question, "남겨진 매체", "기록 매체", "핵심 매체")
                    && containsAny(question, "확인 조건", "마지막 조건");
        }
        return labels.size() == 2
                && containsAny(question, "정체", "대상")
                && containsAny(question, "향한 곳", "숨겨진 곳", "장소");
    }

    private String fallbackAnswer(
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request) {
        List<String> candidates = new ArrayList<>();
        if (place != null) {
            if (place.getNumbers() != null) candidates.addAll(place.getNumbers());
            if (place.getVisibleElements() != null) candidates.addAll(place.getVisibleElements());
            if (place.getKeywords() != null) candidates.addAll(place.getKeywords());
        }
        return candidates.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .filter(value -> !isBadPuzzleAnswerBasis(value, place))
                .filter(value -> !isLowQualityGenericValue(value))
                .filter(value -> !containsAnyApprovedFinalKeyword(value, request))
                .findFirst()
                .orElseGet(() -> {
                    String basis = bestPuzzleBasis(place);
                    if (isBadPuzzleAnswerBasis(basis, place)
                            || isLowQualityGenericValue(basis)
                            || containsAnyApprovedFinalKeyword(basis, request)) {
                        return "검수필요";
                    }
                    return basis;
                });
    }

    private String fallbackReward(String role, int index) {
        return switch (role) {
            case "ANSWER_HINT" ->
                    List.of("찢긴 흔적", "빛바랜 표면", "접힌 자리", "반사된 그림자").get(Math.min(index, 3));
            case "DESTINATION_HINT", "FINAL" ->
                    index % 2 == 0 ? "붉은 벽의 치환" : "기록을 연 문";
            default ->
                    List.of("마지막 사진", "봉인된 봉투", "엇갈린 진술", "사라진 시간").get(Math.min(index % 4, 3));
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
        if (sanitized.stream().noneMatch(value -> same(value, "realPersonAsFinalAnswer"))) {
            sanitized.add("realPersonAsFinalAnswer");
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
            mission.setStoryText("모든 단서를 다시 대조하고 서로 다른 흔적의 연결을 확인할 수 있는 조용한 조사 지점입니다.");
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
                "substring", "syllable", "initial", "letter", "nth", "first", "last",
                "첫 글자", "첫글자", "마지막 글자", "마지막글자", "두 번째 글자", "두번째글자",
                "초성", "자음", "모음", "몇 번째 글자", "몇번째글자", "글자를 조합", "글자 조합");
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
        boolean asksCharacterExtraction = containsAny(question,
                "letter", "syllable", "initial", "first", "second", "third", "fourth", "last", "substring", "nth",
                "첫글자", "마지막글자", "두번째글자", "초성", "자음", "모음", "몇번째글자", "글자를조합", "글자조합");
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
        if (answer.isBlank() || "review-required".equals(answer) || answer.contains("검수필요")) {
            return false;
        }
        if (isGenericBasisLabel(answer) || isLowQualityGenericValue(answer) || isPlaceNameAnswer(answer, place.getName())) {
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
        if (blank(compactAnswer)) {
            return true;
        }

        return Set.of(
                "placedescription",
                "adminmemo",
                "casememo",
                "selectedoperationspot",
                "selected",
                "operation",
                "spot",
                "nearby",
                "verification",
                "focus",
                "place",
                "address",
                "entrance",
                "area",
                "siteverificationfocus",
                "nearbyfamousplacesignal",
                "seoul",
                "서울",
                "kakao",
                "kakaolocal",
                "tourapi",
                "tourapibased",
                "placecandidate",
                "장소후보",
                "후보지",
                "관광지",

                "memo",
                "record",
                "document",
                "clue",
                "info",
                "truth",
                "secret",
                "object",
                "event",

                "메모",
                "기록",
                "문서",
                "단서",
                "정보",
                "진실",
                "비밀",
                "물건",
                "사건",
                "흔적",
                "표식",
                "사진",
                "봉인",
                "그림자",
                "현장",
                "현장단서",
                "관리자검수",
                "검수필요",
                "확인필요"
        ).contains(compactAnswer);
    }


    private boolean usesWeakTextExtractionPuzzle(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = compact(String.join(" ", blank(mission.getQuestionText()) ? "" : mission.getQuestionText(), blank(mission.getAnswer()) ? "" : mission.getAnswer(), mission.getHints() == null ? "" : String.join(" ", mission.getHints())));
        return containsAny(text,
                "lettercount", "nthletter", "syllable", "initialonly", "firstletter", "lastletter", "combineinorder", "substring",
                "첫글자", "마지막글자", "두번째글자", "초성", "자음", "모음", "몇번째글자", "글자를조합", "글자조합");
    }


    private void applyPlayableStoryPuzzle(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            String role,
            int index
    ) {
        String basis = bestPuzzleBasis(place);

        if (isBadPuzzleAnswerBasis(basis, place)) {
            basis = fallbackPuzzleBasis(role, index);
        }

        if (isBadPuzzleAnswerBasis(basis, place)) {
            basis = "검수필요";
        }

        mission.setPuzzleType("STORY_COMBINATION");
        mission.setAnswer(basis);
        mission.setAnswerFormat("TEXT");

        if ("검수필요".equals(basis)) {
            mission.setQuestionText("이 지점은 현장 근거를 확인한 뒤 퍼즐 정답을 확정해야 합니다.");
            mission.setHints(reviewRequiredHints());
            mission.setGroundRule("사용 가능한 현장 근거가 부족하여 관리자 검수 후 퍼즐 정답 확정이 필요합니다.");
            mission.setPuzzleAnswerSource("FICTION_SAFE");
            mission.setPuzzleAnswerRisk("REVIEW_REQUIRED");
            mission.setVerificationLevel("FIELD_REQUIRED");
            return;
        }

        mission.setQuestionText("요원, 제공된 현장 근거 [" + basis + "]를 미션 파일과 연결한 확인어를 보고하게.");

        mission.setHints(List.of(
                "먼저 문제에 제시된 현장 근거를 확인하게.",
                "이 근거가 " + markerRoleLabel(role) + " 흐름에서 어떤 역할인지 보게.",
                "정답은 장소명이 아니라 이 지점에서 확인한 짧은 근거어일세."
        ));

        mission.setGroundRule("제공된 현장 근거 [" + basis + "]를 미션 파일 흐름과 연결합니다.");
        mission.setPuzzleAnswerSource(resolvePuzzleAnswerSource(basis, place));
        mission.setPuzzleAnswerRisk("OK");
        mission.setVerificationLevel("ADMIN_REVIEW");
    }

    private List<String> reviewRequiredHints() {
        return List.of(
                "자동 생성만으로 확인 가능한 근거가 부족합니다.",
                "현장에서 실제 확인 가능한 표지, 숫자, 조형물, 문구를 기록하세요.",
                "관리자 화면에서 정답과 근거를 보강한 뒤 공개하세요."
        );
    }


    private String bestPuzzleBasis(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null) return "검수필요";
        if (place.getVisibleElements() != null) {
            String visible = place.getVisibleElements().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!blank(visible)) return visible;
        }
        if (place.getKeywords() != null) {
            String keyword = place.getKeywords().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!blank(keyword)) return keyword;
        }
        String memoBasis = extractBasisPhrase(place.getAdminMemo(), place.getName());
        if (!blank(memoBasis)) return memoBasis;
        String descriptionBasis = extractBasisPhrase(place.getDescription(), place.getName());
        if (!blank(descriptionBasis)) return descriptionBasis;
        return "검수필요";
    }

    private boolean isReviewRequiredBasis(String basis) {
        String compactBasis = compact(basis);
        return compactBasis.isBlank() || compactBasis.contains("검수필요") || "review-required".equals(compactBasis);
    }

    private String fallbackPuzzleBasis(String role, int index) {
        return "검수필요";
    }

    private boolean isUsableAnswerBasis(String value, String placeName) {
        if (blank(value)) {
            return false;
        }

        String compactValue = compact(value);

        if (isGenericBasisLabel(compactValue)) {
            return false;
        }

        if (isPlaceNameAnswer(compactValue, placeName)) {
            return false;
        }

        if (compactValue.length() < 2) {
            return false;
        }

        if (compactValue.length() > 12) {
            return false;
        }

        return true;
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
            if (isBadPuzzleAnswerBasis(candidate, null)) {
                continue;
            }
            if (!compactPlaceName.isBlank() && (compactPlaceName.contains(compactCandidate) || compactCandidate.contains(compactPlaceName))) {
                continue;
            }
            if (isBadPuzzleAnswerBasis(candidate, null)) {
                continue;
            }
            return candidate;
        }
        return null;
    }


    private String markerRoleLabel(String role) {
        return switch (role) {
            case "ANSWER_HINT" -> "정답 단서";
            case "DESTINATION_HINT", "FINAL" -> "목적지 단서";
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
        boolean hasDescription = !blank(place.getDescription()) && !compact(place.getDescription()).contains("selected operation spot");
        boolean hasAdminMemo = !blank(place.getAdminMemo()) && !compact(place.getAdminMemo()).contains("운영공개전검수");
        return !hasRealVisibleElement && !hasNumber && !hasKeyword && !hasDescription && !hasAdminMemo;
    }


    private void sanitizeFinalAnswerLeaks(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request,
            String role,
            int index,
            List<String> warnings
    ) {
        if (draft == null || mission == null) {
            return;
        }

        boolean changed = false;

        if (containsFinalKeywordOrAlias(mission.getQuestionText(), draft)
                || containsAnyApprovedFinalKeyword(mission.getQuestionText(), request)) {
            mission.setQuestionText(safeQuestionText(role));
            changed = true;
        }

        if (containsFinalKeywordOrAlias(mission.getAnswer(), draft)
                || containsAnyApprovedFinalKeyword(mission.getAnswer(), request)) {
            mission.setAnswer(fallbackAnswer(place, request));
            mission.setPuzzleAnswerRisk("FINAL_KEYWORD_RISK");
            mission.setVerificationLevel("ADMIN_REVIEW");
            changed = true;
        }

        if (containsFinalKeywordOrAlias(mission.getRewardClue(), draft)
                || containsAnyApprovedFinalKeyword(mission.getRewardClue(), request)) {
            mission.setRewardClue(safeRewardClue(role, index));
            changed = true;
        }

        if (containsFinalKeywordOrAlias(mission.getStoryText(), draft)
                || containsAnyApprovedFinalKeyword(mission.getStoryText(), request)) {
            mission.setStoryText(safeStoryText(role));
            changed = true;
        }

        if (mission.getHints() != null && !mission.getHints().isEmpty()) {
            List<String> sanitizedHints = new ArrayList<>();
            boolean hintChanged = false;

            for (int i = 0; i < mission.getHints().size(); i++) {
                String hint = mission.getHints().get(i);

                if (containsFinalKeywordOrAlias(hint, draft)
                        || containsAnyApprovedFinalKeyword(hint, request)
                        || textContains(hint, mission.getAnswer())) {
                    sanitizedHints.add(safeHint(i));
                    hintChanged = true;
                } else {
                    sanitizedHints.add(hint);
                }
            }

            if (hintChanged) {
                mission.setHints(sanitizedHints);
                changed = true;
            }
        }

        if (changed) {
            warnings.add("Mission " + (index + 1) + " final answer leak was normalized; review before publishing.");
        }
    }

    private boolean containsFinalAnswerAlias(String text, AiEpisodeDraftResponse.EpisodeDraft draft) {
        return containsFinalKeywordOrAlias(text, draft);
    }

    private boolean containsFinalKeywordOrAlias(String text, AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (blank(text) || draft == null) {
            return false;
        }

        if (!blank(draft.getFinalAnswer()) && containsExactAnswerValue(text, draft.getFinalAnswer())) {
            return true;
        }

        if (draft.getFinalAnswerKeywords() != null) {
            for (String keyword : draft.getFinalAnswerKeywords()) {
                if (!blank(keyword) && containsExactAnswerValue(text, keyword)) {
                    return true;
                }
            }
        }

        if (draft.getFinalAnswerAliases() != null) {
            for (String alias : draft.getFinalAnswerAliases()) {
                if (blank(alias)) {
                    continue;
                }

                if (!alias.trim().startsWith("KW:") && containsExactAnswerValue(text, alias)) {
                    return true;
                }

                if (alias.trim().startsWith("KW:")) {
                    String raw = alias.trim().substring(3);
                    String[] parts = raw.split("\\|");

                    for (String part : parts) {
                        if (!blank(part) && containsExactAnswerValue(text, part)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean containsExactAnswerValue(String text, String value) {
        if (blank(text) || blank(value)) {
            return false;
        }

        String normalizedText = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalizedValue = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String compactValue = compact(normalizedValue);

        if (compactValue.length() <= 2) {
            if (same(normalizedText, normalizedValue)) {
                return true;
            }
            for (String token : normalizedText.split("[\\s\\p{Punct}·|/]+")) {
                if (same(token, normalizedValue)) {
                    return true;
                }
            }
            return false;
        }

        return normalizedText.contains(normalizedValue)
                || compact(normalizedText).contains(compactValue);
    }

    private void sanitizeCardKeywordLeaks(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings
    ) {
        if (draft == null) {
            return;
        }

        boolean changed = false;

        if (draft.getSuspects() != null) {
            for (AiEpisodeDraftResponse.SuspectDraft card : draft.getSuspects()) {
                if (containsFinalKeywordOrAlias(card.getDisplayName(), draft)
                        || containsAnyApprovedFinalKeyword(card.getDisplayName(), request)) {
                    card.setDisplayName("기록 전달 관계자");
                    changed = true;
                }
                if (containsFinalKeywordOrAlias(card.getShortDescription(), draft)
                        || containsAnyApprovedFinalKeyword(card.getShortDescription(), request)) {
                    card.setShortDescription("동선 기록의 일부를 알고 있지만 핵심 내용을 직접 밝히지 않는 관계자입니다.");
                    changed = true;
                }
                if (containsFinalKeywordOrAlias(card.getSuspiciousPoint(), draft)
                        || containsAnyApprovedFinalKeyword(card.getSuspiciousPoint(), request)) {
                    card.setSuspiciousPoint("진술과 이동 기록 사이에 확인이 필요한 간접적인 차이가 있습니다.");
                    changed = true;
                }
                if (containsFinalKeywordOrAlias(card.getAlibiSummary(), draft)
                        || containsAnyApprovedFinalKeyword(card.getAlibiSummary(), request)) {
                    card.setAlibiSummary("해금 자료 카드와 현장 동선을 함께 대조해야 진술의 신뢰도를 판단할 수 있습니다.");
                    changed = true;
                }
            }
        }

        if (draft.getEvidences() != null) {
            for (AiEpisodeDraftResponse.EvidenceDraft card : draft.getEvidences()) {
                if (containsFinalKeywordOrAlias(card.getTitle(), draft)
                        || containsAnyApprovedFinalKeyword(card.getTitle(), request)) {
                    card.setTitle("간접 기록 자료");
                    changed = true;
                }
                if (containsFinalKeywordOrAlias(card.getTextSummary(), draft)
                        || containsAnyApprovedFinalKeyword(card.getTextSummary(), request)) {
                    card.setTextSummary("정답 값을 직접 밝히지 않고 형태, 재질, 용도 중 일부 특징만 남긴 해금 자료입니다.");
                    changed = true;
                }
            }
        }

        if (changed) {
            warnings.add("중간 카드에서 최종 정답 값 노출 위험이 감지되어 간접 단서로 보정되었습니다.");
        }
    }

    private String safeRewardClue(String role, int index) {
        String normalizedRole = normalize(role);

        if ("ANSWER_HINT".equals(normalizedRole)) {
            return "answer-clue-" + (index + 1);
        }

        if ("DESTINATION_HINT".equals(normalizedRole) || "FINAL".equals(normalizedRole) || "FINAL_PLACE".equals(normalizedRole)) {
            return "destination-clue-" + (index + 1);
        }

        return "story-clue-" + (index + 1);
    }

    private String safeStoryText(String role) {
        String normalizedRole = normalize(role);

        if ("START".equals(normalizedRole)) {
            return "요원, 미션 파일이 개방되었다. 현장 근거와 작전 기록을 분리해 첫 단서의 방향을 확인하게.";
        }

        if ("ANSWER_HINT".equals(normalizedRole)) {
            return "요원, 이 지점의 기록은 최종 진실의 한 역할을 좁히는 보조 증거다. 현장 근거와 미션 파일을 대조하게.";
        }

        if ("DESTINATION_HINT".equals(normalizedRole)) {
            return "요원, 이 지점의 단서는 다음 동선과 숨겨진 위치의 특징을 좁히는 자료다. 장소명 자체가 아니라 방향과 조건을 확인하게.";
        }

        if ("FINAL".equals(normalizedRole) || "FINAL_PLACE".equals(normalizedRole)) {
            return "요원, 이 지점에서는 지금까지 해금한 자료를 조용히 대조하고 서로 다른 흔적의 연결을 확인하게.";
        }

        return "요원, 현장 근거와 미션 파일을 대조해 이 지점의 역할을 확인하게.";
    }

    private String safeQuestionText(String role) {
        String normalizedRole = normalize(role);

        if ("FINAL".equals(normalizedRole) || "FINAL_PLACE".equals(normalizedRole)) {
            return "요원, 해금된 미션 파일 카드를 조합하라. 장소명을 직접 말하지 말고, 각 단서가 가리키는 역할과 조건만 보고하게.";
        }

        if ("ANSWER_HINT".equals(normalizedRole)) {
            return "요원, 이 지점의 현장 근거와 미션 메모를 대조하라. 최종 정답이 아니라 이 단서가 맡은 역할을 확인하게.";
        }

        if ("DESTINATION_HINT".equals(normalizedRole)) {
            return "요원, 이 지점이 가리키는 동선 조건을 확인하라. 장소명 대신 이동 단서의 특징을 보고하게.";
        }

        return "요원, 이 지점의 현장 근거를 미션 파일과 대조하라. 확인 가능한 짧은 단서어만 보고하게.";
    }

    private String safeHint(int index) {
        return switch (index) {
            case 0 -> "먼저 현장 근거와 미션 파일 기록을 분리해서 보게.";
            case 1 -> "이 단서는 최종 정답 자체가 아니라 특정 역할을 좁히는 보조 근거일세.";
            default -> "보상 단서와 이전에 해금된 증거 카드를 함께 대조하되, 정답 키워드를 그대로 말하지 말게.";
        };
    }


    private void ensureMinimumSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> warnings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = draft.getSuspects() == null ? new ArrayList<>() : new ArrayList<>(draft.getSuspects());
        List<AiEpisodeDraftResponse.SuspectDraft> defaults = List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("의뢰인")
                        .displayName("봉투를 맡긴 기록 중개인")
                        .shortDescription("미션을 의뢰했지만 자신이 받은 봉투의 출처를 끝까지 숨기는 인물입니다.")
                        .relationToVictim("사라진 문서의 최초 전달자")
                        .suspiciousPoint("문서가 사라지기 전 마지막으로 봉투의 봉인을 확인했고, 봉투 안 물건의 정확한 이름을 알고 있습니다.")
                        .alibiSummary("의뢰 시간에는 다른 장소에 있었다고 주장하지만, 정답 힌트 카드의 봉인 문양 설명과 그의 진술이 맞물립니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("정리관")
                        .displayName("기록 순서를 바꾼 보관 담당자")
                        .shortDescription("문서와 사진의 순서를 정리하던 중 일부 자료를 다른 파일철로 옮긴 인물입니다.")
                        .relationToVictim("미션 자료를 분류하던 내부 협력자")
                        .suspiciousPoint("사진, 메모, 목격 기록의 시간 순서를 바꾸면 최종 목적지가 전혀 다른 곳처럼 보이게 만들 수 있습니다.")
                        .alibiSummary("자료실에만 있었다고 주장하지만, 목적지 힌트 카드 하나가 그의 이동 경로와 충돌합니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("전달자")
                        .displayName("마지막 쪽지를 옮긴 연락책")
                        .shortDescription("최종 장소를 직접 말하지 않고 방향과 물건의 특징만 남긴 연락책입니다.")
                        .relationToVictim("마지막 단서를 운반한 증언자")
                        .suspiciousPoint("정답을 숨긴 인물이라기보다, 정답을 보호하기 위해 일부 힌트를 일부러 흐리게 남겼을 가능성이 있습니다.")
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
            warnings.add("관계자 카드가 3개 미만이라 스토리 역할이 분명한 기본 관계자 카드로 보강했습니다.");
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

    private void addFinding(
            List<AiEpisodeDraftValidationResponse.Finding> findings,
            String severity,
            String code,
            String message,
            Integer missionOrder
    ) {
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                .severity(severity)
                .code(code)
                .message(normalizeFindingMessage(code, message))
                .missionOrder(missionOrder)
                .build());
    }

    private String normalizeFindingMessage(String code, String message) {
        String normalizedCode = normalize(code);

        return switch (normalizedCode) {
            case "MISSING_TITLE" ->
                    "에피소드 제목이 필요합니다.";
            case "MISSING_FINAL_ANSWER" ->
                    "최종 정답이 필요합니다.";
            case "INVALID_FINAL_ANSWER_TYPE" ->
                    "finalAnswerType은 허용된 최종 정답 유형 중 하나여야 합니다.";
            case "ABSTRACT_FINAL_ANSWER" ->
                    "최종 정답이 너무 추상적입니다. 플레이어가 입력할 수 있는 구체적인 픽션 정답이어야 합니다.";
            case "REAL_HISTORICAL_PERSON_IN_FINAL_ANSWER" ->
                    "최종 정답에 실존 역사 인물이 들어가면 안 됩니다.";
            case "REAL_NAME_LIKE_CULPRIT" ->
                    "CULPRIT 유형의 정답은 실존 인물명처럼 보이면 안 됩니다. 가상의 역할명이나 별칭을 사용하세요.";
            case "REAL_HISTORICAL_PERSON_IN_FINAL_ALIAS" ->
                    "최종 정답 별칭에 실존 역사 인물이 들어가면 안 됩니다.";

            case "MISSING_FINAL_KEYWORDS" ->
                    "출력 finalAnswerKeywords에 관리자 승인 정답 키워드가 포함되어야 합니다.";
            case "APPROVED_KEYWORD_MISSING_IN_OUTPUT" ->
                    withMessageDetail("관리자 승인 정답 키워드가 finalAnswerKeywords에서 누락되었습니다.", message);
            case "APPROVED_KEYWORD_MISSING_IN_FINAL_ANSWER" ->
                    withMessageDetail("최종 정답 문장에 관리자 승인 키워드가 포함되어야 합니다.", message);
            case "MISSING_KEYWORD_CONTRACT_ALIAS" ->
                    "finalAnswerAliases에 KW:키워드1|키워드2 형식의 정답 키워드 계약값이 필요합니다.";
            case "FINAL_KEYWORD_IN_TITLE" ->
                    "에피소드 제목에 최종 정답 키워드가 노출되면 안 됩니다.";
            case "FINAL_KEYWORD_IN_SUBTITLE" ->
                    "부제목에 최종 정답 키워드가 노출되면 안 됩니다.";
            case "FINAL_KEYWORD_IN_SYNOPSIS" ->
                    "fictionSynopsis에 최종 정답 키워드가 노출되면 안 됩니다.";
            case "FINAL_KEYWORD_IN_FINAL_QUESTION" ->
                    "finalQuestion은 정확한 키워드가 아니라 슬롯 라벨로 질문해야 합니다.";

            case "FINAL_ANSWER_IS_PLACE" ->
                    "최종 정답이 실제 장소명과 같으면 안 됩니다.";
            case "MISSING_MISSIONS" ->
                    "AI 초안에는 최소 1개 이상의 미션이 필요합니다.";
            case "INVALID_MISSION_COUNT" ->
                    "미션 개수는 선택한 장소 개수와 정확히 일치해야 합니다.";
            case "MISSION_COUNT_BELOW_POLICY_MIN" ->
                    "미션 개수가 missionPolicy의 최소 개수보다 적습니다.";
            case "MISSION_COUNT_ABOVE_POLICY_MAX" ->
                    "미션 개수가 missionPolicy의 최대 개수를 초과했습니다.";
            case "INVALID_START_COUNT" ->
                    "START 미션 개수가 missionPolicy.startCount와 일치해야 합니다.";
            case "INVALID_FINAL_PLACE_COUNT" ->
                    "내부 FINAL 미션 개수가 missionPolicy.finalCount와 일치해야 합니다.";
            case "LOW_ANSWER_HINT_COUNT" ->
                    "ANSWER_HINT 미션 수가 missionPolicy.answerHintRatio 기준보다 적습니다.";
            case "LOW_DESTINATION_HINT_COUNT" ->
                    "DESTINATION_HINT 미션 수가 missionPolicy.destinationHintRatio 기준보다 적습니다.";

            case "PUBLIC_FINAL_MARKER_EXPOSED" ->
                    "publicMarkerType에는 FINAL을 노출하면 안 됩니다.";
            case "FINAL_PUBLIC_MARKER_INVALID" ->
                    "내부 FINAL 미션의 publicMarkerType은 DESTINATION_HINT여야 합니다.";
            case "FINAL_PLACE_PUBLIC_TYPE_INVALID" ->
                    "실제 최종 장소가 START로 공개되면 안 됩니다.";
            case "FINAL_PLACE_REVEAL_IN_STORY" ->
                    "미션 storyText에서 최종 장소 표현이 노출되면 안 됩니다.";
            case "MISSING_STORY_TEXT" ->
                    "조사 지점 storyText가 비어 있습니다.";

            case "MISSING_PUZZLE_QUESTION" ->
                    "퍼즐 질문이 필요합니다.";
            case "MISSING_PUZZLE_ANSWER" ->
                    "퍼즐 정답이 필요합니다.";
            case "MISSING_HINTS" ->
                    "각 퍼즐에는 힌트 3개가 필요합니다.";
            case "MISSING_REWARD_CLUE" ->
                    "미션 완료 후 제공할 rewardClue가 필요합니다.";
            case "GENERIC_REWARD_CLUE" ->
                    "rewardClue는 일반적인 설명이 아니라 실제 추론에 쓰이는 구체적인 간접 단서여야 합니다.";
            case "MISSING_GROUND_RULE" ->
                    "groundRule에는 어떤 입력 필드를 근거로 퍼즐을 만들었는지 설명해야 합니다.";
            case "QUESTION_USES_PLACE_NAME_TEXT" ->
                    "퍼즐은 장소명이나 상호명에서 글자·음절을 추출하는 방식이면 안 됩니다.";
            case "PUZZLE_ANSWER_IS_PLACE_NAME" ->
                    "퍼즐 정답이 장소명 또는 장소명 일부이면 안 됩니다.";
            case "NUMBER_LOCK_WITHOUT_PROVIDED_NUMBER" ->
                    "NUMBER_LOCK 퍼즐은 관리자/TourAPI 입력에 있는 숫자만 사용할 수 있습니다.";
            case "NUMBER_LOCK_ANSWER_NOT_PROVIDED" ->
                    "NUMBER_LOCK 정답은 해당 장소의 sourceInput.place.numbers에 있는 값이어야 합니다.";
            case "FINAL_ANSWER_IN_QUESTION" ->
                    "퍼즐 질문에 최종 정답이 직접 포함되어 있습니다.";
            case "FULL_FINAL_ANSWER_AS_REWARD" ->
                    "rewardClue에 전체 최종 정답이 포함되어 있습니다.";
            case "PUZZLE_ANSWER_IS_FINAL_KEYWORD" ->
                    "퍼즐 정답이 최종 정답 키워드와 같으면 안 됩니다.";
            case "FINAL_KEYWORD_IN_QUESTION" ->
                    "퍼즐 질문에 최종 정답 키워드가 노출되면 안 됩니다.";
            case "FINAL_KEYWORD_IN_HINT" ->
                    "힌트에 최종 정답 키워드가 직접 노출되면 안 됩니다.";
            case "GENERIC_PUZZLE_ANSWER" ->
                    "퍼즐 정답이 너무 일반적이거나 차단된 값입니다.";
            case "PUZZLE_TYPE_NOT_ALLOWED" ->
                    "puzzlePolicy에서 허용하지 않은 퍼즐 유형입니다.";
            case "TEXT_EXTRACTION_PUZZLE_FORBIDDEN" ->
                    "글자·음절·부분 문자열 추출 방식의 퍼즐은 사용할 수 없습니다.";
            case "DUPLICATE_PUZZLE_ANSWER" ->
                    withMessageDetail("중복된 퍼즐 정답이 있습니다.", message);

            case "MISSING_DEDUCTION_SECRET_FACTS" ->
                    "최종 추론 채팅에 사용할 deductionSecretFacts가 필요합니다.";
            case "MISSING_FORBIDDEN_FINAL_REVEAL" ->
                    "deductionForbiddenReveals에는 최종 정답이 포함되어야 합니다.";
            case "LOW_SUSPECT_COUNT" ->
                    "관계자 카드는 최소 3개 이상을 권장합니다.";
            case "LOW_EVIDENCE_COUNT" ->
                    "해금 자료 카드는 대부분의 미션 지점을 커버하는 것이 좋습니다.";
            case "GENERIC_CHARACTER_CARD_TEXT" ->
                    "관계자 카드에는 구체적인 행동이나 진술의 모순이 필요합니다.";
            case "GENERIC_EVIDENCE_CARD_TEXT" ->
                    "해금 자료 카드에는 구체적인 흔적, 조건 또는 관계가 필요합니다.";
            case "IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING" ->
                    "이미지 프롬프트에는 readable text, 한글 글자, 라벨, 필기 금지 조건이 명시되어야 합니다.";
            case "LOW_SLOT_CLUE_COVERAGE" ->
                    withMessageDetail("정답 슬롯을 뒷받침하는 rewardClue 수가 부족합니다.", message);
            case "STORY_OBJECTIVE_MISMATCH" ->
                    "fictionSynopsis의 목표와 finalQuestion/finalAnswer가 일치하지 않습니다. 시놉시스가 요구한 모든 해결 요소를 질문과 정답에 포함해야 합니다.";

            default -> blank(message) ? "검증 항목을 확인해야 합니다." : message;
        };
    }

    private String withMessageDetail(String koreanMessage, String originalMessage) {
        if (blank(originalMessage)) {
            return koreanMessage;
        }

        int colonIndex = originalMessage.indexOf(':');

        if (colonIndex >= 0 && colonIndex + 1 < originalMessage.length()) {
            return koreanMessage + " (" + originalMessage.substring(colonIndex + 1).trim() + ")";
        }

        int equalsIndex = originalMessage.indexOf('=');

        if (equalsIndex >= 0 && equalsIndex + 1 < originalMessage.length()) {
            return koreanMessage + " (" + originalMessage.substring(equalsIndex + 1).trim() + ")";
        }

        return koreanMessage;
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
            case 1 -> "첫 현장 사진 봉투";
            case 2 -> "찢긴 동선 메모";
            case 3 -> "엇갈린 목격 기록";
            case 4 -> "렌즈 파편 기록";
            case 5 -> "붉은 인장 스케치";
            case 6 -> "목적지 암호 메모";
            case 7 -> "최종 동선 기록";
            case 8 -> "봉인된 명함";
            default -> "최종 결론 보조 파일";
        };
    }


    private String defaultEvidenceSummary(int order, AiEpisodeDraftRequest.PlaceInput place) {
        String name = place == null || blank(place.getName()) ? "이 지점" : place.getName();

        return switch (order) {
            case 1 ->
                    name + "이 미션의 시작 지점임을 보여주는 기본 자료입니다.";
            case 2 ->
                    name + "의 동선과 누락된 흔적을 연결하는 자료입니다.";
            case 3 ->
                    "서로 맞지 않는 기록을 비교하게 만드는 목격 자료입니다.";
            case 4 ->
                    "핵심 자료의 정체를 좁혀주는 보조 자료입니다.";
            case 5 ->
                    "관계자의 행동 이유와 미션 흐름을 연결하는 단서입니다.";
            case 6 ->
                    "장소명을 직접 말하지 않고 목적지 조건을 좁혀주는 메모입니다.";
            case 7 ->
                    "마지막 동선을 다시 구성하는 데 필요한 기록입니다.";
            case 8 ->
                    "최종 결론 전에 확인해야 할 봉인된 파일입니다.";
            default ->
                    "모은 단서를 조합하는 데 필요한 보조 자료입니다.";
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

    private List<String> withKeywordContract(List<String> aliases, List<String> keywords) {
        List<String> values = new ArrayList<>();
        if (aliases != null) {
            aliases.stream()
                    .filter(value -> value != null && !value.isBlank() && !value.startsWith("KW:"))
                    .map(String::trim)
                    .forEach(values::add);
        }
        List<String> required = keywords == null ? List.of() : keywords.stream()
                                                               .filter(value -> value != null && !value.isBlank())
                                                               .map(String::trim)
                                                               .distinct()
                                                               .toList();
        if (!required.isEmpty()) {
            values.add("KW:" + String.join("|", required));
        }
        return values;
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

    private boolean usesProvidedNumber(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place) {
        if (mission == null || place == null || place.getNumbers() == null || blank(mission.getAnswer())) {
            return false;
        }
        return place.getNumbers().stream()
                .filter(value -> !blank(value))
                .anyMatch(value -> same(value, mission.getAnswer()));
    }

    private List<String> approvedFinalSlotLabels(AiEpisodeDraftRequest request, List<String> keywords) {
        List<String> labels = new ArrayList<>();
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                if (item == null || blank(item.getLabel())) {
                    continue;
                }
                String label = item.getLabel().trim();
                boolean exposesKeyword = keywords.stream().anyMatch(keyword -> textContains(label, keyword));
                if (!exposesKeyword && labels.stream().noneMatch(existing -> same(existing, label))) {
                    labels.add(label);
                }
            }
        }
        while (labels.size() < keywords.size()) {
            labels.add("핵심 요소 " + (labels.size() + 1));
        }
        return labels;
    }

    private void sanitizeGenericRewardClue(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request,
            String role,
            int index,
            List<String> warnings) {
        if (mission == null) {
            return;
        }
        String reward = compact(mission.getRewardClue());
        boolean unsafe = reward.isBlank()
                || isGenericRewardKey(reward)
                || isGenericBasisLabel(reward)
                || isLowQualityGenericValue(reward)
                || isTooShortRewardClue(reward)
                || reward.contains("검수필요")
                || containsAnyApprovedFinalKeyword(mission.getRewardClue(), request);
        if (!unsafe) {
            return;
        }

        String basis = bestPuzzleBasis(place);
        if (isBadPuzzleAnswerBasis(basis, place) || containsAnyApprovedFinalKeyword(basis, request)) {
            basis = "겹쳐 표시된 방향";
        }
        String suffix = switch (normalize(role)) {
            case "ANSWER_HINT" -> "이 가리키는 역할의 흔적";
            case "DESTINATION_HINT", "FINAL", "FINAL_PLACE" -> "을 따라 이어지는 방향의 흔적";
            default -> " 주변에 반복된 배열의 흔적";
        };
        mission.setRewardClue(basis + suffix);
        warnings.add("Mission " + (index + 1) + " reward clue was normalized; review before publishing.");
    }

    private String naturalFinalSynopsis(List<String> labels) {
        if (labels.size() >= 2) {
            return "여러 장소에 흩어진 흔적을 따라 " + String.join(", ", labels)
                    + "에 해당하는 단서를 밝혀, 왜 모든 흔적이 하나의 경로로 이어졌는지 완성해야 합니다.";
        }
        return "여러 장소에 흩어진 흔적을 대조해 " + labels.get(0) + "에 해당하는 핵심 진실을 밝혀야 합니다.";
    }

    private String naturalFinalQuestion(List<String> labels) {
        if (labels.size() == 2) {
            return "흩어진 단서를 종합하면, 사라진 대상의 정체와 그것이 향한 곳은 어디인가?";
        }
        if (labels.size() >= 3) {
            return "관계자의 행동과 남겨진 매체, 마지막 확인 조건을 연결하면 이번 미션의 전말은 무엇인가?";
        }
        return "모든 흔적을 하나로 연결했을 때 드러나는 이번 미션의 진실은 무엇인가?";
    }

    private String naturalFinalAnswer(List<String> keywords) {
        if (keywords.size() == 1) {
            return "모든 흔적이 가리킨 진실은 " + keywords.get(0) + "이었다.";
        }
        if (keywords.size() == 2) {
            return withSubject(keywords.get(0)) + " 마지막 흔적이 가리킨 " + keywords.get(1) + "에 숨겨져 있었다.";
        }
        return withSubject(keywords.get(0)) + " " + withObject(keywords.get(1)) + " 옮겼고, "
                + withSubject(String.join(", ", keywords.subList(2, keywords.size())))
                + " 성립하는 순간 기록을 확인하려 했다.";
    }

    private boolean isLowQualityGenericValue(String value) {
        String compactValue = compact(value).replaceAll("\\d+$", "");
        return Set.of(
                "동선확인", "증거확인", "최종검토", "기록확인", "단서확인", "자료확인",
                "현장확인", "미션확인", "흐름확인", "연결단서", "보조자료", "핵심자료",
                "관계단서", "작전개시"
        ).contains(compactValue)
                || compactValue.startsWith("story-clue-")
                || compactValue.startsWith("answer-clue-")
                || compactValue.startsWith("destination-clue-");
    }

    private boolean isTooShortRewardClue(String value) {
        String compactValue = compact(value);
        return !compactValue.matches("\\d+")
                && compactValue.codePointCount(0, compactValue.length()) < 8;
    }

    private void strengthenDeductionCards(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null ? List.of() : draft.getMissions();
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = draft.getSuspects() == null ? List.of() : draft.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            AiEpisodeDraftResponse.MissionDraft mission = missions.isEmpty() ? null : missions.get(i % missions.size());
            String clue = mission == null ? "서로 다른 방향으로 반복된 흔적" : mission.getRewardClue();
            if (isGenericCardText(suspect.getShortDescription())) {
                suspect.setShortDescription("이 인물은 다른 이들이 알기 전부터 " + clue + "의 존재를 알고 있었지만, 그 출처만은 끝까지 숨겼습니다.");
            }
            if (isGenericCardText(suspect.getSuspiciousPoint())) {
                suspect.setSuspiciousPoint("그의 진술에서는 " + clue + "이 발견된 시점보다 앞서 같은 특징을 언급한 모순이 드러납니다.");
            }
            suspect.setImagePrompt(ensureTextFreeImagePrompt(suspect.getImagePrompt()));
        }

        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = draft.getEvidences() == null ? List.of() : draft.getEvidences();
        for (int i = 0; i < evidences.size(); i++) {
            AiEpisodeDraftResponse.EvidenceDraft evidence = evidences.get(i);
            AiEpisodeDraftResponse.MissionDraft mission = missions.isEmpty() ? null : missions.get(i % missions.size());
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces() == null || request.getPlaces().isEmpty()
                    ? null : request.getPlaces().get(i % request.getPlaces().size());
            if (isGenericCardText(evidence.getTextSummary())) {
                String basis = bestPuzzleBasis(place);
                evidence.setTextSummary(basis + " 주변의 마모 자국과 접힌 가장자리는 서로 떨어진 두 기록이 같은 방향을 가리켰음을 보여 줍니다.");
            }
            evidence.setImagePrompt(ensureTextFreeImagePrompt(evidence.getImagePrompt()));
        }
        warnings.add("관계자 카드와 해금 자료 카드가 구체적인 행동, 모순, 흔적 중심으로 보강되었습니다.");
    }

    private boolean isGenericCardText(String value) {
        if (!blank(value) && (value.contains("서로 맞지 않는 기록을 비교")
                || value.contains("목격 자료입니다")
                || value.contains("정답 값을 직접 밝히지 않고"))) {
            return true;
        }
        return blank(value) || containsAny(value,
                "다음 판단을 돕", "핵심 자료의 정체를 좁", "미션 흐름을 연결",
                "동선과 누락된 흔적을 연결", "보조 자료", "기본 관계자 카드", "연결 가능성이 있는");
    }

    private String withSubject(String value) {
        return value + (hasFinalConsonant(value) ? "이" : "가");
    }

    private String withObject(String value) {
        return value + (hasFinalConsonant(value) ? "을" : "를");
    }

    private boolean hasFinalConsonant(String value) {
        if (blank(value)) {
            return false;
        }
        char last = value.trim().charAt(value.trim().length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
    }

    private boolean hasTextFreeImageConstraints(String prompt) {
        if (blank(prompt)) {
            return false;
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        return normalized.contains("no readable text")
                && normalized.contains("no korean letters")
                && normalized.contains("no labels")
                && normalized.contains("no handwriting");
    }

    private String ensureTextFreeImagePrompt(String prompt) {
        String base = blank(prompt)
                ? "Flat 2D Korean webtoon and printed storybook mission archive card, muted earth tones, matte paper grain."
                : prompt.trim();
        if (hasTextFreeImageConstraints(base)) {
            return base;
        }
        return base + " No readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark.";
    }

    private AiEpisodeDraftRequest.PlaceInput sourcePlace(AiEpisodeDraftRequest sourceInput, int missionOrder) {
        if (sourceInput == null || sourceInput.getPlaces() == null || missionOrder <= 0 || missionOrder > sourceInput.getPlaces().size()) {
            return null;
        }
        return sourceInput.getPlaces().get(missionOrder - 1);
    }

    private String selectedGenreName(AiEpisodeDraftRequest request) {
        if (request == null) {
            return "야외 스토리 미션";
        }

        if (!blank(request.getSelectedGenreName())) {
            return request.getSelectedGenreName().trim();
        }

        if (!blank(request.getSelectedGenreId())) {
            return request.getSelectedGenreId().trim();
        }

        if (!blank(request.getTheme())) {
            return request.getTheme().trim();
        }

        return "야외 스토리 미션";
    }

    private String selectedGenreId(AiEpisodeDraftRequest request) {
        if (request == null || blank(request.getSelectedGenreId())) {
            return "CUSTOM";
        }
        return request.getSelectedGenreId().trim();
    }

    private List<String> approvedFinalKeywords(AiEpisodeDraftRequest request) {
        if (request == null) {
            return List.of();
        }

        if (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty()) {
            return request.getFinalAnswerKeywordItems().stream()
                    .map(AiEpisodeDraftRequest.AnswerKeywordInput::getKeyword)
                    .filter(value -> value != null && !value.isBlank())
                    .map(this::normalizeAnswerKeywordValue)
                    .filter(value -> !blank(value))
                    .distinct()
                    .toList();
        }

        if (request.getFinalAnswerKeywords() != null && !request.getFinalAnswerKeywords().isEmpty()) {
            return request.getFinalAnswerKeywords().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(this::normalizeAnswerKeywordValue)
                    .filter(value -> !blank(value))
                    .distinct()
                    .toList();
        }

        return List.of();
    }

    private List<String> approvedFinalKeywordVariants(AiEpisodeDraftRequest request) {
        if (request == null || request.getFinalAnswerKeywordItems() == null
                || request.getFinalAnswerKeywordItems().isEmpty()) {
            return approvedFinalKeywords(request);
        }
        return request.getFinalAnswerKeywordItems().stream()
                .filter(item -> item != null)
                .flatMap(item -> {
                    List<String> values = new ArrayList<>();
                    values.add(item.getKeyword());
                    if (item.getAliases() != null) {
                        values.addAll(item.getAliases());
                    }
                    return values.stream();
                })
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeAnswerKeywordValue)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private boolean hasAnyApprovedFinalKeywordInput(AiEpisodeDraftRequest request) {
        if (request == null) {
            return false;
        }

        boolean hasItems = request.getFinalAnswerKeywordItems() != null
                && request.getFinalAnswerKeywordItems().stream()
                .anyMatch(item -> item != null && !blank(item.getKeyword()));

        boolean hasLegacy = request.getFinalAnswerKeywords() != null
                && request.getFinalAnswerKeywords().stream()
                .anyMatch(value -> value != null && !value.isBlank());

        return hasItems || hasLegacy;
    }

    private String firstUsablePuzzleAnswer(List<String> values, AiEpisodeDraftRequest.PlaceInput place) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .filter(value -> !isBadPuzzleAnswerBasis(value, place))
                .findFirst()
                .orElse(null);
    }

    private boolean isBadPuzzleAnswerBasis(String value, AiEpisodeDraftRequest.PlaceInput place) {
        if (blank(value)) {
            return true;
        }

        String compactValue = compact(value);

        if (isGenericBasisLabel(compactValue)) {
            return true;
        }

        if (place != null && isPlaceNameAnswer(compactValue, place.getName())) {
            return true;
        }

        if (compactValue.length() < 2) {
            return true;
        }

        if (compactValue.length() > 12) {
            return true;
        }

        return false;
    }

    private String resolvePuzzleAnswerSource(String answer, AiEpisodeDraftRequest.PlaceInput place) {
        if (blank(answer) || place == null) {
            return "FICTION_SAFE";
        }

        if (place.getNumbers() != null && place.getNumbers().stream().anyMatch(value -> same(value, answer))) {
            return "NUMBER";
        }

        if (place.getVisibleElements() != null && place.getVisibleElements().stream().anyMatch(value -> same(value, answer))) {
            return "VISIBLE_ELEMENT";
        }

        if (place.getKeywords() != null && place.getKeywords().stream().anyMatch(value -> same(value, answer))) {
            return "KEYWORD";
        }

        if (!blank(place.getAdminMemo()) && textContains(place.getAdminMemo(), answer)) {
            return "ADMIN_MEMO";
        }

        if (!blank(place.getDescription()) && textContains(place.getDescription(), answer)) {
            return "DESCRIPTION";
        }

        return "FICTION_SAFE";
    }
}
