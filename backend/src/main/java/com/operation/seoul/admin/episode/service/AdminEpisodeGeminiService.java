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
    private static final String GENRE_NAME = "범죄 미스터리";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final List<String> SLOT_IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    private static final Map<String, String> SLOT_LABELS = Map.of(
            "CULPRIT", "범인",
            "WEAPON", "흉기",
            "MOTIVE", "동기",
            "METHOD", "방법"
    );
    private static final List<String> DEFAULT_ANSWERS = List.of(
            "강수진",
            "독성 캡슐",
            "비밀 계약 은폐",
            "약병 바꿔치기"
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
                .finalQuestionGuide("조사 미션 8개를 완료한 뒤 범인, 흉기, 동기, 방법을 각각 입력합니다.")
                .rationale("장르는 범죄 미스터리로 고정하고 선택 장소 정보는 배경 모티브로만 사용합니다.")
                .planReviewRequired(false)
                .reviewReason("")
                .fieldVerificationRecommended(true)
                .rejectedGenreReasons(List.of("장소 힌트나 최종 장소 추리 구조는 사용하지 않습니다."))
                .validationWarnings(List.of())
                .nextSteps(List.of("4개 정답 슬롯을 검수하고 AI 초안을 생성하세요."))
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini 응답 JSON을 해석할 수 없습니다.");
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
                .generatorType("GEMINI_CRIME_MYSTERY")
                .message("장소 배경을 바탕으로 구성한 범죄 미스터리 초안입니다.")
                .publishable(validation.isValid())
                .draft(draft)
                .validationWarnings(mergeWarnings(safeWarnings, validation))
                .nextSteps(List.of("8개 조사 단서의 중복과 정답 노출 여부를 검수하세요."))
                .build();
    }

    public AiEpisodeDraftValidationResponse validateDraft(AiEpisodeDraftValidationRequest request) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        AiEpisodeDraftResponse.EpisodeDraft draft = request == null ? null : request.getDraft();
        if (draft == null) {
            addFinding(findings, "ERROR", "DRAFT_REQUIRED", "검증할 초안이 없습니다.", null, "draft");
            return validationResponse(findings);
        }
        if (!GENRE_NAME.equals(draft.getGenre()) || !GENRE_NAME.equals(draft.getSelectedGenre())) {
            addFinding(findings, "ERROR", "GENRE_MUST_BE_CRIME_MYSTERY", "장르는 범죄 미스터리로 고정되어야 합니다.", null, "genre");
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
        draft.setFinalQuestion(defaultIfBlank(draft.getFinalQuestion(), "범인, 흉기, 동기, 방법을 각각 입력하세요."));
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
            mission.setQuestionText(defaultIfBlank(mission.getQuestionText(), "현장 기록과 사건 자료를 비교해 답하세요."));
            mission.setAnswer(defaultIfBlank(mission.getAnswer(), "단서" + (i + 1)));
            mission.setAnswerFormat(defaultIfBlank(mission.getAnswerFormat(), "TEXT"));
            mission.setHints(ensureThreeHints(mission.getHints()));
            if (!start && !finalPlace) {
                String target = defaultTargetKeywordType(i - 1);
                mission.setTargetKeywordType(target);
                mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
                mission.setRewardClueSlotId("ANSWER_CLUE");
                mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
                mission.setSupportsKeywordSlots(List.of(target));
                mission.setRewardClue(blank(mission.getRewardClue()) ? null : mission.getRewardClue().trim());
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "조사 미션 8개 완료 시 자동 공개"));
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
        if (!synopsisMentionsAllSuspects(draft)) {
            draft.setFictionSynopsis(canonicalSynopsis(draft, weapon));
            safeWarnings.add("GUARDRAIL_REPAIRED_SYNOPSIS_SUSPECTS");
        }
        if (redactSuspectNamesFromInvestigationClues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES");
        }
        if (rewriteGenericSuspectReferences(draft)) {
            safeWarnings.add("GUARDRAIL_REWROTE_GENERIC_SUSPECT_REFERENCES");
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

    private boolean synopsisMentionsAllSuspects(AiEpisodeDraftResponse.EpisodeDraft draft) {
        String synopsis = compact(draft.getFictionSynopsis());
        if (blank(synopsis)) return false;
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        return suspects.stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftResponse.SuspectDraft::getDisplayName)
                .filter(name -> !blank(name))
                .allMatch(name -> synopsis.contains(compact(name)));
    }

    private String canonicalSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, String weapon) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        String first = suspectName(suspects, 0, "강수진");
        String second = suspectName(suspects, 1, "박도현");
        String third = suspectName(suspects, 2, "이재훈");
        String weaponPhrase = blank(weapon) ? "독성 물질" : weapon;
        return "유명 미술품 수집가 한태준이 개인 갤러리 개관 행사 전날 밤 자신의 집무실에서 숨진 채 발견되었다. "
                + "사인은 " + weaponPhrase + "과 연결된 독극물 중독으로 추정되며, 문은 안에서 잠겨 있었고 외부 침입 흔적은 없었다. "
                + "사건 발생 추정 시각에 건물 안에서 의미 있는 접근 권한을 가진 인물은 "
                + first + ", " + second + ", " + third + " 세 명뿐이었다. "
                + "조사는 각자의 알리바이, 약통 접근 가능성, 인사 기록, 동선 공백을 대조해 범인과 범행 방식으로 수렴한다.";
    }

    private String suspectName(List<AiEpisodeDraftResponse.SuspectDraft> suspects, int index, String fallback) {
        if (suspects == null || suspects.size() <= index || suspects.get(index) == null) {
            return fallback;
        }
        return defaultIfBlank(suspects.get(index).getDisplayName(), fallback);
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
                if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
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
                redacted = redactAnswerValue(redacted, suspect.getDisplayName(), suspectReference(mission.getTargetKeywordType()));
            }
            if (!redacted.equals(clue)) {
                mission.setRewardClue(redacted);
                changed = true;
            }
        }
        return changed;
    }

    private boolean rewriteGenericSuspectReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        boolean changed = false;
        Map<Integer, String> targetByOrder = new LinkedHashMap<>();
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            Integer order = mission.getOrder();
            String target = mission.getTargetKeywordType();
            if (order != null && !blank(target)) targetByOrder.put(order, target);
            if (mission.getRewardClue() == null || isNonInvestigationMission(mission)) continue;
            String rewritten = rewriteGenericSuspectReference(mission.getRewardClue(), target);
            if (!rewritten.equals(mission.getRewardClue())) {
                mission.setRewardClue(rewritten);
                changed = true;
            }
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null || evidence.getTextSummary() == null) continue;
            String rewritten = rewriteGenericSuspectReference(evidence.getTextSummary(), targetByOrder.get(evidence.getSourceMissionOrder()));
            if (!rewritten.equals(evidence.getTextSummary())) {
                evidence.setTextSummary(rewritten);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isNonInvestigationMission(AiEpisodeDraftResponse.MissionDraft mission) {
        return "START".equals(normalize(mission.getMarkerType()))
                || Boolean.TRUE.equals(mission.getFinalPlace())
                || "FINAL".equals(normalize(mission.getMarkerType()));
    }

    private String rewriteGenericSuspectReference(String text, String targetKeywordType) {
        if (blank(text)) return text;
        String replacement = genericSuspectReference(targetKeywordType);
        String result = text
                .replace("특정 용의자", replacement)
                .replace("용의자 중 한 명", replacement)
                .replace("용의자 중 하나", replacement)
                .replace("해당 용의자", replacement)
                .replace("해고 통보를 받은 용의자", replacement)
                .replace("용의자의 재직 기록", replacement + "의 재직 기록")
                .replace("용의자의 개인 소지품", replacement + "의 개인 소지품")
                .replace("용의자가 피해자에게", replacement + "가 피해자에게")
                .replace("용의자가 피해자의", replacement + "가 피해자의")
                .replace("용의자의 동선", replacement + "의 동선")
                .replace("문서에 언급된 인물 사이에", replacement + " 사이에")
                .replace("문서에 언급된 인물는", replacement + "은")
                .replace("문서에 언급된 인물이", replacement + "이")
                .replace("문서에 언급된 인물의", replacement + "의");
        return naturalizeRedactedSuspectReferences(normalizePersonReferenceParticles(result));
    }

    private String genericSuspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "이해관계가 드러난 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
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
        return naturalizeRedactedSuspectReferences(normalizePersonReferenceParticles(result.replace(answer, replacement)));
    }

    private String normalizePersonReferenceParticles(String text) {
        if (blank(text)) {
            return text;
        }
        return text
                .replace("관련 인물가", "관련 인물이")
                .replace("기록 속 인물가", "기록 속 인물이")
                .replace("기록 속 인물는", "기록 속 인물은")
                .replace("물증과 연결된 인물가", "물증과 연결된 인물이")
                .replace("물증과 연결된 인물는", "물증과 연결된 인물은")
                .replace("이해관계가 드러난 인물가", "이해관계가 드러난 인물이")
                .replace("이해관계가 드러난 인물는", "이해관계가 드러난 인물은")
                .replace("동선이 겹친 인물가", "동선이 겹친 인물이")
                .replace("동선이 겹친 인물는", "동선이 겹친 인물은")
                .replace("문서에 언급된 인물가", "문서에 언급된 인물이")
                .replace("문서에 언급된 인물는", "문서에 언급된 인물은")
                .replace("관련 인물가", "관련 인물이")
                .replace("관련 인물는", "관련 인물은")
                .replace("사건 기록 속 인물가", "사건 기록 속 인물이")
                .replace("사건 기록 속 인물는", "사건 기록 속 인물은");
    }

    private String naturalizeRedactedSuspectReferences(String text) {
        if (blank(text)) {
            return text;
        }
        return text
                .replace("CCTV에는 기록 속 인물이", "CCTV에는 동일 인물이")
                .replace("집 외부 CCTV에는 기록 속 인물이", "집 외부 CCTV에는 동일 인물이")
                .replace("'기록 속 인물,", "'메모의 대상자,")
                .replace("기록 속 인물이 박 회장에 대해", "메모의 대상자가 박 회장에 대해")
                .replace("기록 속 인물이 피해자에 대해", "메모의 대상자가 피해자에 대해")
                .replace("기록 속 인물은", "메모의 대상자는")
                .replace("기록 속 인물이", "메모의 대상자가")
                .replace("이해관계가 드러난 인물이 박 회장에게 보낸 문자", "문자 발신자가 박 회장에게 보낸 문자")
                .replace("이해관계가 드러난 인물이 피해자에게 보낸 문자", "문자 발신자가 피해자에게 보낸 문자")
                .replace("유언장에 따르면, 이해관계가 드러난 인물은", "유언장에 따르면, 해당 당사자는")
                .replace("이해관계가 드러난 인물은", "해당 당사자는")
                .replace("이해관계가 드러난 인물이", "문서상 이해관계자가")
                .replace("동선이 겹친 인물이 사용한 것으로 추정되는 특정 화학 물질에 대한 온라인 구매 기록", "동선 기록과 연결된 계정에서 특정 화학 물질을 온라인으로 구매한 기록")
                .replace("동선이 겹친 인물이", "동선 기록과 연결된 인물이");
    }

    private String suspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "문서에 언급된 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
    }

    private String indirectAnswerReference(String slot) {
        return switch (normalize(slot)) {
            case "CULPRIT" -> "기록 속 인물";
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
        } else {
            if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
            if (!isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
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
            addFinding(findings, "ERROR", "FOUR_FINAL_ANSWERS_REQUIRED", "최종 정답은 범인, 흉기, 동기, 방법 4개입니다.", null, "finalAnswers");
        }
        if (draft.getFinalAnswerKeywords() == null || draft.getFinalAnswerKeywords().size() != 4 || draft.getFinalAnswerKeywords().stream().anyMatch(this::blank)) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORDS_REQUIRED", "최종 정답 키워드는 정확히 4개여야 합니다.", null, "finalAnswerKeywords");
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
            if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) {
                addFinding(findings, "ERROR", "CULPRIT_CLUE_CONTRADICTS_SUSPECT_SET", "CULPRIT rewardClue must not imply the culprit is outside the three suspects.", mission.getOrder(), "rewardClue");
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
        if (containsAny(text, "실제로 발생한 살인", "실제 범죄 현장", "이 장소에서 살해", "이곳에서 실제로")) {
            addFinding(findings, "ERROR", "REAL_PLACE_CRIME_IMPLICATION", "실제 장소에서 실제 범죄가 발생한 것처럼 쓰면 안 됩니다.", null, "fictionSynopsis");
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
                .summary(errors == 0 ? "필수 범죄 미스터리 검증을 통과했습니다." : "수정이 필요한 항목이 " + errors + "개 남아 있습니다.")
                .findings(findings)
                .requiredFixes(findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).distinct().toList())
                .publishChecklist(List.of(
                        "장소 정보는 배경 모티브로만 사용합니다.",
                        "8개 조사 단서는 하나의 사건 진실로 수렴해야 하며 범인, 흉기, 동기, 방법을 모두 추론할 수 있어야 합니다.",
                        "장소 힌트, 정답 누출, 깨진 한글이 없어야 합니다."))
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
                Internally tag the 8 investigation clues so every final answer slot is supported, but write them as one natural evidence chain rather than visible category pairs.
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
                - Final truth should state culprit, weapon, method, and motive with the approved answer values, then explain why the other two suspects are weakened by the clues.
                - The 8 rewardClue values should read as a progressive evidence chain: daily medication habit, extra fingerprint or access trace, toxicology source, false or supported alibi, dismissal or conflict document, CCTV timing, object tampering, and final matching trace.

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
                - Required internal tagging for answer board grouping:
                  order 2 targetKeywordType CULPRIT, but write the clue as access/opportunity evidence.
                  order 3 targetKeywordType CULPRIT, but write the clue as alibi narrowing or trace matching evidence.
                  order 4 targetKeywordType WEAPON, but write the clue as toxicology/material evidence.
                  order 5 targetKeywordType WEAPON, but write the clue as object/container evidence.
                  order 6 targetKeywordType MOTIVE, but write the clue as conflict/document/benefit evidence.
                  order 7 targetKeywordType MOTIVE, but write the clue as pressure/message/revenge evidence.
                  order 8 targetKeywordType METHOD, but write the clue as routine/timing evidence.
                  order 9 targetKeywordType METHOD, but write the clue as tampering/access-sequence evidence.
                - Do not make the clue text read like "culprit clue 1", "weapon clue 2", or any visible category checklist.
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 요청에 실패했습니다. 상태=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 요청에 실패했습니다. 원인=" + e.getClass().getSimpleName());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_RESPONSE_PARSE_FAILED", "Gemini 응답을 해석할 수 없습니다.");
        }
    }

    private JsonNode parseJson(String value, String code) {
        try {
            return objectMapper.readTree(stripJsonFence(value));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, code, "Gemini 응답 JSON을 해석할 수 없습니다.");
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
        if (blank(geminiApiKey)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING", "Gemini API 키가 설정되어 있지 않습니다.");
    }

    private void validatePlaces(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().size() != 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_COUNT", "AI 초안 생성에는 정확히 10개 장소가 필요합니다.");
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
        if (SLOT_IDS.stream().anyMatch(slot -> blank(values.get(slot)))) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.");
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
        return SLOT_IDS.stream().map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder().slotId(slot).label(SLOT_LABELS.get(slot)).description(SLOT_LABELS.get(slot) + " 정답 슬롯").minClueCount(2).build()).toList();
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
        return containsAny(text, "DESTINATION_HINT", "DESTINATION_CLUE", "FINAL_DESTINATION", "PLACE_HINT", "장소 힌트", "장소 정답", "최종 장소를 찾", "최종 목적지를 찾");
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
        if ("MOTIVE".equals(target) && containsAny(compacted, "해고", "계약", "분쟁", "유산", "손실", "채무", "협박", "이익", "이득", "재정", "금전", "수익", "상속", "불만", "갈등", "불화", "통보", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정", "복수")) {
            return true;
        }
        if ("METHOD".equals(target) && containsAny(compacted, "교환", "약병", "약함", "약통", "약물", "복용", "캡슐", "외형", "목격", "증언", "장면", "이용", "바꾼", "바꾸", "교체", "조작")) {
            return true;
        }
        return switch (target) {
            case "CULPRIT" -> containsAny(compacted, "지문", "출입", "접근", "알리바이", "동선", "기록", "cctv", "목격", "권한", "일치", "용의자");
            case "WEAPON" -> containsAny(compacted, "흉기", "독", "독극물", "캡슐", "약", "수면제", "잔", "물질", "성분", "검출", "도구");
            case "MOTIVE" -> containsAny(compacted, "동기", "복수", "해고", "계약", "분쟁", "유산", "손실", "채무", "원한", "협박", "이익", "불만", "갈등", "언쟁", "징계", "배제", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정");
            case "METHOD" -> containsAny(compacted, "방법", "바꿔치기", "교체", "조작", "혼입", "투입", "주입", "희석", "위조", "제조", "복용", "캡슐", "접근", "시간", "경로", "열쇠", "봉인");
            default -> true;
        };
    }

    private boolean contradictsCulpritWithinSuspects(String clue) {
        String compacted = compact(clue);
        if (blank(compacted)) return false;
        boolean allSuspects = containsAny(compacted, "용의자세명", "용의자3명", "세용의자", "모든용의자", "용의자전원");
        boolean excludesAll = containsAny(compacted, "모두다르", "전부다르", "일치하지않", "불일치", "해당하지않");
        return allSuspects && excludesAll;
    }

    private String defaultTargetKeywordType(int investigationIndex) {
        return SLOT_IDS.get(Math.min(3, Math.max(0, investigationIndex / 2)));
    }

    private List<String> ensureThreeHints(List<String> hints) {
        List<String> result = new ArrayList<>(safeList(hints).stream().filter(value -> !blank(value)).limit(3).toList());
        while (result.size() < 3) result.add("시간, 접근 권한, 물질 흔적을 비교하세요.");
        return result;
    }

    private List<String> mergeWarnings(List<String> warnings, AiEpisodeDraftValidationResponse validation) {
        List<String> result = new ArrayList<>();
        if (warnings != null) result.addAll(warnings);
        if (validation != null && validation.getFindings() != null) validation.getFindings().stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).forEach(result::add);
        return result;
    }

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder, String fieldPath) {
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                .severity(severity)
                .code(code)
                .message(koreanFindingMessage(code, message))
                .missionOrder(missionOrder)
                .fieldPath(fieldPath)
                .autoFixable(false)
                .fixType("REGENERATE")
                .build());
    }

    private String koreanFindingMessage(String code, String fallback) {
        return switch (code) {
            case "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED" -> "finalAnswerKeywordItems에는 범인, 흉기, 동기, 방법 4개 슬롯과 값이 모두 필요합니다.";
            case "TEN_PLACES_REQUIRED" -> "미션 장소는 시작 1개, 조사 8개, 최종 1개로 총 10개여야 합니다.";
            case "ONE_START_REQUIRED" -> "시작 미션은 정확히 1개여야 합니다.";
            case "ONE_FINAL_REQUIRED" -> "최종 장소 미션은 정확히 1개여야 합니다.";
            case "FINAL_UNLOCK_CONDITION_REQUIRED" -> "최종 장소는 조사 미션 8개를 모두 완료한 뒤 자동 공개되어야 합니다.";
            case "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE" -> "최종 장소는 최종 정답을 추리하는 단서로 사용하면 안 됩니다.";
            case "EIGHT_INVESTIGATION_CLUES_REQUIRED" -> "조사 미션은 정확히 8개여야 합니다.";
            case "DEDUCTIVE_CLUE_REQUIRED" -> "조사 미션에는 추리에 직접 기여하는 보상 단서가 필요합니다.";
            case "GENERIC_DEDUCTIVE_CLUE" -> "조사 단서가 너무 일반적입니다. 기록, 지문, 알리바이, 약물 분석처럼 구체적인 사건 정보로 작성해야 합니다.";
            case "DUPLICATE_CLUE" -> "조사 단서가 중복됩니다. 8개 단서는 서로 다른 정보를 제공해야 합니다.";
            case "DIRECT_ANSWER_LEAK" -> "조사 단서가 최종 정답 값을 직접 노출하고 있습니다.";
            case "TARGET_KEYWORD_TYPE_REQUIRED" -> "조사 단서에는 범인, 흉기, 동기, 방법 중 하나의 대상 슬롯이 필요합니다.";
            case "EXACTLY_ONE_SUPPORTED_SLOT_REQUIRED" -> "각 조사 단서는 하나의 정답 슬롯만 지원해야 합니다.";
            case "DEDUCTIVE_CLUE_NOT_ATMOSPHERE" -> "조사 단서는 분위기나 배경 묘사가 아니라 추리 근거여야 합니다.";
            case "CLUE_SLOT_MISMATCH" -> "조사 단서의 내용이 지정된 정답 슬롯과 맞지 않습니다.";
            case "CULPRIT_CLUE_CONTRADICTS_SUSPECT_SET" -> "범인 단서가 세 용의자 밖의 인물을 범인처럼 암시하고 있습니다.";
            case "DESTINATION_HINT_FORBIDDEN" -> "장소 힌트 또는 최종 장소 추리 구조는 사용할 수 없습니다.";
            case "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED" -> "8개 조사 단서는 내부적으로 범인, 흉기, 동기, 방법을 모두 충분히 지원해야 합니다.";
            case "EXACTLY_THREE_SUSPECTS_REQUIRED" -> "용의자 카드는 정확히 3명이어야 합니다.";
            case "SUSPECT_DETAILS_REQUIRED" -> "각 용의자에는 이름, 알리바이, 의심 포인트가 필요합니다.";
            case "CULPRIT_MUST_BE_SUSPECT" -> "범인 정답은 용의자 카드 3명 중 한 명이어야 합니다.";
            case "FINAL_TRUTH_MUST_EXPLAIN_ANSWERS" -> "진실 요약에는 범인, 흉기, 동기, 방법 4개 정답이 모두 설명되어야 합니다.";
            case "EPISODE_TITLE_REQUIRED" -> "에피소드 제목이 필요합니다.";
            case "FICTION_SYNOPSIS_REQUIRED" -> "허구 사건 개요가 필요합니다.";
            case "FINAL_TRUTH_SUMMARY_REQUIRED" -> "최종 진실 요약에는 범인, 흉기, 동기, 방법이 설명되어야 합니다.";
            case "EIGHT_EVIDENCE_CARDS_REQUIRED" -> "조사 미션 8개에 대응하는 증거 카드 8개가 필요합니다.";
            case "EVIDENCE_DETAILS_REQUIRED" -> "증거 카드에는 제목과 요약이 필요합니다.";
            case "EVIDENCE_SOURCE_MISSION_REQUIRED" -> "증거 카드는 조사 미션 2번부터 9번까지 각각 하나씩 연결되어야 합니다.";
            case "IMMERSION_BREAKING_TEXT" -> "사용자에게 노출되는 문구에 구현 방식, 검수 표현, 허구 고지처럼 몰입을 깨는 표현이 포함되어 있습니다.";
            case "INVALID_FINAL_ANSWER_KEYWORDS" -> "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.";
            default -> fallback;
        };
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
