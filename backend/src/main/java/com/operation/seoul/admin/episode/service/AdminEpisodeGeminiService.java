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
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
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
        JsonNode root = parseJson(callGemini(buildPlanPrompt(request)), "GEMINI_PLAN_PARSE_FAILED");
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
            JsonNode root = parseJson(callGemini(buildDraftPrompt(request)), "GEMINI_DRAFT_PARSE_FAILED");
            draft = objectMapper.treeToValue(draftJsonNode(root), AiEpisodeDraftResponse.EpisodeDraft.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini JSON parse failed.");
        }
        return buildDraftResponse(draft, request, new ArrayList<>());
    }

    private AiEpisodeDraftResponse buildDraftResponse(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        applyApprovedFinalAnswerContract(draft, request, safeWarnings);
        normalizeDraft(draft, request);
        applyDeterministicCrimeMysteryGuardrail(draft, request, safeWarnings);
        AiEpisodeDraftValidationRequest validationRequest = new AiEpisodeDraftValidationRequest();
        validationRequest.setDraft(draft);
        validationRequest.setSourceInput(request);
        AiEpisodeDraftValidationResponse validation = validateDraft(validationRequest);
        return AiEpisodeDraftResponse.builder()
                .generatorType("GEMINI_CRIME_MYSTERY_RAG")
                .message("TourAPI \uc7a5\uc18c \uc815\ubcf4\ub97c \ubc30\uacbd \ubaa8\ud2f0\ube0c\ub85c \uc0ac\uc6a9\ud55c \ubc94\uc8c4 \ubbf8\uc2a4\ud130\ub9ac \ucd08\uc548\uc785\ub2c8\ub2e4.")
                .publishable(validation.isValid())
                .draft(draft)
                .validationWarnings(mergeWarnings(safeWarnings, validation))
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
        validateAnswerCoherence(draft, findings);
        validateNarrativeFields(draft, findings);
        validateMissions(draft, findings);
        validateSuspects(draft, findings);
        validateEvidences(draft, findings);
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
        draft.setActualHistorySummary(defaultIfBlank(draft.getActualHistorySummary(), "이 지역의 문화적 배경과 장소의 분위기를 바탕으로 사건의 모티브를 구성했습니다."));
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
                mission.setRewardClue(blank(mission.getRewardClue()) ? null : mission.getRewardClue().trim());
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "\uc870\uc0ac \ubbf8\uc158 8\uac1c \uc644\ub8cc \uc2dc \uc790\ub3d9 \uacf5\uac1c"));
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private void applyDeterministicCrimeMysteryGuardrail(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : warnings;
        Map<String, String> approved = approvedAnswers(request);
        String culprit = approved.get("CULPRIT");
        String weapon = approved.get("WEAPON");
        String motive = approved.get("MOTIVE");
        String method = approved.get("METHOD");
        draft.setFictionSynopsis(defaultIfBlank(draft.getFictionSynopsis(),
                "중요한 행사 전날 밤 피해자가 집무실에서 숨진 채 발견되었다. 외부 침입 흔적은 없고, 사건 시간대에 의미 있는 접근 권한을 가진 인물은 세 명뿐이었다. 조사 단서는 피해자의 평소 복용 습관, 접근 기록, 독성 분석, 알리바이의 빈틈을 따라 하나의 진실로 수렴한다."));
        draft.setMissionDescription("8개 조사 단서로 범인, 흉기, 동기, 방법을 종합해 최종 진실을 판단합니다.");
        if (!finalTruthExplainsAnswers(draft, culprit, weapon, motive, method)) {
            draft.setFinalTruthSummary(String.format(
                    "범인: %s. 흉기: %s. 동기: %s. 방법: %s. 피해자의 평소 복용 습관, 약통 접근 흔적, 독성 성분 분석, 인사 문서와 알리바이 검증 결과가 서로 맞물리며 이 네 가지 정답으로 수렴합니다.",
                    culprit, weapon, motive, method));
            safeWarnings.add("GUARDRAIL_REPAIRED_FINAL_TRUTH_SUMMARY");
        }
        if (!hasUsableSuspects(draft, culprit)) {
            draft.setSuspects(canonicalSuspects(draft.getSuspects(), culprit));
            safeWarnings.add("GUARDRAIL_REPAIRED_SUSPECTS");
        }
        if (redactSuspectNamesFromInvestigationClues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES");
        }
        if (redactFinalAnswerValuesFromInvestigationClues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_ANSWER_VALUES");
        }
        List<String> investigationClueIssues = investigationClueIssues(draft);
        if (!investigationClueIssues.isEmpty()) {
            logInvestigationClueRepairSnapshot(draft, investigationClueIssues);
            applyCanonicalInvestigationClues(draft);
            safeWarnings.add("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES");
            investigationClueIssues.forEach(issue -> safeWarnings.add("GUARDRAIL_INVESTIGATION_CLUES_" + issue));
        }
        if (!hasUsableEvidences(draft)) {
            draft.setEvidences(canonicalEvidences(draft.getMissions()));
            safeWarnings.add("GUARDRAIL_REPAIRED_EVIDENCES");
        }
    }

    private boolean finalTruthExplainsAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit, String weapon, String motive, String method) {
        String truth = compact(draft.getFinalTruthSummary());
        return Stream.of(culprit, weapon, motive, method)
                .allMatch(value -> !blank(value) && truth.contains(compact(value)));
    }

    private boolean hasUsableSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        boolean hasCulprit = false;
        for (AiEpisodeDraftResponse.SuspectDraft suspect : suspects) {
            if (suspect == null || blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                return false;
            }
            String suspectText = compact(String.join(" ",
                    trim(suspect.getDisplayName()),
                    trim(suspect.getAlias()),
                    trim(suspect.getRelationToVictim())));
            hasCulprit = hasCulprit || suspectText.contains(compact(culprit));
        }
        return hasCulprit;
    }

    private boolean hasUsableInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return investigationClueIssues(draft).isEmpty();
    }

    private List<String> investigationClueIssues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        Set<String> issues = new LinkedHashSet<>();
        List<AiEpisodeDraftResponse.MissionDraft> investigation = safeList(draft.getMissions()).stream()
                .filter(mission -> mission != null)
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (investigation.size() != 8) issues.add("COUNT");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
            if (!blank(clue) && isGenericFallbackClue(clue)) issues.add("GENERIC");
            if (!blank(clue) && !clues.add(compact(clue))) issues.add("DUPLICATE");
            if (!blank(clue) && answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
                issues.add("DIRECT_ANSWER_LEAK");
            }
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) {
                issues.add("TARGET_SLOT");
            } else {
                if (!isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
                counts.computeIfPresent(target, (key, count) -> count + 1);
            }
            List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                    .map(this::normalize)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
            if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        }
        if (!SLOT_IDS.stream().allMatch(slot -> counts.getOrDefault(slot, 0) == 2)) issues.add("SLOT_BALANCE");
        return new ArrayList<>(issues);
    }

    private boolean redactFinalAnswerValuesFromInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answers = answerValues(draft);
        if (answers.stream().allMatch(this::blank)) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            String clue = mission.getRewardClue();
            if (blank(clue)) continue;
            String redacted = clue;
            for (int i = 0; i < SLOT_IDS.size() && i < answers.size(); i++) {
                String answer = answers.get(i);
                if (blank(answer)) continue;
                redacted = redactAnswerValue(redacted, answer, indirectAnswerReference(SLOT_IDS.get(i)));
            }
            if (!redacted.equals(clue)) {
                mission.setRewardClue(redacted);
                changed = true;
            }
        }
        return changed;
    }

    private boolean redactSuspectNamesFromInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.isEmpty()) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            String clue = mission.getRewardClue();
            if (blank(clue)) continue;
            String redacted = clue;
            for (int i = 0; i < suspects.size(); i++) {
                AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
                if (suspect == null || blank(suspect.getDisplayName())) continue;
                redacted = redactAnswerValue(redacted, suspect.getDisplayName(), suspectReference(i));
            }
            if (!redacted.equals(clue)) {
                mission.setRewardClue(redacted);
                changed = true;
            }
        }
        return changed;
    }

    private String redactAnswerValue(String text, String answer, String replacement) {
        String result = text.replace("용의자 " + answer, replacement);
        result = result.replace(answer + "은", replacement + "는");
        result = result.replace(answer + "는", replacement + "는");
        result = result.replace(answer + "이", replacement + "가");
        result = result.replace(answer + "가", replacement + "가");
        result = result.replace(answer + "을", replacement + "을");
        result = result.replace(answer + "를", replacement + "를");
        result = result.replace(answer + "에게", replacement + "에게");
        result = result.replace(answer + "의", replacement + "의");
        result = result.replace(answer + "와", replacement + "와");
        result = result.replace(answer + "과", replacement + "과");
        return result.replace(answer, replacement);
    }

    private String suspectReference(int index) {
        return switch (index) {
            case 0 -> "첫 번째 용의자";
            case 1 -> "두 번째 용의자";
            case 2 -> "세 번째 용의자";
            default -> "해당 용의자";
        };
    }

    private String indirectAnswerReference(String slot) {
        return switch (normalize(slot)) {
            case "CULPRIT" -> "해당 용의자";
            case "WEAPON" -> "해당 물증";
            case "MOTIVE" -> "해당 동기";
            case "METHOD" -> "해당 실행 방식";
            default -> "해당 단서";
        };
    }


    private void logInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        if (!log.isWarnEnabled()) return;
        String debugPath = writeInvestigationClueRepairSnapshot(draft, issues);
        log.warn("ai_draft_investigation_clues_repaired issues={} title={} debugPath={}",
                issues,
                abbreviateForLog(draft.getEpisodeTitle(), 80),
                debugPath);
    }

    private String writeInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        List<AiEpisodeDraftResponse.MissionDraft> investigation = safeList(draft.getMissions()).stream()
                .filter(mission -> mission != null)
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        List<String> answers = answerValues(draft);
        List<Map<String, Object>> missions = investigation.stream()
                .map(mission -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", mission.getOrder());
                    item.put("targetKeywordType", normalize(mission.getTargetKeywordType()));
                    item.put("supportsKeywordSlots", safeList(mission.getSupportsKeywordSlots()).stream().map(this::normalize).toList());
                    item.put("issues", investigationClueMissionIssues(mission, answers));
                    item.put("rewardClue", trim(mission.getRewardClue()));
                    return item;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", trim(draft.getEpisodeTitle()));
        payload.put("issues", issues);
        payload.put("missions", missions);
        Path path = Path.of("build", "ai-draft-debug", "latest-pre-guardrail-investigation-clues.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
            return path.toString();
        } catch (Exception e) {
            log.warn("ai_draft_investigation_clue_debug_write_failed path={} error={}", path, e.toString());
            return "WRITE_FAILED";
        }
    }

    private List<String> investigationClueMissionIssues(AiEpisodeDraftResponse.MissionDraft mission, List<String> answers) {
        Set<String> issues = new LinkedHashSet<>();
        if (mission == null) return List.of("NULL_MISSION");
        String clue = trim(mission.getRewardClue());
        if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
        if (!blank(clue) && isGenericFallbackClue(clue)) issues.add("GENERIC");
        if (!blank(clue) && safeList(answers).stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
            issues.add("DIRECT_ANSWER_LEAK");
        }
        String target = normalize(mission.getTargetKeywordType());
        if (!SLOT_IDS.contains(target)) {
            issues.add("TARGET_SLOT");
        } else if (!isSlotRelevantClue(target, clue)) {
            issues.add("SLOT_RELEVANCE");
        }
        List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
        if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        return new ArrayList<>(issues);
    }

    private boolean hasUsableEvidences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        if (evidences.size() != 8) return false;
        Set<Integer> orders = evidences.stream()
                .map(AiEpisodeDraftResponse.EvidenceDraft::getSourceMissionOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (int order = 2; order <= 9; order++) {
            if (!orders.contains(order)) return false;
        }
        return evidences.stream().allMatch(evidence -> evidence != null && !blank(evidence.getTitle()) && !blank(evidence.getTextSummary()));
    }

    private List<AiEpisodeDraftResponse.SuspectDraft> canonicalSuspects(List<AiEpisodeDraftResponse.SuspectDraft> source, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> result = new ArrayList<>();
        AiEpisodeDraftResponse.SuspectDraft culpritDraft = safeList(source).stream()
                .filter(suspect -> suspect != null && containsAny(compact(String.join(" ", trim(suspect.getDisplayName()), trim(suspect.getAlias()))), compact(culprit)))
                .findFirst()
                .orElseGet(() -> AiEpisodeDraftResponse.SuspectDraft.builder().displayName(culprit).build());
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(defaultIfBlank(culpritDraft.getDisplayName(), culprit))
                .alias(culpritDraft.getAlias())
                .relationToVictim(defaultIfBlank(culpritDraft.getRelationToVictim(), "피해자의 비서"))
                .alibiSummary(defaultIfBlank(culpritDraft.getAlibiSummary(), "사건 추정 시각 동안 행사 자료를 정리하고 있었다고 주장하며, 일부 노트북 사용 기록이 남아 있다."))
                .suspiciousPoint(defaultIfBlank(culpritDraft.getSuspiciousPoint(), "최근 해고 통보를 받았고 피해자의 일정과 약 복용 습관을 가장 잘 알고 있었다."))
                .shortDescription(culpritDraft.getShortDescription())
                .portraitImageUrl(culpritDraft.getPortraitImageUrl())
                .imagePrompt(culpritDraft.getImagePrompt())
                .build());
        addNonCulpritSuspect(result, source, "박도현", "사업 파트너",
                "사건 시간 동안 투자자와 화상회의를 했다고 주장하며, 회의 접속 기록이 대부분 남아 있다.",
                "피해자와 투자 분쟁이 있었고 피해자 사망 시 경제적 이익을 얻을 수 있었다.");
        addNonCulpritSuspect(result, source, "이재훈", "피해자의 조카",
                "사건 시간 동안 전시 준비를 하고 있었다고 주장하며, 일부 CCTV에 모습이 남아 있다.",
                "유산 상속 예정자였고 최근 피해자와 크게 다퉜으나 CCTV 공백 시간이 사망 추정 시각과 어긋난다.");
        return result.stream().limit(3).toList();
    }

    private void addNonCulpritSuspect(List<AiEpisodeDraftResponse.SuspectDraft> result, List<AiEpisodeDraftResponse.SuspectDraft> source, String fallbackName, String relation, String alibi, String suspicion) {
        AiEpisodeDraftResponse.SuspectDraft existing = safeList(source).stream()
                .filter(suspect -> suspect != null && result.stream().noneMatch(saved -> compact(saved.getDisplayName()).equals(compact(suspect.getDisplayName()))))
                .findFirst()
                .orElse(null);
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(defaultIfBlank(existing == null ? "" : existing.getDisplayName(), fallbackName))
                .alias(existing == null ? null : existing.getAlias())
                .relationToVictim(defaultIfBlank(existing == null ? "" : existing.getRelationToVictim(), relation))
                .alibiSummary(defaultIfBlank(existing == null ? "" : existing.getAlibiSummary(), alibi))
                .suspiciousPoint(defaultIfBlank(existing == null ? "" : existing.getSuspiciousPoint(), suspicion))
                .shortDescription(existing == null ? null : existing.getShortDescription())
                .portraitImageUrl(existing == null ? null : existing.getPortraitImageUrl())
                .imagePrompt(existing == null ? null : existing.getImagePrompt())
                .build());
    }

    private void applyCanonicalInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> clues = List.of(
                "약통에서는 피해자의 지문 외에 업무 공간을 자유롭게 출입할 수 있는 한 사람의 추가 지문만 검출되었다.",
                "사업 파트너의 화상회의 기록은 사망 추정 시간 내내 유지되었고, 조카의 CCTV 공백은 사망 추정 시각보다 1시간 이전이었다.",
                "약통 안의 캡슐 일부에서 일반 수면제 성분과 다른 독성 물질이 검출되었다.",
                "독성 물질은 음식이나 음료가 아니라 캡슐 내부에서만 발견되어 약물 조작 가능성을 높였다.",
                "사망 일주일 전 작성된 인사 문서에는 비서 계약 종료와 인수인계 일정이 기록되어 있었다.",
                "피해자와 가까운 직원이 계약 종료 통보 직후 강한 불만과 복수심을 드러냈다는 통화 기록이 남아 있었다.",
                "피해자는 매일 밤 같은 약통에서 수면제를 복용했고, 약통은 집무실 서랍 안에 보관되어 있었다.",
                "약통 보관 서랍의 열림 기록과 캡슐 교체 추정 시간이 비서의 업무 동선과 겹친다."
        );
        List<String> targets = List.of("CULPRIT", "CULPRIT", "WEAPON", "WEAPON", "MOTIVE", "MOTIVE", "METHOD", "METHOD");
        List<AiEpisodeDraftResponse.MissionDraft> missions = safeList(draft.getMissions());
        int clueIndex = 0;
        for (AiEpisodeDraftResponse.MissionDraft mission : missions) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            if (clueIndex >= clues.size()) break;
            String target = targets.get(clueIndex);
            mission.setTargetKeywordType(target);
            mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
            mission.setRewardClueSlotId("ANSWER_CLUE");
            mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
            mission.setSupportsKeywordSlots(List.of(target));
            mission.setRewardClue(clues.get(clueIndex));
            clueIndex++;
        }
    }

    private List<AiEpisodeDraftResponse.EvidenceDraft> canonicalEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return safeList(missions).stream()
                .filter(mission -> mission != null && mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                        .title(mission.getOrder() + "번 조사 증거")
                        .type("STORY_CLUE")
                        .textSummary(mission.getRewardClue())
                        .sourceMissionOrder(mission.getOrder())
                        .build())
                .toList();
    }

    private void validateFinalAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers == null || blank(answers.getCulprit()) || blank(answers.getWeapon()) || blank(answers.getMotive()) || blank(answers.getMethod())) {
            addFinding(findings, "ERROR", "FOUR_FINAL_ANSWERS_REQUIRED", "\ucd5c\uc885 \uc815\ub2f5\uc740 \ubc94\uc778, \ud749\uae30, \ub3d9\uae30, \ubc29\ubc95 4\uac1c\uc785\ub2c8\ub2e4.", null, "finalAnswers");
        }
        if (draft.getFinalAnswerKeywords() == null || draft.getFinalAnswerKeywords().size() != 4 || draft.getFinalAnswerKeywords().stream().anyMatch(this::blank)) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORDS_REQUIRED", "\ucd5c\uc885 \uc815\ub2f5 \ud0a4\uc6cc\ub4dc\ub294 \uc815\ud655\ud788 4\uac1c\uc5ec\uc57c \ud569\ub2c8\ub2e4.", null, "finalAnswerKeywords");
        }
        List<AiEpisodeDraftResponse.AnswerKeywordItem> items = safeList(draft.getFinalAnswerKeywordItems());
        Set<String> itemSlots = items.stream()
                .map(item -> normalize(defaultIfBlank(item.getSlotId(), item.getType())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (items.size() != 4 || !itemSlots.equals(new LinkedHashSet<>(SLOT_IDS)) || items.stream().anyMatch(item -> blank(answerKeywordItemValue(item)))) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED", "finalAnswerKeywordItems must contain exactly CULPRIT, WEAPON, MOTIVE, METHOD with non-empty values.", null, "finalAnswerKeywordItems");
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
        List<AiEpisodeDraftResponse.MissionDraft> finals = missions.stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (finals.size() != 1) addFinding(findings, "ERROR", "ONE_FINAL_REQUIRED", "FINAL mission must be exactly one.", null, "missions");
        for (AiEpisodeDraftResponse.MissionDraft finalMission : finals) {
            if (!"ALL_INVESTIGATION_MISSIONS_CLEARED".equals(normalize(finalMission.getUnlockCondition()))) {
                addFinding(findings, "ERROR", "FINAL_UNLOCK_CONDITION_REQUIRED", "Final place must unlock automatically after all 8 investigation missions are cleared.", finalMission.getOrder(), "unlockCondition");
            }
            if (!blank(finalMission.getTargetKeywordType()) || !safeList(finalMission.getSupportsKeywordSlots()).isEmpty()) {
                addFinding(findings, "ERROR", "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE", "Final place must not be used as a final-answer clue.", finalMission.getOrder(), "targetKeywordType");
            }
        }
        if (investigation.size() != 8) addFinding(findings, "ERROR", "EIGHT_INVESTIGATION_CLUES_REQUIRED", "Investigation missions must be exactly eight.", null, "missions");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(clue) || clue.length() < 10) addFinding(findings, "ERROR", "DEDUCTIVE_CLUE_REQUIRED", "Investigation rewardClue is required.", mission.getOrder(), "rewardClue");
            if (isGenericFallbackClue(clue)) addFinding(findings, "ERROR", "GENERIC_DEDUCTIVE_CLUE", "Investigation rewardClue must be a concrete case fact, not a generic fallback sentence.", mission.getOrder(), "rewardClue");
            if (!blank(clue) && !clues.add(compact(clue))) addFinding(findings, "ERROR", "DUPLICATE_CLUE", "Investigation clues must be unique.", mission.getOrder(), "rewardClue");
            if (answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) addFinding(findings, "ERROR", "DIRECT_ANSWER_LEAK", "rewardClue must not include final answer values.", mission.getOrder(), "rewardClue");
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) addFinding(findings, "ERROR", "TARGET_KEYWORD_TYPE_REQUIRED", "targetKeywordType is required.", mission.getOrder(), "targetKeywordType");
            else counts.computeIfPresent(target, (key, count) -> count + 1);
            List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream().map(this::normalize).filter(value -> !value.isBlank()).toList();
            if (supports.size() != 1 || !supports.contains(target)) {
                addFinding(findings, "ERROR", "EXACTLY_ONE_SUPPORTED_SLOT_REQUIRED", "Each investigation clue must support exactly one matching final answer slot.", mission.getOrder(), "supportsKeywordSlots");
            }
            if (containsAny(compact(clue), "atmosphere", "mood", "scenery", "backgroundonly")) {
                addFinding(findings, "ERROR", "DEDUCTIVE_CLUE_NOT_ATMOSPHERE", "Investigation rewardClue must be deductive evidence, not atmosphere or background description.", mission.getOrder(), "rewardClue");
            }
            if (!isSlotRelevantClue(target, clue)) {
                addFinding(findings, "ERROR", "CLUE_SLOT_MISMATCH", "Investigation rewardClue must match its targetKeywordType.", mission.getOrder(), "rewardClue");
            }
            if (containsForbiddenPlaceHint(mission)) addFinding(findings, "ERROR", "DESTINATION_HINT_FORBIDDEN", "Place hint structure is forbidden.", mission.getOrder(), "markerType");
        }
        for (String slot : SLOT_IDS) {
            int count = counts.getOrDefault(slot, 0);
            if (count != 2) addFinding(findings, "ERROR", "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED", slot + " must be supported by exactly 2 investigation missions. current=" + count, null, "missions.targetKeywordType");
        }
    }

    private void validateSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) addFinding(findings, "ERROR", "EXACTLY_THREE_SUSPECTS_REQUIRED", "Exactly three suspects are required.", null, "suspects");
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                addFinding(findings, "ERROR", "SUSPECT_DETAILS_REQUIRED", "Suspect details are required.", null, "suspects[" + i + "]");
            }
        }
    }

    private void validateAnswerCoherence(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers == null) return;
        String culprit = trim(answers.getCulprit());
        if (!blank(culprit)) {
            boolean culpritExists = safeList(draft.getSuspects()).stream().anyMatch(suspect ->
                    suspect != null && containsAny(compact(String.join(" ",
                            trim(suspect.getDisplayName()),
                            trim(suspect.getAlias()),
                            trim(suspect.getRelationToVictim()))), compact(culprit)));
            if (!culpritExists) {
                addFinding(findings, "ERROR", "CULPRIT_MUST_BE_SUSPECT", "The culprit answer must be one of the three suspect cards.", null, "suspects");
            }
        }
        String truth = compact(draft.getFinalTruthSummary());
        for (String value : List.of(answers.getCulprit(), answers.getWeapon(), answers.getMotive(), answers.getMethod())) {
            if (!blank(value) && !truth.contains(compact(value))) {
                addFinding(findings, "ERROR", "FINAL_TRUTH_MUST_EXPLAIN_ANSWERS", "finalTruthSummary must explicitly explain all four final answer values.", null, "finalTruthSummary");
                break;
            }
        }
    }

    private void validateNarrativeFields(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        if (blank(draft.getEpisodeTitle())) addFinding(findings, "ERROR", "EPISODE_TITLE_REQUIRED", "episodeTitle is required.", null, "episodeTitle");
        if (blank(draft.getFictionSynopsis())) addFinding(findings, "ERROR", "FICTION_SYNOPSIS_REQUIRED", "fictionSynopsis must describe the fictional case overview.", null, "fictionSynopsis");
        if (blank(draft.getFinalTruthSummary())) addFinding(findings, "ERROR", "FINAL_TRUTH_SUMMARY_REQUIRED", "finalTruthSummary must explain the culprit, weapon, motive, and method.", null, "finalTruthSummary");
    }

    private void validateEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        if (evidences.size() != 8) {
            addFinding(findings, "ERROR", "EIGHT_EVIDENCE_CARDS_REQUIRED", "Exactly 8 evidence cards are required, one for each investigation mission.", null, "evidences");
        }
        Set<Integer> orders = new LinkedHashSet<>();
        for (int i = 0; i < evidences.size(); i++) {
            AiEpisodeDraftResponse.EvidenceDraft evidence = evidences.get(i);
            if (evidence == null) {
                addFinding(findings, "ERROR", "EVIDENCE_DETAILS_REQUIRED", "Evidence details are required.", null, "evidences[" + i + "]");
                continue;
            }
            if (blank(evidence.getTitle()) || blank(evidence.getTextSummary())) {
                addFinding(findings, "ERROR", "EVIDENCE_DETAILS_REQUIRED", "Evidence title and textSummary are required.", evidence.getSourceMissionOrder(), "evidences[" + i + "]");
            }
            Integer order = evidence.getSourceMissionOrder();
            if (order == null || order < 2 || order > 9 || !orders.add(order)) {
                addFinding(findings, "ERROR", "EVIDENCE_SOURCE_MISSION_REQUIRED", "Evidence must map uniquely to investigation mission orders 2-9.", order, "evidences[" + i + "].sourceMissionOrder");
            }
        }
    }

    private void validatePlaceSafety(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String text = String.join(" ", trim(draft.getFictionSynopsis()), trim(draft.getMissionDescription()), safeList(draft.getMissions()).stream().map(AiEpisodeDraftResponse.MissionDraft::getStoryText).map(this::trim).collect(Collectors.joining(" ")));
        if (containsAny(text, "\uc2e4\uc81c\ub85c \ubc1c\uc0dd\ud55c \uc0b4\uc778", "\uc2e4\uc81c \ubc94\uc8c4 \ud604\uc7a5", "\uc774 \uc7a5\uc18c\uc5d0\uc11c \uc0b4\ud574", "\uc774\uacf3\uc5d0\uc11c \uc2e4\uc81c\ub85c")) {
            addFinding(findings, "ERROR", "REAL_PLACE_CRIME_IMPLICATION", "\uc2e4\uc81c \uc7a5\uc18c\uc5d0\uc11c \uc2e4\uc81c \ubc94\uc8c4\uac00 \ubc1c\uc0dd\ud55c \uac83\ucc98\ub7fc \uc4f0\uba74 \uc548 \ub429\ub2c8\ub2e4.", null, "fictionSynopsis");
        }
        if (containsImmersionBreakingText(playerFacingText(draft))) {
            addFinding(findings, "ERROR", "IMMERSION_BREAKING_TEXT", "Player-facing text must not mention implementation, review, or fiction disclaimers.", null, "draft");
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

    private String buildPlanPrompt(AiEpisodeDraftRequest request) {
        return """
                Return JSON only.
                Genre is fixed to CRIME_MYSTERY.
                Final answers are exactly four slots: CULPRIT, WEAPON, MOTIVE, METHOD.
                Do not create place hints, destination clues, or final-place guessing.
                Use the selected places and research context only as background motifs.
                Never imply that a real crime happened at a real place.
                Do not use immersion-breaking wording such as "real place", "fictional suspect", "needs admin review", or "RAG context".

                Required JSON shape:
                {
                  "finalAnswerKeywords": [
                    {"slotId":"CULPRIT","type":"CULPRIT","label":"범인","keyword":"..."},
                    {"slotId":"WEAPON","type":"WEAPON","label":"흉기","keyword":"..."},
                    {"slotId":"MOTIVE","type":"MOTIVE","label":"동기","keyword":"..."},
                    {"slotId":"METHOD","type":"METHOD","label":"방법","keyword":"..."}
                  ]
                }

                Context:
                """ + buildPlaceContext(request);
    }

    private String buildDraftPrompt(AiEpisodeDraftRequest request) {
        return """
                Return JSON only, matching AiEpisodeDraftResponse.EpisodeDraft.
                Write in Korean.
                Genre is fixed to 범죄 미스터리.
                Final answers are exactly CULPRIT, WEAPON, MOTIVE, METHOD.
                Use the approved final answer values exactly. Do not invent a different culprit, weapon, motive, or method.
                The culprit answer value must be one of the 3 suspect displayName values.
                All suspects, finalTruthSummary, rewardClue values, and evidences must converge to the approved final answers.
                finalTruthSummary must include the approved CULPRIT, WEAPON, MOTIVE, and METHOD values verbatim.
                The final place is not a deduction answer. It unlocks automatically after all 8 investigation missions are cleared.
                Use TourAPI, external research notes, reference URLs, admin memo, and place descriptions only as background motifs.
                Do not state or imply that a real crime happened at any real place.
                Do not write phrases that break immersion, including "실제 장소", "가상의 용의자", "관리자 검수", "RAG", "TourAPI", "외부 검색".
                Do not create place hints, destination clues, DESTINATION_HINT, DESTINATION_CLUE, FINAL_DESTINATION, or PLACE_HINT.
                Create 10 missions: order 1 START, orders 2-9 ANSWER_HINT, order 10 FINAL.
                The 8 investigation rewardClue values must be distinct deductive clues, not atmosphere.
                Never use generic clue text such as "조사 단서는 ... 판단에 필요한 근거를 제공합니다."
                Each investigation rewardClue must support exactly one targetKeywordType.
                Use exactly two investigation clues per slot: CULPRIT, WEAPON, MOTIVE, METHOD.
                rewardClue must not directly include final answer values, including the culprit name.
                Investigation rewardClue should avoid suspect display names in general. Use indirect labels such as "a suspect", "the person with access", "the owner of the fingerprint", or "the person shown in CCTV".
                Keep suspect display names in the suspects array and finalTruthSummary only, not in the 8 investigation rewardClue values.
                Before returning JSON, self-check every investigation rewardClue against the approved final answer values.
                If a rewardClue contains the exact approved CULPRIT, WEAPON, MOTIVE, or METHOD value, rewrite it as indirect evidence.
                Example: do not write the culprit name; write "the person with unrestricted office access" or "the owner of the extra fingerprint" instead.
                Example: do not write the weapon answer value; write toxicology, container, residue, or material facts that let the player infer it.
                Example: do not write the motive answer value; write documents, debt records, conflict messages, or benefit facts that imply it.
                Example: do not write the method answer value; write timing, access path, object state, or tampering sequence facts that imply it.
                Suspects must include exactly 3 people, each with alibiSummary and suspiciousPoint.
                Evidences must include exactly 8 cards mapped to sourceMissionOrder 2 through 9.
                actualHistorySummary must explain the historical/cultural motif behind the final place without saying the case is real.

                Do not omit or null these fields: episodeTitle, fictionSynopsis, finalTruthSummary, missions, suspects, evidences.

                Case blueprint:
                - Create a fictional victim, incident setup, cause or mechanism, limited suspect pool, and timeline.
                - The case overview must resemble a locked-room crime mystery: victim found dead or incapacitated, clear cause/mechanism, no obvious forced entry, and exactly 3 suspects present in the plausible incident window.
                - The culprit must be exactly one of the 3 suspects.
                - Each suspect needs a concrete alibiSummary and a concrete suspiciousPoint.
                - Suspect alibiSummary must include the claimed activity during the incident window and what record/witness partially supports it.
                - Suspect suspiciousPoint must include motive pressure, recent conflict, access, missing time, or benefit from the victim's death.
                - Build the truth so CULPRIT, WEAPON, MOTIVE, and METHOD are uniquely deducible only after combining all 8 clues.
                - The 8 clues must be evidence facts such as records, fingerprints, access logs, object traces, CCTV gaps, medical/toxicology facts, contracts, schedules, or witness observations.
                - Do not use simple mood, scenery, tourism facts, route directions, address numbers, sign text, or place-name extraction as deduction clues.
                - Every clue must add different information. Avoid repeating the same fact with different wording.
                - finalAnswerKeywordItems and finalAnswers must contain only the approved 4 answer slots.

                Target story pattern:
                - fictionSynopsis should read like: a prominent professional or collector is found dead before an important event; the cause is poisoning or another clear mechanism; the room or timeline limits outside intrusion; only 3 suspects had meaningful access.
                - Suspect cards should read like: name and relation, alibi during the estimated incident time, supporting record, suspicious point, and why the person remains plausible.
                - Final truth should read like: culprit, weapon, method, and motive are stated with the approved answer values, then explain why the other two suspects are weakened by the clues.
                - The 8 rewardClue values should function like evidence chain clues: daily medication habit, extra fingerprint/access trace, toxicology source, false or supported alibi, dismissal or conflict document, CCTV timing, object tampering, and final matching trace.

                Slot-specific clue rules:
                - CULPRIT clues identify access, fingerprints, CCTV, alibi gaps, or exclusive opportunity. Do not write the culprit name.
                - WEAPON clues identify the object, material, toxicology, capsule, medication, or physical trace. Do not mention motive.
                - MOTIVE clues identify dismissal, revenge, conflict, loss, debt, inheritance, threat, or benefit. Do not describe the physical method.
                - METHOD clues identify swap, replacement, tampering, injection, concealment, timing, access path, or execution sequence. Do not describe motive.
                - CULPRIT rewardClue should naturally include at least one of these Korean evidence anchors: 지문, 출입, 접근, 알리바이, 동선, 기록, CCTV, 목격, 권한, 일치, 용의자.
                - WEAPON rewardClue should naturally include at least one of these Korean evidence anchors: 흉기, 도구, 독극물, 캡슐, 약, 약물, 병, 물질, 성분, 검출, 흔적.
                - MOTIVE rewardClue should naturally include at least one of these Korean evidence anchors: 동기, 복수, 해고, 계약, 분쟁, 유산, 손실, 채무, 협박, 이익, 불만, 갈등.
                - METHOD rewardClue should naturally include at least one of these Korean evidence anchors: 방법, 바꿔치기, 교체, 조작, 삽입, 주입, 은폐, 복용, 캡슐, 접근, 시간, 경로.

                Required mission contract:
                - START: markerType START, clueRole START, publicMarkerType START, finalPlace false.
                - Investigation: markerType ANSWER_HINT, clueRole ANSWER_HINT, publicMarkerType ANSWER_HINT, finalPlace false.
                - Orders 2-9 must each include a concrete rewardClue sentence. Never leave rewardClue null, empty, or templated.
                - Invalid rewardClue examples: "2번 조사 단서는 범인 판단에 필요한 근거를 제공합니다.", "이 단서는 정답 추리에 필요합니다.", "현장에서 단서가 발견되었다."
                - Valid rewardClue examples must name a concrete record, trace, timing, object state, document, witness observation, or analysis result.
                - Required investigation mission slots:
                  order 2 targetKeywordType CULPRIT with a concrete rewardClue.
                  order 3 targetKeywordType CULPRIT with a different concrete rewardClue.
                  order 4 targetKeywordType WEAPON with a concrete rewardClue.
                  order 5 targetKeywordType WEAPON with a different concrete rewardClue.
                  order 6 targetKeywordType MOTIVE with a concrete rewardClue.
                  order 7 targetKeywordType MOTIVE with a different concrete rewardClue.
                  order 8 targetKeywordType METHOD with a concrete rewardClue.
                  order 9 targetKeywordType METHOD with a different concrete rewardClue.
                - FINAL: markerType FINAL, clueRole FINAL_PLACE, publicMarkerType ANSWER_HINT, finalPlace true, unlockCondition ALL_INVESTIGATION_MISSIONS_CLEARED.

                Required JSON shape:
                {
                  "episodeTitle": "...",
                  "subtitle": "...",
                  "genre": "범죄 미스터리",
                  "fictionSynopsis": "사건 개요. 피해자, 사망/피해 정황, 제한된 용의자 범위, 시간대를 포함.",
                  "missionDescription": "8개 조사 단서로 범인, 흉기, 동기, 방법을 추론한다.",
                  "finalTruthSummary": "범인: <approved CULPRIT>. 흉기: <approved WEAPON>. 동기: <approved MOTIVE>. 방법: <approved METHOD>. 네 정답이 왜 유일한지 설명.",
                  "actualHistorySummary": "최종 장소의 역사/문화 모티브 설명.",
                  "missions": [
                    {"order":1,"markerType":"START","publicMarkerType":"START","clueRole":"START","finalPlace":false},
                    {"order":2,"markerType":"ANSWER_HINT","publicMarkerType":"ANSWER_HINT","clueRole":"ANSWER_HINT","finalPlace":false,"targetKeywordType":"CULPRIT","supportsKeywordSlots":["CULPRIT"],"rewardClue":"범인 이름 없이 접근 권한이나 알리바이 공백을 보여주는 증거"},
                    {"order":10,"markerType":"FINAL","publicMarkerType":"ANSWER_HINT","clueRole":"FINAL_PLACE","finalPlace":true,"unlockCondition":"ALL_INVESTIGATION_MISSIONS_CLEARED"}
                  ],
                  "suspects": [
                    {"displayName":"...","relationToVictim":"...","alibiSummary":"...","suspiciousPoint":"..."}
                  ],
                  "evidences": [
                    {"title":"...","type":"STORY_CLUE","textSummary":"rewardClue와 연결되는 구체적 증거","sourceMissionOrder":2}
                  ]
                }

                Context:
                """ + buildPlaceContext(request);
    }

    private String buildPlaceContext(AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        AiEpisodeDraftRequest.PlaceInput finalPlace = finalPlaceInput(request);
        if (finalPlace != null) {
            builder.append("finalPlaceMotif:\n");
            appendPlace(builder, finalPlace, places.indexOf(finalPlace) + 1);
        }
        builder.append("routePlaces:\n");
        for (int i = 0; i < places.size(); i++) {
            appendPlace(builder, places.get(i), i + 1);
        }
        return builder.toString();
    }

    private void appendApprovedAnswers(StringBuilder builder, AiEpisodeDraftRequest request) {
        Map<String, String> approved = approvedAnswers(request);
        builder.append("approvedFinalAnswers:\n");
        for (String slot : SLOT_IDS) {
            builder.append("- ").append(slot).append(": ").append(safePromptText(approved.get(slot))).append('\n');
        }
    }

    private AiEpisodeDraftRequest.PlaceInput finalPlaceInput(AiEpisodeDraftRequest request) {
        if (request == null) return null;
        if (request.getFinalSpot() != null) return request.getFinalSpot();
        List<AiEpisodeDraftRequest.PlaceInput> places = request.getPlaces() == null ? List.of() : request.getPlaces();
        return places.stream()
                .filter(place -> "FINAL".equals(normalize(place.getRole())))
                .findFirst()
                .orElse(places.isEmpty() ? null : places.get(places.size() - 1));
    }

    private void appendPlace(StringBuilder builder, AiEpisodeDraftRequest.PlaceInput place, int order) {
        if (place == null) return;
        builder.append("- order: ").append(order).append('\n');
        builder.append("  role: ").append(safePromptText(place.getRole())).append('\n');
        builder.append("  name: ").append(safePromptText(place.getName())).append('\n');
        builder.append("  address: ").append(safePromptText(place.getAddress())).append('\n');
        builder.append("  description: ").append(safePromptText(place.getDescription())).append('\n');
        builder.append("  adminMemo: ").append(safePromptText(place.getAdminMemo())).append('\n');
        appendPromptList(builder, "visibleElements", place.getVisibleElements());
        appendPromptList(builder, "keywords", place.getKeywords());
        appendPromptList(builder, "usablePuzzleSources", place.getUsablePuzzleSources());
        appendPromptList(builder, "verificationNotes", place.getVerificationNotes());
        appendPromptList(builder, "externalResearchNotes", place.getExternalResearchNotes());
        appendPromptList(builder, "referenceUrls", place.getReferenceUrls());
        builder.append("  researchSourceSummary: ").append(safePromptText(place.getResearchSourceSummary())).append('\n');
    }

    private void appendPromptList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safePromptText)
                .limit(8)
                .collect(Collectors.joining(" | "));
        if (!joined.isBlank()) {
            builder.append("  ").append(label).append(": ").append(joined).append('\n');
        }
    }

    private String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
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

    private JsonNode draftJsonNode(JsonNode root) {
        if (root != null && root.has("draft") && root.path("draft").isObject()) {
            return root.path("draft");
        }
        if (root != null && root.has("data") && root.path("data").has("draft") && root.path("data").path("draft").isObject()) {
            return root.path("data").path("draft");
        }
        return root;
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

    private String answerKeywordItemValue(AiEpisodeDraftResponse.AnswerKeywordItem item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        return "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getValue());
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

    private String playerFacingText(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) return "";
        List<String> values = new ArrayList<>();
        values.add(trim(draft.getEpisodeTitle()));
        values.add(trim(draft.getSubtitle()));
        values.add(trim(draft.getFictionSynopsis()));
        values.add(trim(draft.getMissionDescription()));
        values.add(trim(draft.getFinalQuestion()));
        values.add(trim(draft.getFinalTruthSummary()));
        values.add(trim(draft.getActualHistorySummary()));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            values.add(trim(mission.getStoryText()));
            values.add(trim(mission.getQuestionText()));
            values.add(trim(mission.getRewardClue()));
            values.addAll(safeList(mission.getHints()));
        }
        for (AiEpisodeDraftResponse.SuspectDraft suspect : safeList(draft.getSuspects())) {
            values.add(trim(suspect.getDisplayName()));
            values.add(trim(suspect.getShortDescription()));
            values.add(trim(suspect.getRelationToVictim()));
            values.add(trim(suspect.getAlibiSummary()));
            values.add(trim(suspect.getSuspiciousPoint()));
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            values.add(trim(evidence.getTitle()));
            values.add(trim(evidence.getTextSummary()));
        }
        return values.stream().filter(value -> !blank(value)).collect(Collectors.joining(" "));
    }

    private boolean containsImmersionBreakingText(String text) {
        if (blank(text)) return false;
        String lowered = text.toLowerCase(Locale.ROOT);
        return containsAny(lowered,
                "rag",
                "tourapi",
                "external search",
                "admin review",
                "needs admin review",
                "real place",
                "fictional suspect",
                "관리자 검수",
                "관리자 확인",
                "검수가 필요",
                "외부 검색",
                "실제 장소",
                "가상의 용의자",
                "허구의 용의자");
    }

    private boolean isGenericFallbackClue(String clue) {
        String compacted = compact(clue);
        return containsAny(compacted,
                "조사단서는범인판단에필요한근거를제공합니다",
                "조사단서는흉기판단에필요한근거를제공합니다",
                "조사단서는동기판단에필요한근거를제공합니다",
                "조사단서는방법판단에필요한근거를제공합니다",
                "판단에필요한근거를제공합니다");
    }

    private boolean isSlotRelevantClue(String target, String clue) {
        if (blank(target) || blank(clue)) return true;
        String compacted = compact(clue);
        if ("CULPRIT".equals(target) && containsAny(compacted, "인물", "인력", "한명", "행동", "걸음걸이", "모습", "증언", "보조", "직원", "연구원", "서재", "누락", "확인")) {
            return true;
        }
        if ("MOTIVE".equals(target) && containsAny(compacted, "해고", "계약", "분쟁", "유산", "손실", "채무", "협박", "이익", "이득", "재정", "금전", "수익", "상속", "불만", "갈등", "불화", "통보", "문자", "메모", "복수")) {
            return true;
        }
        if ("METHOD".equals(target) && containsAny(compacted, "교환", "약병", "약함", "약통", "약물", "복용", "캡슐", "외형", "목격", "증언", "장면", "이용", "바꾼", "바꾸", "교체", "조작")) {
            return true;
        }
        return switch (target) {
            case "CULPRIT" -> containsAny(compacted, "지문", "출입", "접근", "알리바이", "동선", "기록", "cctv", "목격", "권한", "일치", "용의자");
            case "WEAPON" -> containsAny(compacted, "흉기", "독", "독극물", "캡슐", "약", "수면제", "잔", "물질", "성분", "검출", "도구");
            case "MOTIVE" -> containsAny(compacted, "동기", "복수", "해고", "계약", "분쟁", "유산", "손실", "채무", "원한", "협박", "이익", "불만", "갈등", "언쟁", "징계", "배제");
            case "METHOD" -> containsAny(compacted, "방법", "바꿔치기", "교체", "조작", "혼입", "투입", "주입", "희석", "위조", "제조", "복용", "캡슐", "접근", "시간", "경로", "열쇠", "봉인");
            default -> true;
        };
    }

    private String defaultTargetKeywordType(int investigationIndex) {
        return SLOT_IDS.get(Math.min(3, Math.max(0, investigationIndex / 2)));
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
    private String abbreviateForLog(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}
