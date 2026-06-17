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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

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

    private static final Set<String> BAD_PLAN_PLACEHOLDERS = Set.of(
            "관리자 현장 메모 필요",
            "현장 메모 필요",
            "관리자 입력 필요",
            "검수필요",
            "확인필요",
            "데이터 보강 필요",
            "관찰 데이터 부족",
            "장소 관찰 데이터 보강",
            "정답 키워드 후보를 만들 현장 관찰 요소가 부족합니다",
            "실제 운영 전",
            "관리자 메모로 보강",
            "주변 확인 후보",
            "주변 상권",
            "문화 후보지",
            "후보지입니다",
            "Kakao Local 기준",
            "현장 관찰 요소"
    );

    private static final Set<String> AI_INFERRED_SOURCE_TYPES = Set.of(
            "AI_INFERRED_OBSERVATION",
            "AI_INFERRED_STORY_OBJECT",
            "AI_INFERRED_ROUTE_FEATURE"
    );

    private static final List<PlanSlot> FIXED_FINAL_ANSWER_SLOTS = List.of(
            new PlanSlot("RELATED_PERSON", "관련자", "최종 정답에 반드시 포함될 픽션 관련자 또는 역할"),
            new PlanSlot("ANSWER_CLUE", "핵심 단서", "최종 정답을 성립시키는 핵심 물건, 숫자, 문구, 조건 중 하나"),
            new PlanSlot("FINAL_DESTINATION", "장소", "내부 최종 목적지의 실제 장소명")
    );

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(geminiRequestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    private static SimpleClientHttpRequestFactory geminiRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(170_000);
        return factory;
    }

    public AiEpisodeDraftResponse createGeminiDraft(AiEpisodeDraftRequest request) {
        validateRequest(request);
        if (request.getFinalAnswerKeywordItems() == null
                || request.getFinalAnswerKeywordItems().size() != 3
                || request.getFinalAnswerKeywordItems().stream().anyMatch(item ->
                        item == null || blank(item.getSlotId()) || blank(item.getLabel()) || blank(item.getKeyword()))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AI_PLAN_REVIEW_REQUIRED",
                    "근거가 확인된 최종 정답 키워드 3개가 필요합니다. 현장 관찰 데이터를 보강하고 plan을 다시 생성하세요."
            );
        }
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
            return sanitizePlanResponse(root, request);

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
                        .sourceType(node.path("sourceType").asText(""))
                        .sourcePlaceName(node.path("sourcePlaceName").asText(""))
                        .sourceText(node.path("sourceText").asText(""))
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

    private String normalizePlanGenreId(String genreId, AiEpisodeDraftRequest request) {
        if (request == null || request.getGenreCatalog() == null || request.getGenreCatalog().isEmpty()) {
            return blank(genreId) ? "CUSTOM" : genreId.trim();
        }
        String matched = request.getGenreCatalog().stream()
                .filter(genre -> genre != null && !blank(genre.getGenreId()))
                .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreId)
                .filter(id -> same(id, genreId))
                .findFirst()
                .orElse("");
        String placeFit = genreIdByPlaceSignals(request);
        if (blank(matched)) {
            return blank(placeFit) ? firstGenreId(request) : placeFit;
        }
        if (same(matched, "TREASURE_HUNT") && !treasureGenreFits(request) && !blank(placeFit)) {
            return placeFit;
        }
        return matched;
    }

    private AiEpisodePlanResponse sanitizePlanResponse(
            JsonNode geminiPlan,
            AiEpisodeDraftRequest request) {
        List<String> warnings = new ArrayList<>();

        String requestedGenreName = geminiPlan.path("selectedGenreName").asText(
                geminiPlan.path("selectedGenre").asText("")
        );

        String genreId = normalizePlanGenreId(
                geminiPlan.path("selectedGenreId").asText(""),
                request
        );

        List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots =
                fixedFinalAnswerSlots();
        warnings.add("PLAN_SLOTS_FIXED_RELATED_PERSON_ANSWER_CLUE_FINAL_DESTINATION");

        List<AiEpisodePlanResponse.AnswerKeyword> geminiKeywordItems =
                parsePlanKeywords(geminiPlan.path("finalAnswerKeywords"));

        normalizePlanSlots(answerSlots, geminiKeywordItems, warnings);

        List<AiEpisodePlanResponse.AnswerKeyword> keywordItems =
                sanitizeGeminiFinalAnswerKeywordItems(
                        geminiKeywordItems,
                        answerSlots,
                        request,
                        warnings
                );

        if (keywordItems.size() < answerSlots.size()) {
            List<AiEpisodePlanResponse.AnswerKeyword> synthesized =
                    synthesizeFictionPlanKeywordItems(
                            answerSlots,
                            request,
                            keywordItems,
                            warnings
                    );

            keywordItems = mergePlanKeywordItems(answerSlots, keywordItems, synthesized);
        }

        keywordItems = enforceFinalDestinationKeyword(answerSlots, keywordItems, request, warnings);
        keywordItems = enforceConcreteAnswerClueKeyword(answerSlots, keywordItems, request, warnings);

        String genreName = resolveSelectedGenreNameFromCatalog(
                genreId,
                requestedGenreName,
                request,
                keywordItems,
                warnings
        );

        String questionGuide = normalizeFinalQuestionGuide(
                geminiPlan.path("finalQuestionGuide").asText(""),
                answerSlots,
                request
        );

        if (isBadPlanPlaceholder(questionGuide)
                || !containsAllApprovedKeywordsInGuide(questionGuide, keywordItems)) {
            questionGuide = buildFinalAnswerGuideFromKeywords(answerSlots, keywordItems);
            warnings.add("PLAN_QUESTION_GUIDE_REWRITTEN_WITH_KEYWORDS");
        }

        List<String> failures = validateSanitizedPlan(
                genreName,
                keywordItems,
                questionGuide,
                request
        );

        boolean generationImpossible =
                !hasMinimumPlaceContext(request) || keywordItems.size() < answerSlots.size();

        boolean reviewRequired = generationImpossible;

        String reviewReason = reviewRequired
                ? "장소명, 주소, 카테고리성 정보가 부족하여 자동 생성할 수 없습니다."
                : "";

        boolean fieldVerificationRecommended = keywordItems.stream()
                .anyMatch(item -> item != null
                        && (isAiInferredSourceType(item.getSourceType())
                        || "REVIEW_REQUIRED".equals(item.getRisk())
                        || "WEAK_SOURCE".equals(item.getRisk())));

        int qualityScore = Math.max(0, 100 - failures.size() * 15);
        warnings.add("PLAN_QUALITY_SCORE_" + qualityScore);

        if (!failures.isEmpty()) {
            warnings.add("PLAN_VALIDATION_WARN_" + String.join("_", failures));
        }

        String rationale = geminiPlan.path("rationale").asText("");
        if (blank(rationale) || isBadPlanPlaceholder(rationale)) {
            rationale = buildSafePlanRationale(keywordItems, request);
        }

        return AiEpisodePlanResponse.builder()
                .selectedGenreId(blank(genreId) ? "CUSTOM" : genreId)
                .selectedGenreName(genreName)
                .answerSlots(answerSlots)
                .finalAnswerKeywords(keywordItems)
                .finalQuestionGuide(questionGuide)
                .rationale(rationale)
                .planReviewRequired(reviewRequired)
                .reviewReason(reviewReason)
                .fieldVerificationRecommended(fieldVerificationRecommended)
                .rejectedGenreReasons(readStringArray(geminiPlan.path("rejectedGenreReasons")))
                .validationWarnings(warnings.stream().distinct().toList())
                .nextSteps(List.of(
                        "장르와 정답 키워드를 확인합니다.",
                        "확정된 키워드를 기준으로 전체 스토리 초안을 생성합니다."
                ))
                .build();
    }

    private String resolveSelectedGenreNameFromCatalog(
            String genreId,
            String requestedGenreName,
            AiEpisodeDraftRequest request,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems,
            List<String> warnings) {

        if (request != null
                && request.getGenreCatalog() != null
                && !request.getGenreCatalog().isEmpty()) {

            return request.getGenreCatalog().stream()
                    .filter(genre -> genre != null && same(genre.getGenreId(), genreId))
                    .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreName)
                    .filter(value -> !blank(value))
                    .findFirst()
                    .orElseGet(() -> request.getGenreCatalog().stream()
                            .filter(genre -> genre != null && !blank(genre.getGenreName()))
                            .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreName)
                            .findFirst()
                            .orElse("야외 방탈출"));
        }

        return normalizePlanGenreName(
                requestedGenreName,
                request,
                keywordItems,
                warnings
        );
    }

    private String buildFinalAnswerGuideFromKeywords(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems) {

        Map<String, String> byLabel = new LinkedHashMap<>();
        for (AiEpisodePlanResponse.AnswerKeyword item : keywordItems) {
            if (item != null && !blank(item.getLabel()) && !blank(item.getKeyword())) {
                byLabel.put(item.getLabel().trim(), item.getKeyword().trim());
            }
        }

        if (byLabel.containsKey("범인")
                && byLabel.containsKey("범행도구")
                && byLabel.containsKey("사건장소")) {
            return byLabel.get("범인") + "이 "
                    + byLabel.get("범행도구") + "로 "
                    + byLabel.get("사건장소") + "에서 사건을 일으켰다는 결론을 입력하게 한다.";
        }

        if (byLabel.containsKey("숨겨진 물건")
                && byLabel.containsKey("해금 조건")
                && byLabel.containsKey("보관 장소")) {
            return byLabel.get("숨겨진 물건") + "을 찾기 위해 "
                    + byLabel.get("해금 조건") + "을 만족하고 "
                    + byLabel.get("보관 장소") + "를 확인해야 한다는 결론을 입력하게 한다.";
        }

        if (byLabel.containsKey("최종 문장")
                && byLabel.containsKey("핵심 숫자")
                && byLabel.containsKey("암호해독 장소")) {
            return byLabel.get("암호해독 장소") + "에서 "
                    + byLabel.get("핵심 숫자") + "를 이용해 "
                    + byLabel.get("최종 문장") + "을 해독했다는 결론을 입력하게 한다.";
        }

        if (byLabel.containsKey("실종 원인")
                && byLabel.containsKey("마지막 장소")
                && byLabel.containsKey("관련 물건")) {
            return byLabel.get("실종 원인") + " 때문에 사라졌고, "
                    + byLabel.get("마지막 장소") + "에서 마지막 흔적이 확인되며, "
                    + byLabel.get("관련 물건") + "이 핵심 증거라는 결론을 입력하게 한다.";
        }

        String joined = keywordItems.stream()
                .filter(item -> item != null && !blank(item.getLabel()) && !blank(item.getKeyword()))
                .map(item -> item.getLabel().trim() + "=" + item.getKeyword().trim())
                .collect(java.util.stream.Collectors.joining(", "));

        return "최종 정답에는 다음 키워드가 모두 포함되어야 한다: " + joined;
    }

    private boolean hasGenreCatalog(AiEpisodeDraftRequest request) {
        return request != null
                && request.getGenreCatalog() != null
                && !request.getGenreCatalog().isEmpty();
    }

    private String firstGenreId(AiEpisodeDraftRequest request) {
        if (!hasGenreCatalog(request)) {
            return "CUSTOM";
        }
        return request.getGenreCatalog().stream()
                .filter(genre -> genre != null && !blank(genre.getGenreId()))
                .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreId)
                .findFirst()
                .orElse("CUSTOM");
    }

    private String genreIdByPlaceSignals(AiEpisodeDraftRequest request) {
        if (!hasGenreCatalog(request)) {
            return "CUSTOM";
        }
        String text = genreSignalText(request);
        Map<String, Integer> scores = new LinkedHashMap<>();
        addGenreScore(scores, "CODE_BREAKING", text,
                "암호", "코드", "숫자", "번호", "문양", "표식", "기호", "비문", "간판", "문장", "해독", "문서", "기록", "전시", "박물관", "기념");
        addGenreScore(scores, "MISSING_CASE", text,
                "실종", "사라", "마지막", "행방", "목격", "동선", "길", "거리", "골목", "역", "정류장", "흔적", "발자국", "이동");
        addGenreScore(scores, "WITNESS_CONTRADICTION", text,
                "증언", "진술", "목격", "시간", "시각", "출입", "서명", "접수", "반출", "기록", "메모", "사진");
        addGenreScore(scores, "ARCHIVE_TRACE", text,
                "기록", "문서", "자료", "보관", "도서", "서고", "기념", "역사", "전시", "박물관", "아카이브", "메모");
        addGenreScore(scores, "TIME_SLIP", text,
                "시간", "시계", "연도", "시대", "옛", "근대", "역사", "기념", "복원", "흔적");
        addGenreScore(scores, "URBAN_LEGEND", text,
                "전설", "소문", "골목", "벽화", "마을", "시장", "밤", "그림자", "오래된", "이야기");
        addGenreScore(scores, "TREASURE_HUNT", text,
                "보물", "상자", "열쇠", "봉인", "해금", "숨겨진", "감춰진", "보관함", "금고", "자물쇠");
        addGenreScore(scores, "MURDER_MYSTERY", text,
                "사건", "혈흔", "범인", "피해", "위협", "충돌", "갈등", "신고");

        return scores.entrySet().stream()
                .filter(entry -> genreExists(request, entry.getKey()))
                .sorted((left, right) -> {
                    int compare = Integer.compare(right.getValue(), left.getValue());
                    if (compare != 0) {
                        return compare;
                    }
                    return Integer.compare(genreTieBreaker(request, left.getKey()), genreTieBreaker(request, right.getKey()));
                })
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> diversifiedFallbackGenreId(request));
    }

    private String genreSignalText(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null) {
            return "";
        }
        return request.getPlaces().stream()
                .filter(place -> place != null)
                .map(place -> String.join(" ",
                        blank(place.getName()) ? "" : place.getName(),
                        blank(place.getAddress()) ? "" : place.getAddress(),
                        blank(place.getDescription()) ? "" : place.getDescription(),
                        blank(place.getAdminMemo()) ? "" : place.getAdminMemo(),
                        place.getVisibleElements() == null ? "" : String.join(" ", place.getVisibleElements()),
                        place.getNumbers() == null ? "" : String.join(" ", place.getNumbers()),
                        place.getKeywords() == null ? "" : String.join(" ", place.getKeywords()),
                        place.getUsablePuzzleSources() == null ? "" : String.join(" ", place.getUsablePuzzleSources())
                ))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void addGenreScore(Map<String, Integer> scores, String genreId, String text, String... signals) {
        int score = 0;
        for (String signal : signals) {
            if (containsAny(text, signal)) {
                score += 10;
            }
        }
        scores.put(genreId, score);
    }

    private boolean genreExists(AiEpisodeDraftRequest request, String genreId) {
        return hasGenreCatalog(request) && request.getGenreCatalog().stream()
                .anyMatch(genre -> genre != null && same(genre.getGenreId(), genreId));
    }

    private int genreTieBreaker(AiEpisodeDraftRequest request, String genreId) {
        String base = compact(String.join(" ",
                request == null ? "" : blank(request.getArea()) ? "" : request.getArea(),
                request == null ? "" : blank(request.getTheme()) ? "" : request.getTheme(),
                genreId
        ));
        return Math.floorMod(base.hashCode(), 1000);
    }

    private String diversifiedFallbackGenreId(AiEpisodeDraftRequest request) {
        List<String> ids = hasGenreCatalog(request)
                ? request.getGenreCatalog().stream()
                    .filter(genre -> genre != null && !blank(genre.getGenreId()))
                    .map(AiEpisodeDraftRequest.GenreTemplateInput::getGenreId)
                    .filter(id -> !same(id, "TREASURE_HUNT"))
                    .toList()
                : List.of();
        if (ids.isEmpty()) {
            return firstGenreId(request);
        }
        int index = Math.floorMod(genreSignalText(request).hashCode(), ids.size());
        return ids.get(index);
    }

    private boolean treasureGenreFits(AiEpisodeDraftRequest request) {
        String text = genreSignalText(request);
        return containsAny(text, "보물", "상자", "열쇠", "봉인", "해금", "숨겨진", "감춰진", "보관함", "금고", "자물쇠");
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> fixedFinalAnswerSlots() {
        return FIXED_FINAL_ANSWER_SLOTS.stream()
                .map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId(slot.slotId())
                        .label(slot.label())
                        .description(slot.description())
                        .minClueCount(3)
                        .build())
                .toList();
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> enforceFinalDestinationKeyword(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        AiEpisodeDraftRequest.PlaceInput finalPlace = finalDestinationPlace(request);
        String finalPlaceName = finalPlace == null ? "" : normalizeAnswerKeywordValue(finalPlace.getName());

        for (AiEpisodePlanResponse.AnswerSlotPlan slot : answerSlots) {
            AiEpisodePlanResponse.AnswerKeyword item = findKeywordForSlot(keywordItems, slot);
            if ("FINAL_DESTINATION".equals(slot.getSlotId())) {
                if (blank(finalPlaceName)) {
                    warnings.add("FINAL_DESTINATION_PLACE_MISSING");
                    continue;
                }
                result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                        .slotId(slot.getSlotId())
                        .label(slot.getLabel())
                        .keyword(finalPlaceName)
                        .aliases(List.of())
                        .sourcePlaceOrder(finalDestinationPlaceOrder(request, finalPlace))
                        .sourceBasis("내부 최종 목적지로 선택된 장소명")
                        .sourceType("FINAL_DESTINATION")
                        .sourcePlaceName(finalPlaceName)
                        .sourceText("최종 정답의 장소는 내부 최종 목적지 장소명과 동일해야 합니다.")
                        .difficulty("NORMAL")
                        .risk("OK")
                        .build());
                continue;
            }
            if (item != null) {
                item.setSlotId(slot.getSlotId());
                item.setLabel(slot.getLabel());
                result.add(item);
            }
        }

        if (result.stream().noneMatch(item -> same(item.getSlotId(), "FINAL_DESTINATION"))) {
            warnings.add("FINAL_DESTINATION_KEYWORD_NOT_CREATED");
        }
        return result;
    }

    private AiEpisodeDraftRequest.PlaceInput finalDestinationPlace(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            return null;
        }
        return request.getPlaces().stream()
                .filter(place -> place != null && same(place.getRole(), "FINAL"))
                .findFirst()
                .orElseGet(() -> request.getPlaces().get(request.getPlaces().size() - 1));
    }

    private Integer finalDestinationPlaceOrder(
            AiEpisodeDraftRequest request,
            AiEpisodeDraftRequest.PlaceInput finalPlace) {
        if (request == null || request.getPlaces() == null || finalPlace == null) {
            return null;
        }
        for (int i = 0; i < request.getPlaces().size(); i++) {
            if (request.getPlaces().get(i) == finalPlace) {
                return i + 1;
            }
        }
        return null;
    }

    private boolean isFinalDestinationSource(
            AiEpisodeDraftRequest request,
            Integer sourcePlaceOrder,
            String sourcePlaceName) {
        AiEpisodeDraftRequest.PlaceInput finalPlace = finalDestinationPlace(request);
        if (finalPlace == null) {
            return false;
        }
        Integer finalOrder = finalDestinationPlaceOrder(request, finalPlace);
        if (sourcePlaceOrder != null && finalOrder != null) {
            return sourcePlaceOrder.equals(finalOrder);
        }
        return !blank(sourcePlaceName) && same(sourcePlaceName, finalPlace.getName());
    }

    private boolean answerClueUsesExternalSource(String sourceText, String sourceBasis) {
        String value = compact(String.join(" ",
                sourceText == null ? "" : sourceText,
                sourceBasis == null ? "" : sourceBasis
        )).toLowerCase(Locale.ROOT);
        return value.contains("nearby")
                || value.contains("candidate")
                || value.contains("주변후보")
                || value.contains("확인후보");
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> resolvePlanAnswerSlotsFromCatalogOrGemini(
            String selectedGenreId,
            JsonNode geminiSlotsNode,
            AiEpisodeDraftRequest request,
            List<String> warnings) {

        if (hasGenreCatalog(request)) {
            AiEpisodeDraftRequest.GenreTemplateInput selectedGenre = request.getGenreCatalog().stream()
                    .filter(genre -> genre != null && same(genre.getGenreId(), selectedGenreId))
                    .findFirst()
                    .orElseGet(() -> request.getGenreCatalog().stream()
                            .filter(genre -> genre != null)
                            .findFirst()
                            .orElse(null));

            if (selectedGenre != null
                    && selectedGenre.getAnswerSlots() != null
                    && !selectedGenre.getAnswerSlots().isEmpty()) {

                return selectedGenre.getAnswerSlots().stream()
                        .filter(slot -> slot != null && !blank(slot.getSlotId()) && !blank(slot.getLabel()))
                        .map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder()
                                .slotId(slot.getSlotId().trim())
                                .label(slot.getLabel().trim())
                                .description(blank(slot.getDescription())
                                        ? slot.getLabel().trim() + "에 해당하는 최종 정답 키워드"
                                        : slot.getDescription().trim())
                                .minClueCount(slot.getMinClueCount() == null
                                        ? 2
                                        : Math.max(2, slot.getMinClueCount()))
                                .build())
                        .toList();
            }

            warnings.add("GENRE_CATALOG_SLOT_MISSING");
        }

        List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots = parsePlanAnswerSlots(geminiSlotsNode);

        if (answerSlots.isEmpty()) {
            answerSlots = defaultFictionPlanSlots();
            warnings.add("PLAN_SLOTS_FALLBACK_USED");
        }

        return answerSlots;
    }

    private boolean containsAllApprovedKeywordsInGuide(
            String guide,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems) {
        if (blank(guide) || keywordItems == null || keywordItems.isEmpty()) {
            return false;
        }

        // 생성된 모든 키워드가 guide 문장 내에 전부 존재하는지 검사 (allMatch 사용)
        return keywordItems.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .allMatch(keyword -> textContains(guide, keyword));
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> sanitizeGeminiFinalAnswerKeywordItems(
            List<AiEpisodePlanResponse.AnswerKeyword> geminiItems,
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        Set<String> used = new java.util.LinkedHashSet<>();

        if (geminiItems == null || geminiItems.isEmpty()) {
            warnings.add("GEMINI_PLAN_KEYWORDS_EMPTY");
            return result;
        }

        for (int i = 0; i < answerSlots.size(); i++) {
            AiEpisodePlanResponse.AnswerSlotPlan slot = answerSlots.get(i);

            AiEpisodePlanResponse.AnswerKeyword source = geminiItems.stream()
                    .filter(item -> item != null)
                    .filter(item -> same(item.getSlotId(), slot.getSlotId())
                            || same(item.getLabel(), slot.getLabel()))
                    .findFirst()
                    .orElse(i < geminiItems.size() ? geminiItems.get(i) : null);

            if (source == null) {
                continue;
            }

            String keyword = normalizeAnswerKeywordValue(source.getKeyword());

            if (blank(keyword)
                    || used.contains(compact(keyword))
                    || isBadPlanPlaceholder(keyword)
                    || !"OK".equals(planKeywordRisk(keyword, slot.getLabel(), request))) {
                warnings.add("GEMINI_PLAN_KEYWORD_" + (i + 1) + "_REJECTED");
                continue;
            }

            String sourcePlaceName = source.getSourcePlaceName();
            Integer sourcePlaceOrder = source.getSourcePlaceOrder();

            if (blank(sourcePlaceName)) {
                sourcePlaceName = planSourcePlaceName(request, sourcePlaceOrder);
            }
            if ("ANSWER_CLUE".equals(slot.getSlotId())
                    && !isFinalDestinationSource(request, sourcePlaceOrder, sourcePlaceName)) {
                AiEpisodeDraftRequest.PlaceInput finalPlace = finalDestinationPlace(request);
                if (finalPlace != null && isAiInferredSourceType(source.getSourceType())) {
                    sourcePlaceOrder = finalDestinationPlaceOrder(request, finalPlace);
                    sourcePlaceName = finalPlace.getName();
                    warnings.add("GEMINI_PLAN_ANSWER_CLUE_SOURCE_ASSIGNED_TO_FINAL_PLACE");
                }
            }

            if ("ANSWER_CLUE".equals(slot.getSlotId())
                    && !isFinalDestinationSource(request, sourcePlaceOrder, sourcePlaceName)) {
                warnings.add("GEMINI_PLAN_ANSWER_CLUE_NOT_FINAL_PLACE_REJECTED");
                continue;
            }

            String sourceType = sourceTypeForSlot(slot.getSlotId(), slot.getLabel());

            String sourceText = source.getSourceText();

            if (blank(sourceText) || isBadPlanPlaceholder(sourceText)) {
                AiEpisodeDraftRequest.PlaceInput place = planSourcePlace(request, sourcePlaceOrder, sourcePlaceName);
                sourceText = buildFictionSourceText(
                        slot.getSlotId(),
                        slot.getLabel(),
                        keyword,
                        blank(sourcePlaceName) ? "선택 장소" : sourcePlaceName,
                        place
                );
            }

            String sourceBasis = source.getSourceBasis();

            if (blank(sourceBasis) || isBadPlanPlaceholder(sourceBasis)) {
                sourceBasis = "선택 장소의 이름, 주소, 설명, 카테고리성 키워드를 스토리 단서로 재구성";
            }

            if ("ANSWER_CLUE".equals(slot.getSlotId())
                    && answerClueUsesExternalSource(sourceText, sourceBasis)) {
                warnings.add("GEMINI_PLAN_ANSWER_CLUE_EXTERNAL_SOURCE_REJECTED");
                continue;
            }

            result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                    .slotId(slot.getSlotId())
                    .label(slot.getLabel())
                    .keyword(keyword)
                    .aliases(source.getAliases() == null ? List.of() : source.getAliases())
                    .sourcePlaceOrder(sourcePlaceOrder)
                    .sourceBasis(sourceBasis)
                    .sourceType(sourceType)
                    .sourcePlaceName(blank(sourcePlaceName) ? "선택 장소" : sourcePlaceName)
                    .sourceText(sourceText)
                    .difficulty(blank(source.getDifficulty()) ? "NORMAL" : source.getDifficulty())
                    .risk("OK")
                    .build());

            used.add(compact(keyword));
        }

        return result;
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> synthesizeFictionPlanKeywordItems(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            AiEpisodeDraftRequest request,
            List<AiEpisodePlanResponse.AnswerKeyword> alreadyAccepted,
            List<String> warnings) {
        Set<String> acceptedSlots = alreadyAccepted == null
                ? Set.of()
                : alreadyAccepted.stream()
                  .map(AiEpisodePlanResponse.AnswerKeyword::getSlotId)
                  .filter(value -> !blank(value))
                  .map(this::compact)
                  .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        Set<String> used = alreadyAccepted == null
                ? new java.util.LinkedHashSet<>()
                : alreadyAccepted.stream()
                  .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                  .filter(value -> !blank(value))
                  .map(this::compact)
                  .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();

        for (int i = 0; i < answerSlots.size(); i++) {
            AiEpisodePlanResponse.AnswerSlotPlan answerSlot = answerSlots.get(i);

            if (acceptedSlots.contains(compact(answerSlot.getSlotId()))) {
                continue;
            }

            PlanSlot slot = new PlanSlot(
                    answerSlot.getSlotId(),
                    answerSlot.getLabel(),
                    answerSlot.getDescription()
            );

            PlanKeywordCandidate candidate = inferPlanKeywordCandidate(
                    slot.slotId(),
                    slot.label(),
                    request,
                    used,
                    mergeKeywordItems(alreadyAccepted, result)
            );

            if (candidate == null) {
                warnings.add("PLAN_KEYWORD_" + (i + 1) + "_SOURCE_MISSING");
                continue;
            }

            String risk = planKeywordRisk(candidate.value(), slot.label(), request);

            result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                    .slotId(slot.slotId())
                    .label(slot.label())
                    .keyword(candidate.value())
                    .aliases(List.of())
                    .sourcePlaceOrder(candidate.sourcePlaceOrder())
                    .sourceBasis(candidate.sourceBasis())
                    .sourceType(candidate.sourceType())
                    .sourcePlaceName(candidate.sourcePlaceName())
                    .sourceText(candidate.sourceText())
                    .difficulty("NORMAL")
                    .risk("OK".equals(risk) ? "OK" : "REVIEW_REQUIRED")
                    .build());

            used.add(compact(candidate.value()));
            warnings.add("PLAN_KEYWORD_" + (i + 1) + "_AI_INFERRED");
        }

        return result;
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> mergeKeywordItems(
            List<AiEpisodePlanResponse.AnswerKeyword> first,
            List<AiEpisodePlanResponse.AnswerKeyword> second) {
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result;
    }

    private String sanitizeFinalQuestionGuide(
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems) {
        Set<String> labels = keywordItems.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getLabel)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (labels.contains("기록 매체")
                && labels.contains("방향 표식")
                && labels.contains("확인 조건")) {
            return "현장에 남은 기록 매체와 방향 표식을 대조해, 마지막 확인 조건이 무엇을 가리키는지 추론하게 한다.";
        }
        return "현장에 남은 물건의 특징과 이동 흔적을 대조해, 기록을 확인하기 위한 조건을 추론하게 한다.";
    }

    private String sanitizeSelectedGenreName(
            String ignoredGeminiGenreName,
            AiEpisodeDraftRequest request,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems) {
        String region = planRegionName(request);
        String material = keywordItems.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .findFirst()
                .orElse("");
        if (blank(material)) {
            return region + " 현장근거 검수";
        }
        material = planGenreMaterial(material);
        String value = region + " " + material + " 추적";
        if (value.length() < 8) {
            value = region + " 기록함 흔적 추적";
        }
        if (value.length() > 18) {
            value = region + " 기록함 추적";
        }
        return value;
    }

    private String planGenreMaterial(String keyword) {
        if (containsAny(keyword, "철제함", "보관함", "상자", "원통")) return "기록함";
        if (containsAny(keyword, "사진", "사진첩")) return "사진첩";
        if (containsAny(keyword, "지도")) return "지도";
        if (containsAny(keyword, "열쇠")) return "열쇠";
        if (containsAny(keyword, "영수증", "메모", "필름")) return "기록물";
        if (containsAny(keyword, "손잡이", "표식", "화살표", "문양")) return "표식";
        String value = keyword.replaceAll("\\s+", "");
        return value.length() > 6 ? "기록함" : value;
    }

    private List<String> validateSanitizedPlan(
            String genreName,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems,
            String questionGuide,
            AiEpisodeDraftRequest request) {
        List<String> failures = new ArrayList<>();
        if (keywordItems.size() < 2 || keywordItems.size() > 4) {
            failures.add("KEYWORD_COUNT");
        }
        if (keywordItems.stream().map(item -> compact(item.getLabel())).distinct().count() != 3) {
            failures.add("DUPLICATE_SLOT");
        }
        if (keywordItems.stream().anyMatch(item -> isForbiddenPlanSlot(item.getLabel()))) {
            failures.add("FORBIDDEN_SLOT");
        }
        if (keywordItems.stream().anyMatch(item ->
                !isFinalDestinationKeyword(item)
                        && !"OK".equals(planKeywordRisk(item.getKeyword(), item.getLabel(), request)))) {
            failures.add("INVALID_KEYWORD");
        }
        if (keywordItems.stream().anyMatch(item -> !hasValidPlanKeywordSource(item, request))) {
            failures.add("MISSING_KEYWORD_SOURCE");
        }
        if (isGenericPlanGenreName(genreName)) failures.add("GENERIC_GENRE");
        if (containsAny(questionGuide, "최종 지점", "최종 장소", "마지막 장소", "입구")) {
            failures.add("QUESTION_FORBIDDEN_TERM");
        }
        boolean missingKeyword = keywordItems.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .anyMatch(keyword -> !textContains(questionGuide, keyword)); // 포함되지 않은 게 하나라도 있는지 확인

        if (missingKeyword) failures.add("QUESTION_MISSING_KEYWORD");

        return failures;
    }

    private boolean hasValidPlanKeywordSource(
            AiEpisodePlanResponse.AnswerKeyword item,
            AiEpisodeDraftRequest request) {

        if (item == null) {
            return false;
        }

        if (isFinalDestinationKeyword(item)) {
            AiEpisodeDraftRequest.PlaceInput finalPlace = finalDestinationPlace(request);
            return finalPlace != null && same(item.getKeyword(), finalPlace.getName());
        }

        if (isBadPlanPlaceholder(item.getKeyword()) || isBadPlanPlaceholder(item.getSourceText())) {
            return false;
        }

        if (isAiInferredSourceType(item.getSourceType())) {
            return !blank(item.getKeyword())
                    && !blank(item.getSourceType())
                    && !blank(item.getSourcePlaceName())
                    && !blank(item.getSourceText());
        }

        if (blank(item.getSourceType()) || blank(item.getSourcePlaceName())
                || blank(item.getSourceText()) || request == null || request.getPlaces() == null) {
            return false;
        }

        AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().stream()
                .filter(value -> value != null && same(value.getName(), item.getSourcePlaceName()))
                .findFirst()
                .orElse(null);

        if (place == null) {
            return false;
        }

        return switch (item.getSourceType()) {
            case "VISIBLE_ELEMENT" -> containsSame(place.getVisibleElements(), item.getSourceText());
            case "KEYWORD" -> containsSame(place.getKeywords(), item.getSourceText());
            case "NUMBER" -> containsSame(place.getNumbers(), item.getSourceText());
            case "DESCRIPTION" -> same(place.getDescription(), item.getSourceText());
            case "ADMIN_MEMO" -> same(place.getAdminMemo(), item.getSourceText());
            case "SITE_ENRICHMENT" -> containsSame(place.getUsablePuzzleSources(), item.getSourceText());
            default -> false;
        };
    }

    private boolean containsSame(List<String> values, String target) {
        return values != null && values.stream().anyMatch(value -> same(value, target));
    }

    private boolean isFinalDestinationKeyword(AiEpisodePlanResponse.AnswerKeyword item) {
        return item != null
                && (same(item.getSlotId(), "FINAL_DESTINATION")
                || same(item.getLabel(), "장소")
                || same(item.getSourceType(), "FINAL_DESTINATION"));
    }

    private String planSlotDescription(String label) {
        return switch (label) {
            case "기록 매체" -> "형태와 용도를 단서로 추론하는 구체 매체";
            case "방향 표식" -> "이동 방향을 가리키는 관찰 가능한 표식";
            case "확인 조건" -> "마지막 기록을 확인하기 위한 구체 조건";
            default -> "최종 추론을 구성하는 구체 항목";
        };
    }

    private void normalizePlanSlots(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            List<AiEpisodePlanResponse.AnswerKeyword> keywords,
            List<String> warnings) {
        for (int i = 0; i < answerSlots.size(); i++) {
            AiEpisodePlanResponse.AnswerSlotPlan slot = answerSlots.get(i);
            String originalLabel = slot.getLabel();
            PlanSlot normalized = fixedFinalAnswerSlotAt(i);
            if (!same(originalLabel, normalized.label())
                    || !same(slot.getSlotId(), normalized.slotId())) {
                warnings.add("PLAN_SLOT_" + (i + 1) + "_NORMALIZED_"
                        + compact(originalLabel).toUpperCase(Locale.ROOT));
            }
            slot.setSlotId(normalized.slotId());
            slot.setLabel(normalized.label());
            slot.setDescription(normalized.description());
            slot.setMinClueCount(Math.max(2, slot.getMinClueCount() == null ? 2 : slot.getMinClueCount()));

            if (i < keywords.size()) {
                keywords.get(i).setSlotId(normalized.slotId());
                keywords.get(i).setLabel(normalized.label());
            }
        }
        for (int i = answerSlots.size(); i < keywords.size(); i++) {
            AiEpisodePlanResponse.AnswerKeyword keyword = keywords.get(i);
            PlanSlot normalized = fixedFinalAnswerSlotAt(i);
            keyword.setSlotId(normalized.slotId());
            keyword.setLabel(normalized.label());
        }
    }

    private PlanSlot fixedFinalAnswerSlotAt(int index) {
        return FIXED_FINAL_ANSWER_SLOTS.get(Math.floorMod(index, FIXED_FINAL_ANSWER_SLOTS.size()));
    }

    private PlanSlot normalizePlanSlot(String slotId, String label, int index) {
        String value = normalize(slotId) + " " + compact(label);
        if (containsAny(value,
                "PHRASE", "ENDING", "CLEAR", "해제문구", "최종문구", "결론문구",
                "클리어문구", "엔딩문구", "사건의의미", "숨겨진진실")) {
            return new PlanSlot("CONDITION", "확인 조건", "기록이나 장치를 확인하기 위한 구체적인 조건");
        }
        if (containsAny(value, "DESTINATION", "LOCATION", "PLACE", "최종목적지", "장소키워드")) {
            return new PlanSlot("STORAGE_CLUE", "보관 단서", "실제 장소명이 아닌 보관 위치의 관찰 특징");
        }
        if (containsAny(value, "IDENTITY", "PERSON", "ROLE", "숨긴인물", "관계자역할", "정체")) {
            return new PlanSlot("ROLE", "관계자 역할", "행동과 진술로 추론할 수 있는 픽션 역할");
        }
        if (containsAny(value, "MEDIA", "DOCUMENT", "RECORD", "기록매체", "핵심매개체", "매개체")) {
            return new PlanSlot("MEDIA", "기록 매체", "형태와 용도를 단서로 추론할 수 있는 구체 매체");
        }
        if (containsAny(value, "OBJECT", "ITEM", "ARTIFACT", "핵심물건", "물건", "유물")) {
            return new PlanSlot("OBJECT", "핵심 물건", "흩어진 단서가 가리키는 구체 물건");
        }
        if (containsAny(value, "DEVICE", "LOCK", "봉인장치", "개방장치")) {
            return new PlanSlot("DEVICE", "봉인 장치", "기록을 보호하거나 여는 구체 장치");
        }
        if (containsAny(value, "DIRECTION", "ROUTE", "이동단서", "방향표식", "경로")) {
            return new PlanSlot("DIRECTION", "방향 표식", "이동 방향을 가리키는 관찰 가능한 표식");
        }
        if (containsAny(value, "COMPARE", "REFERENCE", "대조기준", "핵심단서", "정답키워드")) {
            return new PlanSlot("REFERENCE", "대조 기준", "여러 단서를 비교할 때 사용하는 구체 기준");
        }
        if (containsAny(value, "CONDITION", "CODE", "NUMBER", "확인조건", "개방조건", "마지막표식", "조건")) {
            return new PlanSlot("CONDITION", "확인 조건", "마지막 기록을 확인하기 위한 구체 조건");
        }
        return switch (index % 3) {
            case 0 -> new PlanSlot("OBJECT", "핵심 물건", "흩어진 단서가 가리키는 구체 물건");
            case 1 -> new PlanSlot("STORAGE_CLUE", "보관 단서", "실제 장소명이 아닌 보관 위치의 관찰 특징");
            default -> new PlanSlot("CONDITION", "확인 조건", "마지막 기록을 확인하기 위한 구체 조건");
        };
    }

    private String planKeywordRisk(String keyword, String label, AiEpisodeDraftRequest request) {
        if (blank(keyword) || isPlanKeywordTooAbstract(keyword)) {
            return "TOO_ABSTRACT";
        }
        if (!blank(label) && (same(keyword, label)
                || compact(label).contains(compact(keyword))
                || compact(keyword).contains(compact(label)))) {
            return "TOO_ABSTRACT";
        }
        if (isPlanKeywordPlaceNameRisk(keyword, request)) {
            return "PLACE_NAME_RISK";
        }
        if (!isPlanCandidateSuitable(keyword, "", label)) {
            return "WEAK_SOURCE";
        }
        String normalized = keyword.trim();
        int wordCount = normalized.split("\\s+").length;
        if (wordCount > 6 || normalized.length() >= 15
                || normalized.matches(".*(입니다|합니다|한다|했다|되었다|이었다|있었다)[.!?]?$")
                || containsAny(normalized, "사람들의 이야기", "기록의 행방", "마지막 결론")) {
            return "TOO_LONG";
        }
        return "OK";
    }

    private boolean isForbiddenExampleKeyword(String keyword) {
        if (blank(keyword)) {
            return false;
        }
        String value = compact(keyword);
        return Set.of(
                "조작된기록서",
                "누락된증언",
                "거짓알리바이",
                "훼손된장부",
                "사라진목격자",
                "봉인된보고서",
                "바뀐순찰기록",
                "마지막진술",
                "숨겨진거래장부",
                "위조된명령서"
        ).contains(value);
    }

    private boolean isPlanKeywordTooAbstract(String keyword) {
        if (isBadPlanPlaceholder(keyword)) {
            return true;
        }

        String value = compact(keyword);
        if (Set.of(
                "기록", "단서", "문서", "메모", "진실", "비밀", "장소", "물건", "사건",
                "흔적", "정보", "기억", "조건", "정체", "핵심물건", "최종목적지", "해제문구",
                "최종문구", "결론문구", "클리어문구", "엔딩문구", "핵심단서", "정답키워드",
                "검토대상","주변확인후보", "확인후보",  "문화후보지", "상권후보지"
        ).contains(value)) {
            return true;
        }
        return containsAny(keyword,
                "다시 돌아온 시간", "다시 찾은 일상", "잊힌 기억", "숨겨진 진실",
                "평화의 의미", "되찾은 시간", "돌아온 기록", "마지막 결론", "기록의 행방",
                "사람들의 이야기", "삶의 의미", "기억의 조각", "숫자 ");
    }

    private boolean isPlanKeywordPlaceNameRisk(String keyword, AiEpisodeDraftRequest request) {
        String compactKeyword = compact(keyword);

        if (compactKeyword.matches(".*(로|길|대로|거리)$")
                && !containsAny(keyword, "표시", "동선", "연결", "방향")) {
            return true;
        }

        if (request == null || request.getPlaces() == null) {
            return false;
        }
        String keywordCore = planPlaceCore(keyword);
        return request.getPlaces().stream()
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(name -> !blank(name))
                .anyMatch(name -> {
                    if (same(name, keyword) || looksLikePlaceNameFragment(name, keyword)) {
                        return true;
                    }
                    String placeCore = planPlaceCore(name);
                    return placeCore.length() >= 2 && keywordCore.length() >= 2
                            && (placeCore.contains(keywordCore) || keywordCore.contains(placeCore));
                });
    }

    private String planPlaceCore(String value) {
        return compact(value)
                .replace("야외전시장", "")
                .replace("전시장", "")
                .replace("예술마을", "")
                .replace("마을", "")
                .replace("언덕길", "")
                .replace("골목", "")
                .replace("거리", "")
                .replace("광장", "")
                .replace("식당", "")
                .replace("카페", "")
                .replace("상점", "")
                .replace("서울", "")
                .replace("용산", "");
    }

    private PlanKeywordCandidate choosePlanKeywordCandidate(
            String slotId,
            String label,
            AiEpisodeDraftRequest request,
            Set<String> used) {
        if (request != null && request.getPlaces() != null) {
            for (String sourceType : List.of(
                    "VISIBLE_ELEMENT", "KEYWORD", "NUMBER",
                    "ADMIN_MEMO", "DESCRIPTION", "SITE_ENRICHMENT")) {
                for (int i = 0; i < request.getPlaces().size(); i++) {
                    AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
                    for (PlanSourceCandidate candidate : planCandidatesForSource(
                            place, sourceType, slotId, label)) {
                    String normalized = normalizeAnswerKeywordValue(candidate.value());
                    if ("OK".equals(planKeywordRisk(normalized, label, request))
                            && isPlanCandidateSuitable(normalized, slotId, label)
                            && !used.contains(compact(normalized))) {
                        return new PlanKeywordCandidate(
                                normalized,
                                i + 1,
                                candidate.sourceType() + " 근거에서 추출",
                                candidate.sourceType(),
                                place.getName(),
                                candidate.sourceText()
                        );
                    }
                }
                }
            }
        }
        return null;
    }

    private List<PlanSourceCandidate> planCandidatesForSource(
            AiEpisodeDraftRequest.PlaceInput place,
            String sourceType,
            String slotId,
            String label) {
        List<PlanSourceCandidate> candidates = new ArrayList<>();
        switch (sourceType) {
            case "VISIBLE_ELEMENT" ->
                    addPlanSourceCandidates(candidates, place.getVisibleElements(), sourceType);
            case "KEYWORD" ->
                    addPlanSourceCandidates(candidates, place.getKeywords(), sourceType);
            case "NUMBER" -> {
                if (place.getNumbers() != null) {
                    for (String number : place.getNumbers()) {
                        String value = planNumberCandidate(number, slotId, label, place);
                        if (!blank(value)) {
                            candidates.add(new PlanSourceCandidate(value, sourceType, number));
                        }
                    }
                }
            }
            case "ADMIN_MEMO" -> planTextCandidates(place.getAdminMemo()).stream()
                    .map(value -> new PlanSourceCandidate(value, sourceType, place.getAdminMemo()))
                    .forEach(candidates::add);
            case "DESCRIPTION" -> planTextCandidates(place.getDescription()).stream()
                    .map(value -> new PlanSourceCandidate(value, sourceType, place.getDescription()))
                    .forEach(candidates::add);
            case "SITE_ENRICHMENT" ->
                    addPlanSourceCandidates(candidates, place.getUsablePuzzleSources(), sourceType);
            default -> {
            }
        }
        return candidates;
    }

    private void addPlanSourceCandidates(
            List<PlanSourceCandidate> target,
            List<String> values,
            String sourceType) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .filter(value -> !isBadPlanPlaceholder(value))
                .map(value -> new PlanSourceCandidate(value, sourceType, value))
                .forEach(target::add);
    }

    private boolean isPlanCandidateSuitable(String candidate, String slotId, String label) {
        String slot = normalize(slotId) + " " + compact(label);
        if (containsAny(slot, "ROLE", "IDENTITY", "PERSON", "인물", "역할", "정체")) {
            return containsAny(candidate, "기록자", "관찰자", "전달자", "관리자", "보관자");
        }
        if (containsAny(slot, "DIRECTION", "ROUTE", "이동", "방향")) {
            return containsAny(candidate,
                    "화살표", "방향", "배열", "정렬", "골목", "창문", "문살", "동선",
                    "이동", "연결", "표시", "길");
        }
        if (containsAny(slot, "CONDITION", "CODE", "NUMBER", "조건", "표식", "대조")) {
            if (candidate.matches(".*\\d.*")) {
                return true;
            }

            return containsAny(candidate,
                    "번째", "개의", "개 표식", "개 조명", "개 화살표", "개 문양",
                    "정렬", "일치", "봉인", "닫힌", "잠금",
                    "대조", "표시", "확인", "연결", "방향", "화살표");
        }
        if (containsAny(slot, "LOCATION", "STORAGE", "PLACE", "보관", "장소", "이동")) {
            return containsAny(candidate,
                    "계단", "담장", "철문", "입구", "기둥", "손잡이", "표지", "안내판", "골목",
                    "창문", "보관함", "철제함");
        }

        if (containsAny(slot, "OBJECT", "ITEM", "MEDIA", "DEVICE", "물건", "매체", "장치", "유물")) {
            return containsAny(candidate,
                    "사진", "지도", "열쇠", "보관함", "철제함", "손잡이", "봉인", "필름",
                    "원통", "상자", "첩", "함", "영수증", "메모",
                    "카드", "조각", "자료", "시계", "일기장");
        }
        return true;
    }

    private String planNumberCandidate(
            String number,
            String slotId,
            String label,
            AiEpisodeDraftRequest.PlaceInput place) {
        if (blank(number)) {
            return null;
        }
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        String slot = normalize(slotId) + " " + compact(label);
        String context = String.join(" ",
                place.getVisibleElements() == null ? "" : String.join(" ", place.getVisibleElements()),
                place.getKeywords() == null ? "" : String.join(" ", place.getKeywords()),
                blank(place.getDescription()) ? "" : place.getDescription(),
                blank(place.getAdminMemo()) ? "" : place.getAdminMemo());
        if (containsAny(slot, "CONDITION", "NUMBER", "CODE", "조건", "표식", "대조")) {
            if (containsAny(context, "조명", "빛")) return koreanCountPhrase(digits) + " 조명";
            if (containsAny(context, "화살표")) return koreanCountPhrase(digits) + " 화살표";
            if (containsAny(context, "문양")) return koreanCountPhrase(digits) + " 문양";
            if (containsAny(context, "표식")) return koreanCountPhrase(digits) + " 표식";
            return null;
        }
        if (containsAny(slot, "LOCATION", "STORAGE", "보관", "장소", "이동")) {
            if (containsAny(context, "계단")) return koreanOrdinal(digits) + " 계단";
            if (containsAny(context, "기둥")) return koreanOrdinal(digits) + " 기둥";
            return null;
        }
        return null;
    }

    private String koreanCountPhrase(String digits) {
        return switch (digits) {
            case "1" -> "한 개의";
            case "2" -> "두 개의";
            case "3" -> "세 개의";
            case "4" -> "네 개의";
            default -> digits + "개의";
        };
    }

    private String koreanOrdinal(String digits) {
        return switch (digits) {
            case "1" -> "첫 번째";
            case "2" -> "두 번째";
            case "3" -> "세 번째";
            case "4" -> "네 번째";
            default -> digits + "번째";
        };
    }

    private List<String> planTextCandidates(String text) {
        if (blank(text) || isBadPlanPlaceholder(text)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String phrase : text.split("[,.;:!?/|\\n]")) {
            String normalized = normalizeAnswerKeywordValue(phrase);
            if (!blank(normalized)
                    && !isBadPlanPlaceholder(normalized)
                    && normalized.split("\\s+").length <= 6
                    && normalized.length() <= 24) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String normalizePlanGenreName(
            String genreName,
            AiEpisodeDraftRequest request,
            List<AiEpisodePlanResponse.AnswerKeyword> keywords,
            List<String> warnings) {
        String current = blank(genreName) ? "" : genreName.trim();
        if (!isGenericPlanGenreName(current) && current.length() >= 8 && current.length() <= 18) {
            return current;
        }
        String region = planRegionName(request);
        String material = keywords.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .filter(value -> !isPlanKeywordTooAbstract(value))
                .findFirst()
                .orElse("붉은 표식");
        String normalized = region + " " + material + " 추적";
        if (normalized.length() > 18) {
            String shortMaterial = material.length() > 7 ? material.substring(0, 7) : material;
            normalized = region + " " + shortMaterial + " 추적";
        }
        warnings.add("PLAN_GENRE_NAME_NORMALIZED");
        return normalized;
    }

    private boolean isGenericPlanGenreName(String genreName) {
        if (blank(genreName)) {
            return true;
        }
        String value = compact(genreName);
        return Set.of(
                "기록추적미션", "기억찾기", "미스터리탐방", "야외추리미션",
                "장소탐색미션", "야외스토리미션", "기록추적", "장소탐색"
        ).contains(value)
                || value.matches("^(야외|지역|장소|기록|기억)?(추리|탐색|추적|탐방)?미션$");
    }

    private String planRegionName(AiEpisodeDraftRequest request) {
        if (request != null && !blank(request.getArea())) {
            String[] parts = request.getArea().trim().split("\\s+");
            String last = parts[parts.length - 1];
            if (last.matches("[가-힣]구") && parts.length >= 2) {
                return parts[parts.length - 2].replaceAll("(특별시|광역시|시)$", "") + " " + last;
            }
            String region = last.replaceAll("(특별시|광역시|특별자치시|특별자치도|시|군|구)$", "");
            if (!blank(region)) {
                return region;
            }
        }
        if (request != null && request.getPlaces() != null) {
            return request.getPlaces().stream()
                    .map(AiEpisodeDraftRequest.PlaceInput::getName)
                    .filter(name -> !blank(name))
                    .map(this::planPlaceCore)
                    .filter(value -> value.length() >= 2)
                    .findFirst()
                    .orElse("지역");
        }
        return "지역";
    }

    private String normalizeFinalQuestionGuide(
            String guide,
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            AiEpisodeDraftRequest request) {
        List<String> labels = answerSlots == null
                ? List.of()
                : answerSlots.stream()
                        .map(AiEpisodePlanResponse.AnswerSlotPlan::getLabel)
                        .filter(label -> !blank(label))
                        .toList();
        boolean listsSlots = labels.size() >= 2
                && labels.stream().filter(label -> textContains(guide, label)).count() >= 2;
        boolean mechanical = blank(guide)
                || listsSlots
                || containsAny(guide, "종합해 최종 결론", "최종 결론을 보고", "슬롯", "정답 키워드")
                || planQuestionMentionsPlace(guide, request);
        if (!mechanical) {
            return guide.trim();
        }
        return "현장에 남은 물건의 흔적과 이동 단서, 마지막 확인 조건을 대조해 무엇이 어디로 옮겨졌고 어떻게 확인되는지 추론하게 한다.";
    }

    private boolean planQuestionMentionsPlace(String guide, AiEpisodeDraftRequest request) {
        if (blank(guide) || request == null || request.getPlaces() == null) {
            return false;
        }
        String compactGuide = compact(guide);
        return request.getPlaces().stream()
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(name -> !blank(name))
                .anyMatch(name -> {
                    String fullName = compact(name);
                    String core = planPlaceCore(name);
                    return compactGuide.contains(fullName)
                            || (core.length() >= 3 && compactGuide.contains(core));
                });
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> enforceConcreteAnswerClueKeyword(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        if (keywordItems == null) {
            return List.of();
        }
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>(keywordItems);
        Set<String> used = result.stream()
                .filter(item -> item != null && !same(item.getSlotId(), "ANSWER_CLUE"))
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .map(this::compact)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        for (AiEpisodePlanResponse.AnswerKeyword item : result) {
            if (item == null || !same(item.getSlotId(), "ANSWER_CLUE")) {
                continue;
            }
            String keyword = normalizeAnswerKeywordValue(item.getKeyword());
            if (isConcreteAnswerClueKeyword(keyword) && "OK".equals(planKeywordRisk(keyword, item.getLabel(), request))) {
                item.setKeyword(keyword);
                return result;
            }
            String replacement = inferredAnswerClueFromFinalContext(request, result, used);
            if (blank(replacement)) {
                replacement = "봉인 표식";
            }
            item.setKeyword(replacement);
            item.setAliases(answerClueAliases(replacement));
            item.setRisk("OK");
            item.setSourceType(blank(item.getSourceType()) ? "AI_INFERRED_STORY_OBJECT" : item.getSourceType());
            item.setSourceText("핵심 단서는 최종 입력 가능한 구체 명사구로 보정되었습니다.");
            warnings.add("ANSWER_CLUE_KEYWORD_REWRITTEN_CONCRETE");
            return result;
        }

        AiEpisodePlanResponse.AnswerSlotPlan slot = answerSlots == null ? null : answerSlots.stream()
                .filter(item -> item != null && same(item.getSlotId(), "ANSWER_CLUE"))
                .findFirst()
                .orElse(null);
        result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                .slotId("ANSWER_CLUE")
                .label(slot == null ? "핵심 단서" : slot.getLabel())
                .keyword("봉인 표식")
                .aliases(answerClueAliases("봉인 표식"))
                .sourceType("AI_INFERRED_STORY_OBJECT")
                .sourceText("핵심 단서는 최종 입력 가능한 구체 명사구로 보강되었습니다.")
                .difficulty("NORMAL")
                .risk("OK")
                .build());
        warnings.add("ANSWER_CLUE_KEYWORD_REWRITTEN_CONCRETE");
        return result;
    }

    private boolean isConcreteAnswerClueKeyword(String keyword) {
        if (blank(keyword)) {
            return false;
        }
        String compactKeyword = compact(keyword);
        if (Set.of(
                "옛서찰", "사라져가는것들", "숨은증인", "오래된기억", "기록의의미", "진실",
                "기록", "단서", "문서", "메모", "서찰", "증인", "기억", "의미"
        ).contains(compactKeyword)) {
            return false;
        }
        return containsAny(keyword,
                "조작", "누락", "거짓", "훼손", "사라진", "봉인", "바뀐", "위조", "숨겨진", "마지막", "삭제", "변조", "허위")
                && containsAny(keyword,
                "기록서", "기록", "증언", "알리바이", "장부", "목격자", "보고서", "순찰 기록", "진술", "명령서",
                "문서", "출입 기록", "거래 장부", "사진 기록", "쪽지", "메모");
    }

    private List<String> answerClueAliases(String keyword) {
        String normalized = blank(keyword) ? "봉인 표식" : keyword.trim();
        List<String> aliases = new ArrayList<>();
        aliases.add(normalized.replace("된 ", " ").trim());
        aliases.add(normalized.replace("된", "").trim());
        aliases.add(normalized.replace("바뀐", "변조된").trim());
        aliases.add(normalized.replace("조작된", "고쳐진").trim());
        return aliases.stream().filter(value -> !blank(value)).distinct().toList();
    }

    private void validateNormalizedPlan(
            String genreName,
            List<AiEpisodePlanResponse.AnswerKeyword> keywords,
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            String finalQuestionGuide,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        List<String> failures = new ArrayList<>();
        if (isGenericPlanGenreName(genreName) || genreName.length() < 8 || genreName.length() > 18) {
            failures.add("GENRE_NAME");
        }
        if (answerSlots.stream().anyMatch(slot -> isForbiddenPlanSlot(slot.getLabel()))) {
            failures.add("FORBIDDEN_SLOT");
        }
        for (int i = 0; i < keywords.size(); i++) {
            AiEpisodePlanResponse.AnswerKeyword item = keywords.get(i);
            String risk = planKeywordRisk(item.getKeyword(), item.getLabel(), request);
            if (!"OK".equals(risk)) {
                item.setRisk("REVIEW_REQUIRED");
                failures.add("KEYWORD_" + (i + 1) + "_" + risk);
            }
        }
        if (isPlanQuestionGuideMechanical(finalQuestionGuide, answerSlots)
                || planQuestionMentionsPlace(finalQuestionGuide, request)) {
            failures.add("QUESTION_GUIDE");
        }
        int score = Math.max(0, 100 - failures.size() * 20);
        warnings.add("PLAN_QUALITY_SCORE_" + score);
        if (!failures.isEmpty()) {
            warnings.add("PLAN_REVIEW_REQUIRED_" + String.join("_", failures));
        }
    }

    private boolean isForbiddenPlanSlot(String label) {
        return containsAny(label,
                "해제 문구", "최종 문구", "결론 문구", "클리어 문구", "엔딩 문구",
                "최종 목적지", "장소 키워드", "정답 키워드", "핵심 단서",
                "숨겨진 진실", "사건의 의미");
    }

    private boolean isPlanQuestionGuideMechanical(
            String guide,
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots) {
        if (blank(guide)) {
            return true;
        }
        long labelCount = answerSlots.stream()
                .map(AiEpisodePlanResponse.AnswerSlotPlan::getLabel)
                .filter(label -> textContains(guide, label))
                .count();
        return labelCount >= 2
                || containsAny(guide,
                "종합해 최종 결론", "최종 결론을 보고", "슬롯", "정답 키워드",
                "핵심 물건, 보관 단서", "핵심 매개체, 해제 문구");
    }

    private record PlanSlot(String slotId, String label, String description) {
    }

    private record PlanKeywordCandidate(
            String value,
            Integer sourcePlaceOrder,
            String sourceBasis,
            String sourceType,
            String sourcePlaceName,
            String sourceText) {
    }

    private record PlanSourceCandidate(
            String value,
            String sourceType,
            String sourceText) {
    }

    private boolean isAiInferredSourceType(String sourceType) {
        return !blank(sourceType) && AI_INFERRED_SOURCE_TYPES.contains(sourceType);
    }

    private boolean isBadPlanPlaceholder(String value) {
        if (blank(value)) {
            return false;
        }

        String compactValue = compact(value);

        return BAD_PLAN_PLACEHOLDERS.stream()
                .map(this::compact)
                .anyMatch(compactValue::contains);
    }

    private PlanKeywordCandidate inferPlanKeywordCandidate(
            String slotId,
            String label,
            AiEpisodeDraftRequest request,
            Set<String> used,
            List<AiEpisodePlanResponse.AnswerKeyword> knownKeywords) {

        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            return null;
        }

        AiEpisodeDraftRequest.PlaceInput finalPlaceForAnswerClue =
                "ANSWER_CLUE".equals(slotId) ? finalDestinationPlace(request) : null;

        for (int i = 0; i < request.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);

            if (place == null) {
                continue;
            }

            if (finalPlaceForAnswerClue != null && place != finalPlaceForAnswerClue) {
                continue;
            }

            String context = "ANSWER_CLUE".equals(slotId)
                    ? finalDestinationAnswerClueContext(place)
                    : planInferenceContext(place);

            if (blank(context) || isBadPlanPlaceholder(context)) {
                continue;
            }

            String keyword = switch (slotId) {
                case "RELATED_PERSON" -> inferredCulpritKeyword(context);
                case "ANSWER_CLUE" -> inferredWeaponKeyword(context);
                case "FINAL_DESTINATION" -> blank(place.getName()) ? inferredCaseLocationKeyword(context) : place.getName();

                case "CULPRIT" -> inferredCulpritKeyword(context);
                case "WEAPON" -> inferredWeaponKeyword(context);
                case "CASE_LOCATION" -> inferredCaseLocationKeyword(context);

                case "HIDDEN_ITEM" -> inferredHiddenItemKeyword(context);
                case "UNLOCK_CONDITION" -> inferredUnlockConditionKeyword(context);
                case "STORAGE_PLACE" -> inferredStoragePlaceKeyword(context);

                case "FINAL_PHRASE" -> inferredFinalPhraseKeyword(context);
                case "KEY_NUMBER" -> inferredKeyNumberKeyword(context);
                case "DECODE_LOCATION" -> inferredDecodeLocationKeyword(context);

                case "MISSING_REASON" -> inferredMissingReasonKeyword(context);
                case "LAST_LOCATION" -> inferredLastLocationKeyword(context);
                case "RELATED_ITEM" -> inferredRelatedItemKeyword(context);

                case "MEDIA" -> inferredMediaKeyword(context);
                case "DIRECTION" -> inferredDirectionKeyword(context);
                case "CONDITION" -> inferredConditionKeyword(context);

                default -> inferredStoryKeyword(context);
            };

            keyword = normalizeAnswerKeywordValue(keyword);

            if ("ANSWER_CLUE".equals(slotId)
                    && (blank(keyword)
                    || used.contains(compact(keyword))
                    || isBadPlanPlaceholder(keyword)
                    || !"OK".equals(planKeywordRisk(keyword, label, request)))) {
                keyword = inferredAnswerClueFromFinalContext(request, knownKeywords, used);
            }

            if (blank(keyword)
                    || used.contains(compact(keyword))
                    || isBadPlanPlaceholder(keyword)
                    || !"OK".equals(planKeywordRisk(keyword, label, request))) {
                continue;
            }

            String sourceType = switch (slotId) {
                case "FINAL_DESTINATION" -> "FINAL_DESTINATION";
                case "DIRECTION", "CASE_LOCATION", "STORAGE_PLACE", "DECODE_LOCATION", "LAST_LOCATION" ->
                        "AI_INFERRED_ROUTE_FEATURE";
                case "CONDITION", "UNLOCK_CONDITION", "KEY_NUMBER" ->
                        "AI_INFERRED_OBSERVATION";
                case "RELATED_PERSON", "ANSWER_CLUE", "MEDIA", "CULPRIT", "WEAPON", "HIDDEN_ITEM", "FINAL_PHRASE", "MISSING_REASON", "RELATED_ITEM" ->
                        "AI_INFERRED_STORY_OBJECT";
                default -> "AI_INFERRED_STORY_OBJECT";
            };

            String placeName = blank(place.getName())
                    ? "선택 장소 " + (i + 1)
                    : place.getName();
            String sourceText = buildFictionSourceText(
                    slotId,
                    label,
                    keyword,
                    placeName,
                    place
            );

            return new PlanKeywordCandidate(
                    keyword,
                    i + 1,
                    "선택 장소의 이름, 주소, 설명, 카테고리성 키워드를 스토리 단서로 재구성",
                    sourceType,
                    placeName,
                    sourceText
            );
        }

        return null;
    }

    private String inferredCulpritKeyword(String context) {
        if (containsAny(context, "사진", "골목", "마을", "언덕")) return "골목 사진사";
        if (containsAny(context, "전시", "박물관", "기념", "전쟁")) return "기록 보관자";
        if (containsAny(context, "시장", "식당", "카페", "상권")) return "영수증 전달자";
        if (containsAny(context, "예술", "갤러리", "공방", "작업실")) return "스케치 중개인";
        return "야간 기록자";
    }

    private String inferredWeaponKeyword(String context) {
        if (containsAny(context, "향교", "서원", "서예", "붓", "유교", "교육", "기록", "문서")) return "오래된 붓";
        if (containsAny(context, "사진", "카메라", "필름", "골목")) return "깨진 렌즈";
        if (containsAny(context, "문", "철문", "잠금", "열쇠")) return "녹슨 열쇠";
        if (containsAny(context, "비", "우산", "거리")) return "접힌 우산";
        if (containsAny(context, "식당", "시장", "영수증")) return "찢긴 영수증";
        return "부러진 펜";
    }

    private String inferredAnswerClueFromFinalContext(
            AiEpisodeDraftRequest request,
            List<AiEpisodePlanResponse.AnswerKeyword> knownKeywords,
            Set<String> used) {
        AiEpisodeDraftRequest.PlaceInput finalPlace = finalDestinationPlace(request);
        String placeText = finalPlace == null ? "" : String.join(" ",
                blank(finalPlace.getName()) ? "" : finalPlace.getName(),
                blank(finalPlace.getAddress()) ? "" : finalPlace.getAddress(),
                blank(finalPlace.getDescription()) ? "" : finalPlace.getDescription(),
                blank(finalPlace.getAdminMemo()) ? "" : finalPlace.getAdminMemo(),
                finalPlace.getVisibleElements() == null ? "" : String.join(" ", finalPlace.getVisibleElements()),
                finalPlace.getNumbers() == null ? "" : String.join(" ", finalPlace.getNumbers()),
                finalPlace.getKeywords() == null ? "" : String.join(" ", finalPlace.getKeywords()),
                finalPlace.getUsablePuzzleSources() == null ? "" : String.join(" ", finalPlace.getUsablePuzzleSources())
        );
        String relatedPerson = keywordForKnownSlot(knownKeywords, "RELATED_PERSON");

        List<String> candidates = new ArrayList<>();
        if (containsAny(placeText, "충주향교", "향교", "서원", "유교", "교육", "서예", "붓", "문서", "기록")
                || containsAny(relatedPerson, "향교", "기록자", "기록")) {
            candidates.add("오래된 붓");
        }
        if (containsAny(placeText, "문", "입구", "gate", "door")) {
            candidates.add("입구 표식");
        }
        if (containsAny(placeText, "전시", "기념", "박물관", "gallery", "museum", "exhibition")) {
            candidates.add("기념 인장");
        }
        if (containsAny(placeText, "계단", "언덕", "길", "거리", "동선", "route", "street")) {
            candidates.add("동선 표식");
        }
        if (containsAny(placeText, "사진", "카메라", "그림", "벽화", "image", "photo")) {
            candidates.add("흐린 사진");
        }
        if (containsAny(placeText, "번호", "숫자", "연도", "number", "year")) {
            candidates.add("확인 번호");
        }
        if (containsAny(relatedPerson, "기록", "보관", "archivist")) {
            candidates.add("봉인 기록");
        }
        if (containsAny(relatedPerson, "사진", "촬영", "camera")) {
            candidates.add("사진 조각");
        }
        if (containsAny(relatedPerson, "전달", "배달", "courier")) {
            candidates.add("접힌 쪽지");
        }
        candidates.add("봉인 표식");
        candidates.add("확인 표식");
        candidates.add("기록 조각");

        for (String candidate : candidates) {
            String normalized = normalizeAnswerKeywordValue(candidate);
            if (!blank(normalized)
                    && !used.contains(compact(normalized))
                    && !isForbiddenExampleKeyword(normalized)
                    && "OK".equals(planKeywordRisk(normalized, "핵심 단서", request))) {
                return normalized;
            }
        }
        return null;
    }

    private String keywordForKnownSlot(
            List<AiEpisodePlanResponse.AnswerKeyword> knownKeywords,
            String slotId) {
        if (knownKeywords == null) {
            return "";
        }
        return knownKeywords.stream()
                .filter(item -> item != null && same(item.getSlotId(), slotId))
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .findFirst()
                .orElse("");
    }

    private String inferredCaseLocationKeyword(String context) {
        if (containsAny(context, "계단", "언덕", "비탈")) return "낮은 돌계단";
        if (containsAny(context, "철문", "문", "입구")) return "붉은 철문 뒤";
        if (containsAny(context, "벽화", "골목", "마을")) return "벽화 골목 끝";
        if (containsAny(context, "전시", "기념", "박물관")) return "전시 안내판 뒤";
        return "좁은 골목 끝";
    }

    private String inferredHiddenItemKeyword(String context) {
        if (containsAny(context, "사진", "골목", "마을")) return "낡은 사진첩";
        if (containsAny(context, "지도", "길", "동선")) return "봉인된 지도";
        if (containsAny(context, "열쇠", "문", "잠금")) return "황금 열쇠";
        if (containsAny(context, "전시", "기록", "박물관")) return "기록 카드함";
        return "작은 보관함";
    }

    private String inferredUnlockConditionKeyword(String context) {
        if (containsAny(context, "화살표", "방향")) return "같은 방향 화살표";
        if (containsAny(context, "표식", "문양")) return "세 개의 표식";
        if (containsAny(context, "계단")) return "두 번째 계단";
        if (containsAny(context, "숫자", "번호")) return "1446";
        return "붉은 표시";
    }

    private String inferredStoragePlaceKeyword(String context) {
        if (containsAny(context, "계단", "언덕")) return "두 번째 계단";
        if (containsAny(context, "문", "철문", "입구")) return "파란 문 아래";
        if (containsAny(context, "골목", "벽화")) return "벽화 골목 끝";
        if (containsAny(context, "기둥")) return "왼쪽 기둥 뒤";
        return "낮은 담장 옆";
    }

    private String inferredFinalPhraseKeyword(String context) {
        if (containsAny(context, "귀환", "기억", "기념")) return "기록은 돌아온다";
        if (containsAny(context, "골목", "마을")) return "골목은 기억한다";
        if (containsAny(context, "전시", "박물관")) return "증거는 남는다";
        return "마지막 기록";
    }

    private String inferredKeyNumberKeyword(String context) {
        if (containsAny(context, "1446")) return "1446";
        if (containsAny(context, "3", "세", "삼")) return "3";
        if (containsAny(context, "4", "네", "사")) return "4";
        return "7";
    }

    private String inferredDecodeLocationKeyword(String context) {
        if (containsAny(context, "안내판", "전시")) return "전시 안내판 앞";
        if (containsAny(context, "계단")) return "낮은 계단 앞";
        if (containsAny(context, "벽화", "골목")) return "벽화 골목 끝";
        return "표지판 아래";
    }

    private String inferredMissingReasonKeyword(String context) {
        if (containsAny(context, "영수증", "식당", "시장", "상권")) return "잘못 배달된 기록";
        if (containsAny(context, "사진", "골목", "마을")) return "사라진 사진 거래";
        if (containsAny(context, "전시", "박물관", "기념")) return "누락된 전시 기록";
        return "숨겨진 거래";
    }

    private String inferredLastLocationKeyword(String context) {
        if (containsAny(context, "계단", "언덕")) return "낮은 돌계단";
        if (containsAny(context, "철문", "문")) return "붉은 철문 앞";
        if (containsAny(context, "벽화", "골목")) return "벽화 골목 끝";
        return "좁은 골목 끝";
    }

    private String inferredRelatedItemKeyword(String context) {
        if (containsAny(context, "사진", "골목")) return "찢어진 사진";
        if (containsAny(context, "영수증", "식당", "시장")) return "젖은 영수증";
        if (containsAny(context, "시계", "시간")) return "깨진 손목시계";
        if (containsAny(context, "전시", "기록")) return "누락된 기록 카드";
        return "찢어진 일기장";
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> defaultFictionPlanSlots() {
        return List.of(
                AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId("MEDIA")
                        .label("기록 매체")
                        .description("형태와 용도를 단서로 추론하는 구체 매체")
                        .minClueCount(2)
                        .build(),
                AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId("DIRECTION")
                        .label("방향 표식")
                        .description("이동 방향을 가리키는 관찰 가능한 표식")
                        .minClueCount(2)
                        .build(),
                AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId("CONDITION")
                        .label("확인 조건")
                        .description("마지막 기록을 확인하기 위한 구체 조건")
                        .minClueCount(2)
                        .build()
        );
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> mergePlanKeywordItems(
            List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlots,
            List<AiEpisodePlanResponse.AnswerKeyword> accepted,
            List<AiEpisodePlanResponse.AnswerKeyword> synthesized) {
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();

        for (AiEpisodePlanResponse.AnswerSlotPlan slot : answerSlots) {
            AiEpisodePlanResponse.AnswerKeyword item = findKeywordForSlot(accepted, slot);
            if (item == null) {
                item = findKeywordForSlot(synthesized, slot);
            }
            if (item != null) {
                result.add(item);
            }
        }

        return result;
    }

    private AiEpisodePlanResponse.AnswerKeyword findKeywordForSlot(
            List<AiEpisodePlanResponse.AnswerKeyword> items,
            AiEpisodePlanResponse.AnswerSlotPlan slot) {
        if (items == null || slot == null) {
            return null;
        }

        return items.stream()
                .filter(item -> item != null)
                .filter(item -> same(item.getSlotId(), slot.getSlotId())
                        || same(item.getLabel(), slot.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private String sourceTypeForSlot(String slotId, String label) {
        String slot = normalize(slotId) + " " + compact(label);

        if (containsAny(slot, "FINAL_DESTINATION", "최종목적지", "장소")) {
            return "FINAL_DESTINATION";
        }

        if (containsAny(slot,
                "DIRECTION", "ROUTE", "LOCATION", "PLACE", "STORAGE",
                "이동", "방향", "경로", "보관", "장소", "사건장소", "마지막장소", "암호해독장소")) {
            return "AI_INFERRED_ROUTE_FEATURE";
        }

        if (containsAny(slot, "CONDITION", "CODE", "NUMBER", "확인", "조건", "대조", "표식")) {
            return "AI_INFERRED_OBSERVATION";
        }

        return "AI_INFERRED_STORY_OBJECT";
    }

    private AiEpisodeDraftRequest.PlaceInput planSourcePlace(
            AiEpisodeDraftRequest request,
            Integer sourcePlaceOrder,
            String sourcePlaceName) {
        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            return null;
        }

        if (sourcePlaceOrder != null
                && sourcePlaceOrder >= 1
                && sourcePlaceOrder <= request.getPlaces().size()) {
            return request.getPlaces().get(sourcePlaceOrder - 1);
        }

        if (!blank(sourcePlaceName)) {
            return request.getPlaces().stream()
                    .filter(place -> place != null && same(place.getName(), sourcePlaceName))
                    .findFirst()
                    .orElse(request.getPlaces().get(0));
        }

        return request.getPlaces().get(0);
    }

    private String planSourcePlaceName(AiEpisodeDraftRequest request, Integer sourcePlaceOrder) {
        AiEpisodeDraftRequest.PlaceInput place = planSourcePlace(request, sourcePlaceOrder, null);
        if (place == null || blank(place.getName())) {
            return "선택 장소";
        }
        return place.getName();
    }

    private String buildFictionSourceText(
            String slotId,
            String label,
            String keyword,
            String placeName,
            AiEpisodeDraftRequest.PlaceInput place) {
        PlaceStoryProfile profile = placeStoryProfile(place);
        String slot = normalize(slotId) + " " + compact(label);

        if (containsAny(slot, "MEDIA", "DOCUMENT", "RECORD", "OBJECT", "ITEM", "매체", "물건", "기록")) {
            return placeName + "의 " + profile.routeMood()
                    + "을 바탕으로, " + keyword
                    + "을 추적하는 픽션 기록 매체로 설정했습니다.";
        }

        if (containsAny(slot, "LOCATION", "PLACE", "STORAGE", "장소", "사건장소", "보관", "마지막장소", "암호해독장소")) {
            return placeName + "의 " + profile.movementMood()
                    + "을 바탕으로, " + keyword
                    + "을 최종 결론에 필요한 픽션 장소 단서로 설정했습니다.";
        }

        if (containsAny(slot, "DIRECTION", "ROUTE", "이동", "방향", "경로")) {
            return placeName + "의 " + profile.movementMood()
                    + "을 바탕으로, " + keyword
                    + "을 다음 단서와 연결되는 픽션 이동 단서로 설정했습니다.";
        }

        if (containsAny(slot, "CONDITION", "CODE", "NUMBER", "COMPARE", "확인", "조건", "대조", "표식")) {
            return placeName + "에서 이어진 기록 조각을 비교하게 만들기 위해, "
                    + keyword + "을 마지막 확인 조건으로 설정했습니다.";
        }

        return placeName + "의 장소 맥락을 바탕으로, "
                + keyword + "을 플레이어가 추론할 픽션 단서로 설정했습니다.";
    }

    private PlaceStoryProfile placeStoryProfile(AiEpisodeDraftRequest.PlaceInput place) {
        String context = planInferenceContext(place);

        if (containsAny(context, "골목", "마을", "언덕", "거리", "길")) {
            return new PlaceStoryProfile("골목형 동선", "장소 사이를 잇는 이동 흐름", "사진 기록");
        }

        if (containsAny(context, "예술", "갤러리", "공방", "작업실", "전시")) {
            return new PlaceStoryProfile("작품과 기록이 이어지는 흐름", "전시물을 따라 이동하는 흐름", "스케치 기록");
        }

        if (containsAny(context, "시장", "상권", "식당", "카페", "커피")) {
            return new PlaceStoryProfile("상권의 왕복 동선", "가게 사이를 오가는 흐름", "영수증 기록");
        }

        if (containsAny(context, "기념", "박물관", "전쟁", "역사")) {
            return new PlaceStoryProfile("기록물을 따라가는 관람 흐름", "기억을 따라 이동하는 흐름", "전시 기록");
        }

        return new PlaceStoryProfile("장소 간 이동 흐름", "겹쳐지는 동선", "미션 기록");
    }

    private record PlaceStoryProfile(
            String routeMood,
            String movementMood,
            String materialMood
    ) {
    }

    private boolean hasMinimumPlaceContext(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            return false;
        }

        return request.getPlaces().stream()
                .filter(place -> place != null)
                .anyMatch(place ->
                        !blank(place.getName())
                                && (!blank(place.getAddress())
                                || !blank(place.getDescription())
                                || hasAny(place.getKeywords())
                                || hasAny(place.getUsablePuzzleSources()))
                );
    }

    private boolean hasAny(List<String> values) {
        return values != null && values.stream().anyMatch(value -> !blank(value));
    }

    private boolean containsAnyApprovedKeywordInGuide(
            String guide,
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems) {
        if (blank(guide) || keywordItems == null) {
            return false;
        }

        return keywordItems.stream()
                .map(AiEpisodePlanResponse.AnswerKeyword::getKeyword)
                .filter(value -> !blank(value))
                .anyMatch(keyword -> textContains(guide, keyword));
    }

    private String buildSafePlanRationale(
            List<AiEpisodePlanResponse.AnswerKeyword> keywordItems,
            AiEpisodeDraftRequest request) {
        String region = planRegionName(request);

        String joined = keywordItems == null
                ? ""
                : keywordItems.stream()
                  .map(AiEpisodePlanResponse.AnswerKeyword::getLabel)
                  .filter(value -> !blank(value))
                  .distinct()
                  .collect(java.util.stream.Collectors.joining(", "));

        if (blank(joined)) {
            joined = "기록 매체, 방향 표식, 확인 조건";
        }

        return region + "의 장소명, 주소, 설명, 카테고리성 키워드를 바탕으로 "
                + joined + "을 플레이 가능한 픽션 단서 구조로 구성했습니다.";
    }

    private String planInferenceContext(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null) {
            return "";
        }

        return String.join(" ",
                blank(place.getName()) ? "" : place.getName(),
                blank(place.getAddress()) ? "" : place.getAddress(),
                blank(place.getDescription()) ? "" : place.getDescription(),
                blank(place.getAdminMemo()) ? "" : place.getAdminMemo(),
                place.getKeywords() == null ? "" : String.join(" ", place.getKeywords()),
                place.getUsablePuzzleSources() == null ? "" : String.join(" ", place.getUsablePuzzleSources())
        ).trim();
    }

    private String finalDestinationAnswerClueContext(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null) {
            return "";
        }

        String directContext = String.join(" ",
                safeFinalAnswerClueText(place.getDescription()),
                safeFinalAnswerClueText(place.getAdminMemo()),
                safeFinalAnswerClueValues(place.getVisibleElements()),
                safeFinalAnswerClueValues(place.getNumbers()),
                safeFinalAnswerClueValues(place.getKeywords())
        ).trim();

        if (!blank(directContext)) {
            return directContext;
        }

        return String.join(" ",
                safeFinalAnswerClueValues(place.getUsablePuzzleSources()),
                blank(place.getName()) ? "" : place.getName(),
                blank(place.getAddress()) ? "" : place.getAddress()
        ).trim();
    }

    private String safeFinalAnswerClueValues(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream()
                .map(this::safeFinalAnswerClueText)
                .filter(value -> !blank(value))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String safeFinalAnswerClueText(String value) {
        if (blank(value) || isNearbyCandidateSignal(value)) {
            return "";
        }
        return value;
    }

    private boolean isNearbyCandidateSignal(String value) {
        String compactValue = compact(value).toLowerCase(Locale.ROOT);
        return compactValue.contains("kakao")
                || compactValue.contains("kakaolocal")
                || compactValue.contains("localapi")
                || compactValue.contains("nearby")
                || compactValue.contains("candidate")
                || compactValue.contains("주변")
                || compactValue.contains("후보")
                || compactValue.contains("상권")
                || compactValue.contains("900m")
                || compactValue.contains("카페쉼터")
                || compactValue.contains("식당상권");
    }

    private String inferredMediaKeyword(String context) {
        if (containsAny(context, "전쟁", "기념", "전시", "박물관")) {
            return "전시 기록 카드";
        }
        if (containsAny(context, "식당", "노가리", "시장", "상권", "카페", "커피")) {
            return "찢긴 영수증 조각";
        }
        if (containsAny(context, "예술", "갤러리", "공방", "작업실")) {
            return "접힌 스케치 카드";
        }
        if (containsAny(context, "마을", "골목", "언덕", "거리")) {
            return "골목 사진 조각";
        }
        return "미션 기록 카드";
    }

    private String inferredDirectionKeyword(String context) {
        if (containsAny(context, "언덕", "비탈", "경사")) {
            return "언덕 동선 표시";
        }
        if (containsAny(context, "골목", "마을", "거리")) {
            return "골목 연결 표시";
        }
        if (containsAny(context, "전시", "기념", "박물관")) {
            return "전시 동선 표시";
        }
        if (containsAny(context, "시장", "상권", "식당", "카페")) {
            return "상권 이동 표시";
        }
        return "겹친 동선 표시";
    }

    private String inferredConditionKeyword(String context) {
        if (containsAny(context, "전쟁", "기념", "전시", "박물관")) {
            return "기록 대조 표시";
        }
        if (containsAny(context, "식당", "노가리", "시장", "상권", "카페", "커피")) {
            return "영수증 대조 표시";
        }
        if (containsAny(context, "예술", "갤러리", "공방", "작업실")) {
            return "스케치 대조 표시";
        }
        return "마지막 대조 표시";
    }

    private String inferredStoryKeyword(String context) {
        if (containsAny(context, "전쟁", "기념", "전시", "박물관")) {
            return "전시 기록 카드";
        }
        if (containsAny(context, "식당", "시장", "상권", "카페")) {
            return "찢긴 영수증 조각";
        }
        if (containsAny(context, "예술", "갤러리", "공방")) {
            return "접힌 스케치 카드";
        }
        return "미션 기록 카드";
    }

    private String trimPlanText(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
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
                    .filter(finding -> !suppressGeminiFinding(finding, request.getDraft(), request.getSourceInput()))
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
                        "최종 정답은 관련자, 핵심 단서, 장소를 모두 포함하는지 확인하세요.",
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
            - Operation Korea answer contract is fixed:
              * RELATED_PERSON / 관련자: one fictional person, role, or group connected to the incident.
              * ANSWER_CLUE / 핵심 단서: one concrete object, number, phrase, tool, evidence, or condition.
              * FINAL_DESTINATION / 장소: the actual internal final destination place name.
            - ANSWER_CLUE keyword must be a concrete short noun phrase that a player can type, not a symbolic abstraction.
            - Bad ANSWER_CLUE examples: "옛 서찰", "사라져가는 것들", "숨은 증인", "오래된 기억", "기록의 의미", "진실".
            - Example-like ANSWER_CLUE phrases in this prompt are shape references only and must not be copied as output.
            - Generate exactly three clue trails for the final answer: three clues for 관련자, three clues for 핵심 단서, and three clues for 장소.
            - Each mission rewardClue must support one of those three slots through rewardClueSlotId and supportsKeywordSlots.
            - Use SUSPECT_CLUE evidence/cards for 관련자, ANSWER_CLUE for 핵심 단서, and DESTINATION_CLUE for 장소.
            - RELATED_PERSON mission rewardClue and SUSPECT_CLUE evidence must reveal the core contradiction or exclusion basis only after the player clears that mission.
            - ANSWER_CLUE mission rewardClue must not print the full ANSWER_CLUE keyword, but must include at least one category word such as 기록, 문서, 장부, 보고서, 증언, 진술, 알리바이, 목격자, 명령서, 물건, 사람, or 행동.
            - ANSWER_CLUE mission rewardClue must also include a concrete state/action such as 조작, 누락, 거짓, 훼손, 사라진, 봉인, 바뀐, 위조, 숨겨진, 삭제, 변조, 원본과 다른, or 다르게 고쳐진.
            - Do not use vague rewardClue wording such as "뭔가 이상하다", "서로 맞지 않았다", "암시한다", "진실임을 암시한다", or "중요해 보인다".
            - Do not perform cumulative letter reveal in rewardClue text. The server will attach per-card letterReveal data separately.
            - Destination clues must point to the final destination keyword indirectly before clear, not to a different fictional location.
            
            Final question rules:
            - finalQuestion must ask for the complete final answer implied by all approved slots.
            - finalQuestion must use slot labels or mystery roles, not exact keyword values.
            - Good: "관련자, 핵심 단서, 장소를 종합하면 어떤 결론인가?"
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
            - era must be a clear historical period label such as "조선 후기", "대한제국 말기", "일제강점기", "근현대", or "현대".
            - Do not put a poetic description such as "현대에 남은 오래된 기록" in era.
            - fictionSynopsis is the player briefing. Write it as a field commander assigning an urgent mission to an agent.
            - Address the player as "자네" or "요원", explain the current situation and approaching risk, then give a calm directive to inspect the mission file and begin.
            - Use Korean command-briefing endings such as "-하게", "-하도록", "-일세", "-하겠네", and "-해야 하네".
            - Do not write fictionSynopsis as a neutral plot summary or history explanation.
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
            - Every approved answer slot must be supported by exactly three separate clue-bearing missions when possible.
            - With 9 playable hint missions, distribute them as 3 RELATED_PERSON clues, 3 ANSWER_CLUE clues, and 3 FINAL_DESTINATION clues.
            - If there are fewer than 9 non-start missions, keep the distribution as balanced as possible and never omit FINAL_DESTINATION.
            - START missions introduce the operation.
            - ANSWER_HINT missions narrow RELATED_PERSON or ANSWER_CLUE.
            - DESTINATION_HINT missions narrow FINAL_DESTINATION.
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
            
            AI inferred source rules:
            - If input.finalAnswerKeywordItems contains sourceType starting with AI_INFERRED_, treat those keywords as story-safe inferred clues, not confirmed field facts.
            - Do not claim AI_INFERRED_* keywords are printed on signs, visible on plaques, counted on objects, or physically guaranteed at the place.
            - When AI_INFERRED_* is used, prefer STORY_COMBINATION or PATTERN puzzles.
            - Do not use NUMBER_LOCK unless a real number exists in place.numbers.
            - Do not use exact sign text, letter extraction, place-name extraction, object counting, or plaque-reading puzzles for AI_INFERRED_* sources.
            - Set verificationLevel to ADMIN_REVIEW or FIELD_REQUIRED when the puzzle depends on AI_INFERRED_* source.
            - Set puzzleAnswerSource to FICTION_SAFE or DESCRIPTION when the answer is story-safe rather than field-confirmed.
            
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
            - Character displayName must be a fictional Korean full name, not a role or alias. Good: "한서윤", "강도윤", "윤재하". Bad: "의뢰인", "정리관", "전달자", "기록 중개인", "보관 담당자".
            - Character alias may be a role label such as "의뢰인", "정리관", or "전달자".
            - Character suspiciousPoint and alibiSummary must not mention "힌트 카드", "정답 힌트 카드", "목적지 힌트 카드", "카드 하나", or any UI/card object. Write them as in-world actions, route contradictions, statements, timestamps, objects, or witness claims.
            - Create exactly three character cards with different deduction roles:
              1) primary contradiction candidate: alibiSummary must be a plausible claim only; do not include "핵심 모순:" or the decisive contradiction.
              2) excludable false lead: alibiSummary must be a plausible claim only; do not include "배제 근거:" or the reason this person can be ruled out.
              3) witness or courier: alibiSummary must state only their claimed movement or role, not why they did not create the core incident.
            - Put decisive contradictions and exclusion bases in RELATED_PERSON mission rewardClue and SUSPECT_CLUE evidence, not in the default suspect alibiSummary.
            - shortDescription is the card front summary. Keep it short: name/role level, one line, no long suspicion text.
            - relationToVictim must be one sentence about how the person is connected to the incident.
            - suspiciousPoint must cite at least one concrete basis: document, time, object, witness record, signature, entry log, photo, memo, or route record.
            - Avoid abstract words such as "의심스럽다", "수상하다", "잠적했다", "진실을 숨긴다" unless paired with a concrete time/object/document basis.
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
            - Use the fixed Operation Korea answer slots, not genre-specific answer slots.
            - Generate short final answer keywords for 관련자 and 핵심 단서.
            - Use the internal FINAL place name as the 장소 keyword.
            - Plan three clue trails for each keyword, for a total of nine clue directions when enough missions exist.
            - The result will be reviewed by an admin before full draft generation.
            
            Source rules:
            - Use only the provided input JSON.
            - Use selected places, descriptions, keywords, visibleElements, numbers, adminMemo, and place roles as source material.
            - ANSWER_CLUE must be derived from the internal FINAL place itself: its description, visibleElements, numbers, keywords, adminMemo, site/RAG enrichment about that place, or a story-safe inference from RELATED_PERSON + FINAL_DESTINATION.
            - ANSWER_CLUE must not use a nearby candidate place, nearby shop, unrelated selected place, or route-neighbor as the answer value.
            - Do not invent real historical facts that are not present in the input.
            - Do not use real historical people as perpetrators, villains, false leads, or final answer values.
            
            Genre rules:
            - If input.genreCatalog exists and is not empty, choose exactly one genre from input.genreCatalog for tone only.
            - Choose the genre by place fit, not by safest default. Use museums, records, documents, numbers, signs, routes, witnesses, markets, alleys, stations, parks, memorials, or visible objects as genre signals.
            - Do not over-select TREASURE_HUNT. Select TREASURE_HUNT only when the place data clearly contains treasure, box, key, seal, lock, hidden object, storage, or unlocking motifs.
            - If the route is record/document/museum-heavy, prefer ARCHIVE_TRACE or CODE_BREAKING when available.
            - If the route is alley/street/movement/last-seen-heavy, prefer MISSING_CASE or WITNESS_CONTRADICTION when available.
            - If the route is old story/neighborhood/market/wall/art-heavy, prefer URBAN_LEGEND when available.
            - Return the chosen genre's genreId as selectedGenreId.
            - Return the chosen genre's genreName as selectedGenreName.
            - Do not create a genre that is not in input.genreCatalog when genreCatalog is provided.
            - Keep genreId from genreCatalog.
            - Do not use genreCatalog answerSlots as final answer slots.
            - Do not use generic names such as 기록 추적 미션, 기억 찾기, 미스터리 탐방, 야외 추리 미션, 장소 탐색 미션.
            
            Fallback genre rules:
            - If input.genreCatalog is missing or empty, infer a safe temporary genre.
            - In that case, selectedGenreId must be "CUSTOM".
            - Also add "GENRE_CATALOG_MISSING" to validationWarnings.
            - The inferred genre must still be playable as an outdoor clue-based episode.
            
            Answer slot rules:
            - You MUST return exactly these three answerSlots in this order:
            - slotId=RELATED_PERSON, label=관련자, minClueCount=3.
            - slotId=ANSWER_CLUE, label=핵심 단서, minClueCount=3.
            - slotId=FINAL_DESTINATION, label=장소, minClueCount=3.
            - Do not rename these slotIds or labels.
            - Do not use genre-specific slot labels such as 범인, 범행도구, 사건장소, 최종 문장, 핵심 숫자, 암호해독 장소, 보관 장소 as answerSlots.
            
            Final answer keyword rules:
            - STRICT ANTI-HALLUCINATION RULE: Never generate meaningless repeating numbers or symbols like "11", "111", "222", "11. 11.". Every keyword MUST be a valid, contextual Korean word.
            - Each keyword must be an actual answer value, not a slot label.
            - RELATED_PERSON keyword: fictional role, suspect nickname, handler, courier, archivist, or group connected to the incident, e.g. 기록 중개인, 골목 사진사, 야간 보관자.
            - ANSWER_CLUE keyword: concrete object, number, code, phrase, tool, evidence, or condition, e.g. 깨진 렌즈, 접힌 우산, 1897, 붉은 인장.
            - ANSWER_CLUE sourcePlaceName/sourcePlaceOrder must point to the internal FINAL place.
            - ANSWER_CLUE sourceType should be VISIBLE_ELEMENT, KEYWORD, NUMBER, DESCRIPTION, ADMIN_MEMO, or AI_INFERRED_STORY_OBJECT based only on the internal FINAL place.
            - FINAL_DESTINATION keyword: the exact name of the input place whose role is FINAL. If no role is FINAL, use the last input place name.
            - Bad: label=관련자, keyword=관련자
            - Good: label=관련자, keyword=한서윤
            - Bad: label=핵심 단서, keyword=핵심 단서
            - Good: label=핵심 단서, keyword=<new contextual object or condition from the input, not a copied example>
            - Bad: label=장소, keyword=표지판 아래
            - Good: label=장소, keyword=<the actual FINAL place name from input>
            
            Keyword quality rules:
            - Keep each keyword short, concrete, atomic, and easy to type.
            - Prefer nouns, fictional role names, object names, short place features, short conditions, numbers, or short phrases.
            - A keyword should usually be 1 to 6 Korean words.
            - Every keyword must be inferable from two or more indirect clues and easy for a player to type.
            - Do not output poetic phrases, full sentences, or title-like expressions as one keyword.
            - Do not use vague generic words alone.
            - Forbidden generic keywords: 단서, 기록, 문서, 메모, 진실, 비밀, 장소, 물건, 사건, 흔적, 정보, 기억, 조건, 정체.
            - Forbidden abstract or poetic keywords: 다시 찾은 일상, 잊힌 기억, 숨겨진 진실, 평화의 의미, 되찾은 시간.
            - A keyword must not equal its slot label, a shortened slot label, or a generic restatement of that label.
            - RELATED_PERSON and ANSWER_CLUE must not be selected place names, route names, or neighborhood names.
            - FINAL_DESTINATION must be the selected internal final place name.
            - Bad place-derived answers: 해방촌마을, 해방촌 언덕길, 전쟁기념관 야외전시장.
            - Bad abstract answers: 다시 찾은 일상, 잊힌 기억, 숨겨진 진실, 평화의 의미.
            - Bad label answers: 핵심 물건, 최종 목적지, 해제 문구, 정체, 조건.
            - Good concrete answers: 녹슨 철모, 낮은 돌계단, 세 개의 귀환 표식, 귀환 기록자, 붉은 표식, 낡은 사진첩, 같은 방향의 화살표.
            - Do not use adjective-heavy forms unless the adjective is truly the answer.
            - Avoid: 잊혀진, 숨겨진, 가려진, 봉인된, 사라진, 오래된, 비밀스러운.
            - Avoid possessive forms using "의" unless absolutely necessary.
            - Do not use a full selected place name for RELATED_PERSON or ANSWER_CLUE.
            - Do use the full selected final place name for FINAL_DESTINATION.
            - Do not use a real person's name as a final answer keyword.
            
            Clue grounding rules:
            - Each final answer keyword must have a sourceBasis.
            - sourceBasis must briefly explain which input data inspired the keyword.
            - If possible, include sourcePlaceOrder.
            - Do not claim there is a sign, plaque, number, sculpture, stair, mural, or visible object unless it exists in visibleElements, numbers, description, keywords, or adminMemo.
            - Do not force restaurants, cafes, or shops into unsupported historical claims.
            - When commercial facilities dominate the route, prefer supplied observable shapes, colors, arrangements, movement, return, memory, and record motifs.
            
            When place data is thin:
            - Do not say admin confirmation is required.
            - Do not say official description is missing.
            - Do not ask the admin to add field observation, visible elements, memo, or TourAPI explanation.
            - Instead, convert place name, address, category-like words, route shape, and surrounding context into fictional story clues.
            - Use AI_INFERRED_STORY_OBJECT for fictional record objects.
            - Use AI_INFERRED_ROUTE_FEATURE for fictional route or direction features.
            - Use AI_INFERRED_OBSERVATION for fictional comparison or confirmation conditions.
            - sourceText must explain why the keyword was set as a story clue.
            - sourceText must be a story-design rationale, not an admin instruction, API attribution, warning, or field-work request.
            
            Forbidden text in keyword, sourceBasis, sourceText, rationale, finalQuestionGuide:
            - 관리자 확인
            - 검수 필요
            - 공식 설명 없음
            - 현장 관찰 필요
            - 현장 메모
            - TourAPI 기반
            - Kakao Local 기준
            - 주변 확인 후보
            - 실제 운영 전
            - 데이터 보강
            
            Difficulty and risk rules:
            - difficulty must be one of EASY, NORMAL, HARD.
            - risk must be one of OK, REVIEW_REQUIRED, TOO_ABSTRACT, TOO_LONG, REAL_PERSON_RISK, PLACE_NAME_RISK, WEAK_SOURCE.
            - Use REVIEW_REQUIRED or WEAK_SOURCE if the keyword is useful but the source data is thin.
            - Use TOO_ABSTRACT if the keyword is too vague.
            - Use PLACE_NAME_RISK if the keyword resembles a selected place name.
            - Use REAL_PERSON_RISK if the keyword might be interpreted as a real person.
            
            Final question guide rules:
            - finalQuestionGuide must explain what the player will ultimately submit.
            - MUST naturally integrate all the generated finalAnswerKeywords into one clear sentence.
            - Example: "기록 중개인이 붉은 인장을 대한문에서 확인했다." (Using the exact generated 관련자, 핵심 단서, 장소 keywords).
            - Do not mechanically list slot labels followed by "종합해 최종 결론을 보고하게 한다".
            - Describe that the player combines 관련자, 핵심 단서, and 장소 clues at the final destination and submits a sentence containing all three keywords.
            
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
                  "sourceType": "VISIBLE_ELEMENT|KEYWORD|NUMBER|DESCRIPTION|ADMIN_MEMO|SITE_ENRICHMENT|FINAL_DESTINATION|AI_INFERRED_STORY_OBJECT|AI_INFERRED_ROUTE_FEATURE|AI_INFERRED_OBSERVATION",
                  "sourcePlaceName": "string",
                  "sourceText": "string",
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
        List<String> models = geminiCallModels();
        RestClientException lastRequestException = null;
        String lastModel = models.get(0);

        for (String model : models) {
            lastModel = model;
            try {
                return callGeminiModel(model, body);
            } catch (RestClientException e) {
                lastRequestException = e;
                if (!isTransientGeminiFailure(e)) {
                    break;
                }
            }
        }

        if (lastRequestException != null) {
            String reason = isTransientGeminiFailure(lastRequestException)
                    ? "Gemini 모델이 일시적으로 과부하 상태입니다. 잠시 후 다시 시도하세요."
                    : "Gemini 호출에 실패했습니다. gemini.api.key와 gemini.model 설정을 확인하세요.";
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED",
                    reason + " 마지막 시도 모델=" + lastModel + ", 원인: " + lastRequestException.getMessage());
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 호출에 실패했습니다.");
    }

    private String callGeminiModel(String model, Map<String, Object> body) {
        String url = geminiBaseUrl.replaceAll("/+$", "") + "/models/" + model + ":generateContent";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());
        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            if (blank(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_EMPTY_RESPONSE", "Gemini가 빈 초안을 반환했습니다.");
            }
            return extractJson(text);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_RESPONSE_PARSE_FAILED", "Gemini 응답을 초안 JSON으로 해석할 수 없습니다. 모델이 JSON 스키마를 지켰는지 확인하세요.");
        }
    }

    private List<String> geminiCallModels() {
        String configured = blank(geminiModel) ? "gemini-2.5-flash" : geminiModel.trim();
        List<String> models = new ArrayList<>();
        models.add(configured);
        if (!same(configured, "gemini-2.5-flash")) {
            models.add("gemini-2.5-flash");
        }
        return models;
    }

    private boolean isTransientGeminiFailure(RestClientException e) {
        if (e instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 429 || status == 503 || status == 502 || status == 504;
        }
        String message = e.getMessage();
        return message != null && (message.contains("429")
                || message.contains("502")
                || message.contains("503")
                || message.contains("504")
                || message.contains("UNAVAILABLE")
                || message.contains("high demand"));
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

        int assignableHintCount = Math.max(0, totalMissionCount - requiredStartCount - requiredFinalCount);
        double answerRatio = policy != null && policy.getAnswerHintRatio() != null
                ? Math.max(0.0, policy.getAnswerHintRatio())
                : 0.0;
        double destinationRatio = policy != null && policy.getDestinationHintRatio() != null
                ? Math.max(0.0, policy.getDestinationHintRatio())
                : 0.0;
        double ratioTotal = answerRatio + destinationRatio;
        int minAnswerHints = ratioTotal > 0
                ? (int) Math.round(assignableHintCount * answerRatio / ratioTotal)
                : 0;
        int minDestinationHints = Math.max(0, assignableHintCount - minAnswerHints);

        if (answerRatio > 0) {
            if (answerHintCount < minAnswerHints) {
                addFinding(findings, "WARN", "LOW_ANSWER_HINT_COUNT", "ANSWER_HINT count is lower than missionPolicy.answerHintRatio.", null);
            }
        }

        if (totalMissionCount >= 5 && destinationRatio > 0) {
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

        if (defaultBlocked.contains(compactAnswer) || isContextualAnswerFragment(answer)) {
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
                      - Puzzles must use only provided visibleElements, numbers, keywords, description, adminMemo, and usablePuzzleSources.
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
        draft.setEra(resolveExplicitEra(draft.getEra(), request));
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
        Set<String> usedPuzzleAnswers = new java.util.LinkedHashSet<>();
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
            if ("INITIAL_SOUND".equals(normalize(mission.getPuzzleType()))) {
                mission.setPuzzleType("PATTERN");
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            mission.setAnswerFormat(ANSWER_FORMATS.contains(normalize(mission.getAnswerFormat())) ? normalize(mission.getAnswerFormat()) : answerFormat(place));
            if ("NUMBER_LOCK".equals(normalize(mission.getPuzzleType())) && lacksProvidedNumber(request, i + 1)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (usesPlaceNameTextPuzzle(mission, place) || usesWeakTextExtractionPuzzle(mission) || shouldUseReviewFallback(mission, place)) {
                applyPlayableStoryPuzzle(mission, place, role, i);
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            boolean duplicateAnswer = !blank(mission.getAnswer())
                    && usedPuzzleAnswers.contains(compact(mission.getAnswer()));
            if (hasInvalidPuzzleAnswer(mission, place, request) || duplicateAnswer) {
                String replacement = fallbackAnswer(place, request, usedPuzzleAnswers, mission);
                applyGroundedPuzzleAnswer(mission, place, role, replacement);
                warnings.add("Mission " + (i + 1) + " answer was a place name or invalid fallback; review before publishing.");
            }
            if (blank(mission.getAnswer())) {
                String replacement = fallbackAnswer(place, request, usedPuzzleAnswers, mission);
                applyGroundedPuzzleAnswer(mission, place, role, replacement);
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
            sanitizeAnswerClueReward(mission, request, i, warnings);
            if (mission.getHints() == null || mission.getHints().size() < 3) {
                warnings.add("Mission " + (i + 1) + " was normalized; review before publishing.");
            }
            if (blank(mission.getGroundRule())) {
                mission.setGroundRule("제공된 visibleElements, numbers, keywords, description, adminMemo 안의 정보만 근거로 사용합니다.");
            }
            sanitizeCategoryCodes(mission);
            if (!blank(mission.getAnswer()) && !"검수필요".equals(mission.getAnswer())) {
                usedPuzzleAnswers.add(compact(mission.getAnswer()));
            }
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
        draft.setFinalAnswer(naturalFinalAnswer(keywords, slotLabels));

        if (!finalQuestionNamesEverySlot(draft.getFinalQuestion(), slotLabels)) {
            draft.setFinalQuestion(explicitFinalQuestion(slotLabels));
            warnings.add("Final question was rewritten to name every approved answer slot.");
        }

        if (!finalAnswerIncludesEveryKeywordAsSentence(draft.getFinalAnswer(), keywords)) {
            draft.setFinalAnswer(genericFinalAnswerSentence(keywords, slotLabels));
            warnings.add("Final answer was rewritten as a natural sentence using every approved keyword.");
        }

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
            return commandBriefing("항구 일대에 흩어진 기록이 서로 다른 이동 경로를 가리키고 있네",
                    "흔적이 지워지기 전에 암호와 기록을 대조해 숨겨진 경로의 의미와 마지막 자료의 행방을 밝혀내게");
        }

        if (containsAny(source, "정체", "조직", "세력", "은신처", "거점", "아지트")) {
            return commandBriefing("도시 곳곳의 표식이 하나의 숨겨진 역할과 이동 경로를 가리키고 있네",
                    "상대가 흔적을 거두기 전에 현장 기록을 대조해 관계자의 역할과 감춰진 거점의 조건을 밝혀내게");
        }

        if (containsAny(source, "보물", "상자", "봉인", "열쇠", "해금")) {
            return commandBriefing("오래 봉인된 물건의 행방을 둘러싼 기록이 서로 어긋나고 있네",
                    "봉인 장치가 다시 잠기기 전에 현장의 암호와 보관 흔적을 따라 물건의 정체와 확인 조건을 밝혀내게");
        }

        if (containsAny(source, "암호", "문장", "숫자", "해독")) {
            return commandBriefing("여러 조사 지점에서 같은 암호가 서로 다른 형태로 반복되고 있네",
                    "암호 체계가 폐기되기 전에 숫자와 표식의 연결 규칙을 찾아 마지막 메시지의 의미를 밝혀내게");
        }

        if (containsAny(source, "실종", "사라진", "마지막")) {
            return commandBriefing("한 인물 또는 기록이 사라졌고 마지막 동선을 둘러싼 자료도 서로 어긋나고 있네",
                    "남은 흔적까지 사라지기 전에 현장 단서와 물건을 대조해 실종의 이유와 마지막 행방을 밝혀내게");
        }

        String genre = blank(selectedGenre) ? "이 미션" : selectedGenre;
        return commandBriefing("선택된 장소 일대에서 오래된 기록과 서로 어긋나는 단서가 발견됐네",
                "흔적이 훼손되기 전에 현장 단서와 암호를 차례로 대조해 " + genre + "의 결론을 밝혀내게");
    }

    private String commandBriefing(String situation, String directive) {
        return "요원, " + situation + ". 시간이 많지 않네. "
                + directive + ". 당황할 필요는 없네. 평소 훈련한 대로 현장을 나누어 확인하면 충분히 해결할 수 있을 걸세. "
                + "내가 작전 기록을 통해 지원하겠네. 미션 파일을 확인하고 임무를 시작하도록.";
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
        return fallbackAnswer(place, request, Set.of());
    }

    private String fallbackAnswer(
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request,
            Set<String> usedAnswers) {
        return fallbackAnswer(place, request, usedAnswers, null);
    }

    private String fallbackAnswer(
            AiEpisodeDraftRequest.PlaceInput place,
            AiEpisodeDraftRequest request,
            Set<String> usedAnswers,
            AiEpisodeDraftResponse.MissionDraft mission) {
        List<String> candidates = new ArrayList<>();
        if (place != null) {
            if (place.getNumbers() != null) candidates.addAll(place.getNumbers());
            if (place.getVisibleElements() != null) candidates.addAll(place.getVisibleElements());
            if (place.getKeywords() != null) candidates.addAll(place.getKeywords());
            if (place.getUsablePuzzleSources() != null) candidates.addAll(place.getUsablePuzzleSources());
            addExtractedBasisCandidates(candidates, place.getAdminMemo(), place.getName());
            addExtractedBasisCandidates(candidates, place.getDescription(), place.getName());
        }
        if (mission != null) {
            String placeName = place == null ? null : place.getName();
            addExtractedBasisCandidates(candidates, mission.getStoryText(), placeName);
            addExtractedBasisCandidates(candidates, mission.getRewardClue(), placeName);
        }
        return candidates.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .filter(value -> !isBadPuzzleAnswerBasis(value, place))
                .filter(value -> !isLowQualityGenericValue(value))
                .filter(value -> !containsAnyApprovedFinalKeyword(value, request))
                .filter(value -> usedAnswers == null || !usedAnswers.contains(compact(value)))
                .findFirst()
                .orElse("검수필요");
    }

    private void applyGroundedPuzzleAnswer(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            String role,
            String answer) {
        mission.setAnswer(answer);
        String source = resolvePuzzleAnswerSource(answer, place);
        boolean reviewRequired = "검수필요".equals(answer);
        boolean numberAnswer = "NUMBER".equals(source);
        mission.setPuzzleType(numberAnswer ? "NUMBER_LOCK" : recommendedPuzzleTypeForSource(source, role));
        mission.setAnswerFormat(numberAnswer ? "NUMBER" : "TEXT");
        mission.setPuzzleAnswerSource(source);
        mission.setPuzzleAnswerRisk(reviewRequired ? "REVIEW_REQUIRED" : "OK");
        mission.setVerificationLevel(reviewRequired ? "FIELD_REQUIRED" : "AUTO_OK");

        if (reviewRequired) {
            mission.setQuestionText("이 지점은 현장 근거를 확인한 뒤 퍼즐 정답을 확정해야 합니다.");
            mission.setHints(reviewRequiredHints());
            return;
        }

        mission.setQuestionText(switch (source) {
            case "NUMBER" -> "요원, 이 장소의 제공된 숫자 기록 중 미션 파일의 순서와 일치하는 값을 입력하게.";
            case "VISIBLE_ELEMENT" -> "요원, 현장에서 확인 가능한 요소 중 미션 파일의 묘사와 일치하는 대상을 입력하게.";
            case "KEYWORD" -> "요원, 장소 자료에 반복된 구체 키워드 중 현재 지령과 연결되는 값을 입력하게.";
            case "SITE_ENRICHMENT" -> "요원, 사이트 보강 자료에서 확인된 구체 요소 중 현재 지령과 연결되는 값을 입력하게.";
            case "ADMIN_MEMO" -> "요원, 관리자 현장 메모의 구체 관찰 내용을 미션 파일과 대조해 확인어를 입력하게.";
            case "DESCRIPTION" -> "요원, 장소 설명에 제시된 구체 특징을 현장 단서와 연결해 확인어를 입력하게.";
            default -> "요원, 제공된 장소 근거를 미션 파일과 대조해 짧은 확인어를 입력하게.";
        });
        mission.setHints(List.of(
                sourceHint(source),
                "장소명이나 지역명이 아니라 이 지점의 구체적인 숫자, 사물, 형태 또는 특징을 찾게.",
                "정답은 제공된 장소 자료 안에서 그대로 확인할 수 있는 짧은 값일세."
        ));
        mission.setGroundRule("퍼즐 정답 [" + answer + "]은 sourceInput.place의 " + source + " 근거에서 선택했습니다.");
    }

    private String recommendedPuzzleTypeForSource(String source, String role) {
        if ("NUMBER".equals(source)) return "NUMBER_LOCK";
        if ("VISIBLE_ELEMENT".equals(source)) return "OBSERVATION";
        if ("SITE_ENRICHMENT".equals(source)) return "OBSERVATION";
        if ("KEYWORD".equals(source)) return "PATTERN";
        return "FINAL".equals(normalize(role)) ? "STORY_COMBINATION" : "PATTERN";
    }

    private String sourceHint(String source) {
        return switch (source) {
            case "NUMBER" -> "제공된 숫자 후보만 사용하게.";
            case "VISIBLE_ELEMENT" -> "visibleElements에 기록된 관찰 요소를 먼저 확인하게.";
            case "KEYWORD" -> "keywords에 있는 구체 명사와 현장 특징을 비교하게.";
            case "SITE_ENRICHMENT" -> "usablePuzzleSources에 보강된 구체 관찰 요소를 먼저 확인하게.";
            case "ADMIN_MEMO" -> "adminMemo에서 실제 관찰 대상을 나타내는 명사구를 찾게.";
            case "DESCRIPTION" -> "description에서 장소의 구체 사물이나 형태를 나타내는 표현을 찾게.";
            default -> "제공된 장소 근거를 먼저 확인하게.";
        };
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
            mission.setQuestionText("요원, 화면의 단서 장치를 조작해 이 지점의 확인 키를 복원하게. 장소명 글자나 상호명 문자를 쓰지 말고, 장치 패턴과 미션 파일의 단서 흐름만 대조하게.");
            mission.setHints(List.of(
                    "장소명, 상호명, 간판 글자 추출은 사용하지 않네.",
                    "장치에 표시된 순서, 색, 위치, 기억 패턴을 먼저 해제하게.",
                    "해제 후 제출되는 확인 키가 이 장소의 클리어 판정으로 처리되네."
            ));
            mission.setPuzzleType("PATTERN");
            mission.setAnswerFormat("TEXT");
            mission.setPuzzleAnswerSource("FICTION_SAFE");
            mission.setPuzzleAnswerRisk("OK");
            mission.setVerificationLevel("ADMIN_REVIEW");
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
        String question = compact(String.join(" ",
                blank(mission.getQuestionText()) ? "" : mission.getQuestionText(),
                mission.getHints() == null ? "" : String.join(" ", mission.getHints())));
        String answer = compact(mission.getAnswer());
        boolean explicitlyUsesNameText = containsAny(question + answer,
                "장소명", "지명", "상호명", "지역명",
                "장소이름", "가게이름", "명칭의글자", "이름의글자");
        boolean referencesPlaceName = question.contains(compactPlaceName);
        boolean asksCharacterExtraction = containsAny(question,
                "letter", "syllable", "initial", "first", "second", "third", "fourth", "last", "substring", "nth",
                "첫글자", "마지막글자", "두번째글자", "초성", "자음", "모음", "몇번째글자", "글자를조합", "글자조합");
        boolean answerFromPlaceName = !answer.isBlank() && answer.length() <= 4 && compactPlaceName.contains(answer);
        return explicitlyUsesNameText || (referencesPlaceName && (asksCharacterExtraction || answerFromPlaceName));
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
        if (isGenericBasisLabel(answer)
                || isLowQualityGenericValue(answer)
                || isContextualAnswerFragment(mission.getAnswer())
                || isPlaceNameAnswer(answer, place.getName())) {
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
                "요원",
                "미션",
                "미션파일",
                "지점",
                "자료",
                "흐름",
                "방향",
                "비극",
                "계단",
                "돌계단",
                "마을",
                "골목",
                "북쪽",
                "남쪽",
                "동쪽",
                "서쪽",
                "입구",
                "출구",
                "건물",
                "거리",
                "광장",
                "음식점",
                "카페",
                "상점",
                "관리자검수",
                "검수필요",
                "확인필요"
        ).contains(compactAnswer);
    }


    private boolean usesWeakTextExtractionPuzzle(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = compact(String.join(" ", blank(mission.getQuestionText()) ? "" : mission.getQuestionText(), blank(mission.getAnswer()) ? "" : mission.getAnswer(), mission.getHints() == null ? "" : String.join(" ", mission.getHints())));
        return containsAny(text,
                "lettercount", "nthletter", "syllable", "initialonly", "firstletter", "lastletter", "combineinorder", "substring",
                "첫글자", "마지막글자", "두번째글자", "초성", "자음", "모음", "몇번째글자", "글자를조합", "글자조합",
                "글자·음절", "글자/음절", "글자-음절", "문자추출", "문자 추출", "음절추출", "음절 추출",
                "장소명", "지명", "상호명", "지역명", "장소이름", "가게이름");
    }


    private void applyPlayableStoryPuzzle(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest.PlaceInput place,
            String role,
            int index
    ) {
        String basis = bestPuzzleBasis(place);

        if (isBadPuzzleAnswerBasis(basis, place)) {
            basis = extractBasisPhrase(mission.getStoryText(), place == null ? null : place.getName());
        }

        if (isBadPuzzleAnswerBasis(basis, place)) {
            basis = extractBasisPhrase(mission.getRewardClue(), place == null ? null : place.getName());
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
        if (place.getUsablePuzzleSources() != null) {
            String enriched = place.getUsablePuzzleSources().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!blank(enriched)) return enriched;
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
        List<String> candidates = new ArrayList<>();
        addExtractedBasisCandidates(candidates, text, placeName);
        return candidates.stream().findFirst().orElse(null);
    }

    private void addExtractedBasisCandidates(List<String> candidates, String text, String placeName) {
        if (blank(text)) {
            return;
        }
        String compactPlaceName = compact(placeName);
        String cleaned = text
                .replaceAll("[\\[\\]{}()\"'`.,:;!?/\\\\|<>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String[] tokens = cleaned.split(" ");
        for (int width : List.of(3, 2, 1)) {
            for (int i = 0; i + width <= tokens.length; i++) {
                String candidate = String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + width)).trim();
                if (candidate.length() > 18 || isContextualAnswerFragment(candidate)) {
                    continue;
                }
            String compactCandidate = compact(candidate);
                if (isBadPuzzleAnswerBasis(candidate, null)) {
                    continue;
                }
                if (!compactPlaceName.isBlank()
                        && (compactPlaceName.contains(compactCandidate) || compactCandidate.contains(compactPlaceName))) {
                    continue;
                }
                candidates.add(candidate);
            }
        }
    }


    private String markerRoleLabel(String role) {
        return switch (role) {
            case "ANSWER_HINT" -> "핵심 단서";
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
            mission.setAnswer(fallbackAnswer(place, request, Set.of(), mission));
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
                        || containsAnyApprovedFinalKeyword(card.getSuspiciousPoint(), request)
                        || revealsSuspectResolution(card.getSuspiciousPoint())) {
                    card.setSuspiciousPoint("진술과 이동 기록 사이에 확인이 필요한 간접적인 차이가 있습니다.");
                    changed = true;
                }
                if (containsFinalKeywordOrAlias(card.getAlibiSummary(), draft)
                        || containsAnyApprovedFinalKeyword(card.getAlibiSummary(), request)
                        || revealsSuspectResolution(card.getAlibiSummary())) {
                    card.setAlibiSummary("정해진 시간에 자신이 맡은 절차를 처리하고 있었다고 진술합니다.");
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
                        .displayName("한서윤")
                        .shortDescription("사라진 봉투를 처음 맡긴 의뢰인")
                        .relationToVictim("사라진 문서의 접수를 요청했고 봉투 보관 절차를 알고 있던 인물입니다.")
                        .suspiciousPoint("문서가 사라진 18시 20분 직전 봉투의 봉인을 확인한 사람으로 기록되어 있습니다.")
                        .alibiSummary("그 시간에는 접수대 밖 복도에서 다음 접수 순서를 기다렸다고 진술합니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("정리관")
                        .displayName("강도윤")
                        .shortDescription("사진과 메모 순서를 정리한 보관 담당자")
                        .relationToVictim("조사 자료를 시간순으로 정리하고 보관함 출입 기록을 관리했습니다.")
                        .suspiciousPoint("17시 50분 보관함 반출 기록에 그의 서명이 있어 자료 순서를 확인할 위치에 있었습니다.")
                        .alibiSummary("자료실에서 시간순 목록을 정리하고 있었다고 말하며, 당시 출입 기록이 따로 남아 있다고 설명합니다.")
                        .build(),
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("전달자")
                        .displayName("윤재하")
                        .shortDescription("마지막 쪽지를 운반한 연락책")
                        .relationToVictim("마지막 쪽지를 전달했지만 문서 원본 보관 절차에는 참여하지 않았습니다.")
                        .suspiciousPoint("18시 이후 봉투를 들고 이동한 목격 기록이 있어 동선 확인 대상이 되었습니다.")
                        .alibiSummary("봉투를 전달했을 뿐 원본 보관함에는 접근하지 않았다고 진술합니다.")
                        .build()
        );
        for (AiEpisodeDraftResponse.SuspectDraft fallback : defaults) {
            if (suspects.size() >= 3) {
                break;
            }
            suspects.add(fallback);
        }
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (suspect == null) {
                continue;
            }
            AiEpisodeDraftResponse.SuspectDraft fallback = defaults.get(Math.floorMod(i, defaults.size()));
            if (isRoleLikeSuspectDisplayName(suspect.getDisplayName())) {
                suspect.setDisplayName(fallback.getDisplayName());
            }
            if (blank(suspect.getAlias())) {
                suspect.setAlias(fallback.getAlias());
            }
            if (isWeakSuspectFrontText(suspect.getShortDescription())) {
                suspect.setShortDescription(fallback.getShortDescription());
            }
            if (isWeakSuspectDetailText(suspect.getRelationToVictim())) {
                suspect.setRelationToVictim(fallback.getRelationToVictim());
            }
            if (isWeakSuspectDetailText(suspect.getSuspiciousPoint()) || revealsSuspectResolution(suspect.getSuspiciousPoint())) {
                suspect.setSuspiciousPoint(fallback.getSuspiciousPoint());
            }
            if (isWeakSuspectDetailText(suspect.getAlibiSummary()) || revealsSuspectResolution(suspect.getAlibiSummary())) {
                suspect.setAlibiSummary(fallback.getAlibiSummary());
            }
        }
        if (draft.getSuspects() == null || draft.getSuspects().size() < 3) {
            warnings.add("관계자 카드가 3개 미만이라 역할이 분리된 기본 관계자 카드로 보강했습니다.");
        }
        draft.setSuspects(suspects);
    }

    private boolean isRoleLikeSuspectDisplayName(String value) {
        if (blank(value)) {
            return true;
        }
        String compactValue = compact(value);
        return compactValue.length() <= 2
                || containsAny(value, "의뢰인", "정리관", "전달자", "연락책", "보관 담당자", "기록 중개인", "관계자");
    }

    private boolean isWeakSuspectFrontText(String value) {
        if (blank(value)) {
            return true;
        }
        return value.length() > 40 || containsAny(value, "수상", "의심스럽", "진실", "숨긴", "힌트 카드", "정답 힌트", "목적지 힌트");
    }

    private boolean isWeakSuspectDetailText(String value) {
        if (blank(value) || containsAny(value, "힌트 카드", "정답 힌트", "목적지 힌트", "카드 하나", "카드의")) {
            return true;
        }
        boolean concrete = containsAny(value,
                "문서", "시간", "시각", "봉투", "물건", "목격", "기록", "사진", "메모", "서명", "접수", "보관", "반출", "출입", "동선");
        boolean abstractOnly = containsAny(value, "수상", "의심스럽", "진실을 숨", "잠적", "비밀을 숨");
        return !concrete || abstractOnly;
    }

    private boolean revealsSuspectResolution(String value) {
        return containsAny(value,
                "핵심 모순", "배제 근거", "결정적 모순", "모순이 드러", "모순을 드러",
                "범인", "진범", "배제할 수", "배제된다", "배제됩니다", "결정적 증거",
                "알리바이가 거짓", "진술이 거짓", "접근하지 못했", "만들지 않았", "생성하지 않았");
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

    private boolean suppressGeminiFinding(
            AiEpisodeDraftValidationResponse.Finding finding,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput) {
        if (finding == null || finding.getCode() == null || draft == null || draft.getMissions() == null) {
            return false;
        }
        String code = normalize(finding.getCode());
        if ("STORY_OBJECTIVE_MISMATCH".equals(code)) {
            List<AiEpisodeDraftValidationResponse.Finding> localFindings = new ArrayList<>();
            validateStoryObjectiveAlignment(draft, sourceInput, localFindings);
            return localFindings.stream()
                    .noneMatch(item -> "STORY_OBJECTIVE_MISMATCH".equals(normalize(item.getCode())));
        }
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
        AiEpisodeDraftRequest.PlaceInput sourcePlace = sourcePlace(sourceInput, finding.getMissionOrder());
        if (List.of("INSUFFICIENT_PUZZLE_DATA", "PLACEHOLDER_PUZZLE").contains(code) && isReviewPlaceholder(mission)) {
            return true;
        }
        if ("CLUE_USES_PLACE_NAME_TEXT_EXTRACTION".equals(code)
                && sourcePlace != null
                && !usesPlaceNameTextPuzzle(mission, sourcePlace)
                && !usesWeakTextExtractionPuzzle(mission)) {
            return true;
        }
        if ("PUZZLE_ANSWER_RISK".equals(code)
                && sourcePlace != null
                && !hasInvalidPuzzleAnswer(mission, sourcePlace, sourceInput)
                && !isBlockedGenericPuzzleAnswer(mission.getAnswer(), sourceInput)) {
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

    private void sanitizeAnswerClueReward(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftRequest request,
            int index,
            List<String> warnings) {
        if (mission == null || !"ANSWER_CLUE".equals(normalize(mission.getRewardClueSlotId()))) {
            return;
        }
        String keyword = approvedKeywordForSlot(request, "ANSWER_CLUE");
        String reward = mission.getRewardClue();
        boolean unsafe = blank(reward)
                || containsAnyApprovedFinalKeyword(reward, request)
                || textContains(reward, keyword)
                || isVagueAnswerClueReward(reward)
                || !hasAnswerClueCategoryWord(reward)
                || !hasAnswerClueStateWord(reward);
        if (!unsafe) {
            return;
        }
        mission.setRewardClue(answerClueRewardFor(keyword, index));
        warnings.add("Mission " + (index + 1) + " reward clue was normalized; review before publishing.");
    }

    private String approvedKeywordForSlot(AiEpisodeDraftRequest request, String slotId) {
        if (request == null || request.getFinalAnswerKeywordItems() == null) {
            return "";
        }
        return request.getFinalAnswerKeywordItems().stream()
                .filter(item -> item != null && same(item.getSlotId(), slotId))
                .map(AiEpisodeDraftRequest.AnswerKeywordInput::getKeyword)
                .filter(value -> !blank(value))
                .findFirst()
                .orElse("");
    }

    private boolean isVagueAnswerClueReward(String reward) {
        return containsAny(reward,
                "뭔가 이상", "이상하다", "서로 맞지", "암시", "진실임", "가치롭게 여겼던", "의미를 가진다",
                "중요해 보인다", "관련 있어 보인다", "단서일 수 있다", "확인이 필요");
    }

    private boolean hasAnswerClueCategoryWord(String reward) {
        return containsAny(reward,
                "기록", "기록물", "기록서", "문서", "장부", "보고서", "증언", "진술", "알리바이",
                "목격자", "명령서", "출입 기록", "순찰 기록", "사진", "쪽지", "메모", "물건", "사람", "행동");
    }

    private boolean hasAnswerClueStateWord(String reward) {
        return containsAny(reward,
                "고쳐진", "바뀐", "누락", "빠진", "훼손", "찢긴", "봉인", "숨긴", "숨겨진", "위조",
                "조작", "거짓", "삭제", "변조", "다르게 적힌", "원본과 다른");
    }

    private String answerClueRewardFor(String keyword, int index) {
        String normalized = blank(keyword) ? "봉인 표식" : keyword.trim();
        int variant = Math.floorMod(index, 3);
        if (containsAny(normalized, "증언", "진술")) {
            return switch (variant) {
                case 0 -> "사건 시간대의 진술에서 꼭 있어야 할 문장이 빠져 있다.";
                case 1 -> "빠진 증언은 단순한 누락이 아니라 누군가의 행적을 가리기 위한 것으로 보인다.";
                default -> "여러 사람의 말보다 빠진 진술의 위치 자체가 핵심 단서다.";
            };
        }
        if (containsAny(normalized, "알리바이")) {
            return switch (variant) {
                case 0 -> "관계자의 알리바이에 실제 동선과 맞지 않는 시간 표시가 남아 있다.";
                case 1 -> "그 시간 표시는 착오가 아니라 일부러 꾸며낸 진술에 가깝다.";
                default -> "사건 당일 행적을 설명한 알리바이 자체가 핵심 단서다.";
            };
        }
        if (containsAny(normalized, "장부", "거래")) {
            return switch (variant) {
                case 0 -> "장부의 일부 항목이 원래 순서와 다르게 고쳐진 흔적이 있다.";
                case 1 -> "바뀐 항목은 단순 계산 실수가 아니라 특정 거래를 숨기기 위한 것으로 보인다.";
                default -> "사건 당일의 흐름을 적은 장부 자체가 핵심 단서다.";
            };
        }
        if (containsAny(normalized, "보고서", "명령서", "문서", "기록서", "기록")) {
            return switch (variant) {
                case 0 -> "기록물의 일부가 원래 내용과 다르게 고쳐진 흔적이 있다.";
                case 1 -> "바뀐 내용은 단순한 오기가 아니라 누군가의 행적을 숨기기 위한 것으로 보인다.";
                default -> "사건 당일의 동선을 적은 문서 자체가 핵심 단서다.";
            };
        }
        if (containsAny(normalized, "목격자", "사람")) {
            return switch (variant) {
                case 0 -> "목격자로 분류된 사람이 사건 기록에서 의도적으로 빠져 있다.";
                case 1 -> "빠진 사람은 우연한 누락이 아니라 현장 동선을 확인할 수 있는 인물이다.";
                default -> "사건 당일을 직접 본 사람의 존재 자체가 핵심 단서다.";
            };
        }
        return switch (variant) {
            case 0 -> "핵심 단서가 된 물건에는 원래 상태와 다르게 바뀐 흔적이 있다.";
            case 1 -> "그 변화는 우연한 손상이 아니라 누군가의 행적을 숨기려는 행동으로 보인다.";
            default -> "사건 당일의 흐름을 설명하는 물건 자체가 핵심 단서다.";
        };
    }

    private String naturalFinalSynopsis(List<String> labels) {
        String objective = labels.size() >= 2
                ? String.join(", ", labels) + "에 해당하는 단서를 확보하고 모든 흔적이 이어진 이유를 밝혀내게"
                : labels.get(0) + "에 해당하는 핵심 진실을 밝혀내게";
        return commandBriefing(
                "여러 장소에 흩어진 흔적이 하나의 미완성된 경로를 가리키고 있네",
                "상대가 기록을 회수하기 전에 " + objective
        );
    }

    private String resolveExplicitEra(String generatedEra, AiEpisodeDraftRequest request) {
        String source = String.join(" ",
                blank(request == null ? null : request.getEra()) ? "" : request.getEra(),
                blank(generatedEra) ? "" : generatedEra,
                request == null || request.getPlaces() == null ? "" : request.getPlaces().stream()
                        .filter(java.util.Objects::nonNull)
                        .flatMap(place -> java.util.stream.Stream.of(
                                place.getName(), place.getDescription(), place.getAdminMemo(),
                                place.getKeywords() == null ? "" : String.join(" ", place.getKeywords())
                        ))
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.joining(" "))
        );
        if (containsAny(source, "삼국", "고구려", "백제", "신라")) return "삼국시대";
        if (containsAny(source, "고려")) return "고려시대";
        if (containsAny(source, "대한제국", "1897", "1905", "정동")) return "대한제국 말기";
        if (containsAny(source, "일제강점기", "일제", "식민지")) return "일제강점기";
        if (containsAny(source, "조선 후기", "조선후기", "한양", "정조", "영조")) return "조선 후기";
        if (containsAny(source, "조선", "궁궐", "성곽")) return "조선시대";
        if (containsAny(source, "근대", "개화", "한국전쟁", "전쟁기념관", "산업화")) return "근현대";
        return "현대";
    }

    private String naturalFinalQuestion(List<String> labels) {
        if (hasPlanSlot(labels, "실종 원인")
                && hasPlanSlot(labels, "마지막 장소")
                && hasPlanSlot(labels, "관련 물건")) {
            return "관련 물건에 남은 흔적과 이동 기록을 대조하면, 실종 원인은 무엇이며 마지막 장소는 어디인가?";
        }
        if (hasPlanSlot(labels, "물건", "매체", "유물")
                && hasPlanSlot(labels, "보관", "목적지", "장소")
                && hasPlanSlot(labels, "조건", "표식", "문구")) {
            return "흩어진 흔적을 대조하면, 잃어버린 물건은 무엇이며 어디에 보관되어 있고 어떤 조건에서 확인할 수 있는가?";
        }
        if (hasPlanSlot(labels, "관련자")
                && hasPlanSlot(labels, "핵심 단서", "정답 단서")
                && hasPlanSlot(labels, "장소")) {
            return "관련자, 핵심 단서, 장소를 모두 연결하면 이번 미션의 최종 결론은 무엇인가?";
        }
        if (labels.size() == 2) {
            return "흩어진 단서를 종합하면, 사라진 대상의 정체와 그것이 향한 곳은 어디인가?";
        }
        if (labels.size() >= 3) {
            return "관계자의 행동과 남겨진 매체, 마지막 확인 조건을 연결하면 이번 미션의 전말은 무엇인가?";
        }
        return "모든 흔적을 하나로 연결했을 때 드러나는 이번 미션의 진실은 무엇인가?";
    }

    private String naturalFinalAnswer(List<String> keywords, List<String> labels) {
        if (keywords.size() == 1) {
            return "모든 흔적이 가리킨 진실은 " + keywords.get(0) + "이었다.";
        }
        if (keywords.size() == 2) {
            return withSubject(keywords.get(0)) + " 마지막 흔적이 가리킨 " + keywords.get(1) + "에 숨겨져 있었다.";
        }
        if (hasPlanSlot(labels, "관련자")
                && hasPlanSlot(labels, "핵심 단서", "정답 단서")
                && hasPlanSlot(labels, "장소")) {
            String person = keywordForSlot(labels, keywords, "관련자");
            String clue = keywordForSlot(labels, keywords, "핵심 단서", "정답 단서");
            String place = keywordForSlot(labels, keywords, "장소");
            return withSubject(person) + " " + withObject(clue) + " " + place + "에서 확인하려 했다.";
        }
        if (hasPlanSlot(labels, "실종 원인")
                && hasPlanSlot(labels, "마지막 장소")
                && hasPlanSlot(labels, "관련 물건")) {
            String cause = keywordForSlot(labels, keywords, "실종 원인");
            String lastPlace = keywordForSlot(labels, keywords, "마지막 장소");
            String relatedObject = keywordForSlot(labels, keywords, "관련 물건");
            return "관련 물건인 " + relatedObject + "에 남은 흔적을 대조한 결과, 실종 원인은 "
                    + cause + "이었고 마지막 장소는 " + lastPlace + "로 확인되었다.";
        }
        if (hasPlanSlot(labels, "물건", "매체", "유물")
                && hasPlanSlot(labels, "보관", "목적지", "장소")
                && hasPlanSlot(labels, "조건", "표식", "문구")) {
            return withSubject(keywords.get(0)) + " " + keywords.get(1) + "에 보관되어 있었고, "
                    + withObject(keywords.get(2)) + " 맞춰야 기록을 확인할 수 있었다.";
        }
        return withSubject(keywords.get(0)) + " " + withObject(keywords.get(1)) + " 옮겼고, "
                + withSubject(String.join(", ", keywords.subList(2, keywords.size())))
                + " 성립하는 순간 기록을 확인하려 했다.";
    }

    private boolean finalQuestionNamesEverySlot(String question, List<String> labels) {
        return naturalQuestionCoversSlots(question, labels == null ? List.of() : labels);
    }

    private String explicitFinalQuestion(List<String> labels) {
        List<String> safeLabels = labels == null ? List.of() : labels.stream()
                .filter(value -> !blank(value))
                .distinct()
                .toList();
        if (safeLabels.isEmpty()) {
            return "모든 단서를 종합하면 사건의 전말은 무엇인가?";
        }
        return String.join(", ", safeLabels) + "을 바탕으로 사건의 전말은 무엇인가?";
    }

    private boolean finalAnswerIncludesEveryKeywordAsSentence(String answer, List<String> keywords) {
        if (blank(answer) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(value -> !blank(value))
                .allMatch(keyword -> textContains(answer, keyword))
                && answer.codePointCount(0, answer.length()) >= 12;
    }

    private String genericFinalAnswerSentence(List<String> keywords, List<String> labels) {
        List<String> safeKeywords = keywords == null ? List.of() : keywords.stream()
                .filter(value -> !blank(value))
                .toList();
        if (safeKeywords.isEmpty()) {
            return "모든 단서를 종합해 사건의 전말을 확인했다.";
        }
        return naturalFinalAnswer(safeKeywords, labels == null ? List.of() : labels);
    }

    private String keywordForSlot(List<String> labels, List<String> keywords, String... targets) {
        int size = Math.min(labels == null ? 0 : labels.size(), keywords == null ? 0 : keywords.size());
        for (int i = 0; i < size; i++) {
            if (containsAny(labels.get(i), targets)) {
                return keywords.get(i);
            }
        }
        return keywords == null || keywords.isEmpty() ? "" : keywords.get(0);
    }

    private boolean hasPlanSlot(List<String> labels, String... targets) {
        return labels != null && labels.stream().anyMatch(label -> containsAny(label, targets));
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
            if (isGenericCardText(suspect.getSuspiciousPoint()) || revealsSuspectResolution(suspect.getSuspiciousPoint())) {
                suspect.setSuspiciousPoint("그의 진술에는 " + clue + "와 함께 대조해야 할 시간과 동선 차이가 남아 있습니다.");
            }
            if (revealsSuspectResolution(suspect.getAlibiSummary())) {
                suspect.setAlibiSummary("정해진 시간에 자신이 맡은 절차를 처리하고 있었다고 진술합니다.");
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

        if (isContextualAnswerFragment(value)) {
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

    private boolean isContextualAnswerFragment(String value) {
        if (blank(value)) {
            return true;
        }
        String normalized = value.trim();
        String compactValue = compact(normalized);
        if (Set.of(
                "서울의", "지역의", "장소의", "후보의", "주변의", "현장의", "미션의",
                "서울", "서울시", "수도권", "강원권", "충청권", "전라권", "경상권", "제주"
        ).contains(compactValue)) {
            return true;
        }
        if (normalized.matches("^[가-힣A-Za-z0-9]+의$")) {
            return true;
        }
        return containsAny(compactValue,
                "장소명주소주변동선정보",
                "야외스토리미션의배경장소",
                "바탕으로사용합니다",
                "후보정보기반",
                "관리자입력",
                "공식설명",
                "관광지정보");
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

        if (place.getUsablePuzzleSources() != null
                && place.getUsablePuzzleSources().stream().anyMatch(value -> same(value, answer))) {
            return "SITE_ENRICHMENT";
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
