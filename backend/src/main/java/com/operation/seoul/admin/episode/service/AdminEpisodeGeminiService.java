package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
@Slf4j
public class AdminEpisodeGeminiService {
    private static final String GENRE_NAME = "범죄 미스터리";
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;
    private static final Map<String, String> SLOT_LABELS = FinalAnswerSlots.LABELS;
    private final ObjectMapper objectMapper;
    private final GeminiContentClient geminiContentClient;

    public AdminEpisodeGeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.geminiContentClient = new GeminiContentClient(objectMapper);
    }

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    public AiEpisodePlanResponse createAnswerPlan(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        TourApiPlanContext planContext = TourApiPlanInputExtractor.extract(request);
        List<AiEpisodePlanResponse.AnswerKeyword> keywords = answerPlanKeywords(request);
        return AnswerPlanResponseFactory.build(planContext, keywords);
    }

    public AiEpisodeDraftResponse createGeminiDraft(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        FinalAnswerContractSupport.normalizeFinalAnswerKeywordItems(request);
        FinalAnswerContractSupport.repairWeakFinalAnswerKeywords(request);
        FinalAnswerContractSupport.validateFinalAnswerContract(request);
        ensureApiKey();
        AiEpisodeDraftResponse.EpisodeDraft draft = new GeminiDraftGenerator(objectMapper, this::callGemini).generate(request);
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
        findings.addAll(new AiEpisodeDraftValidator(this::validateDraftRules)
                .validationFindings(draft, request.getSourceInput()));
        return validationResponse(findings);
    }

    private void validateDraftRules(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<AiEpisodeDraftValidationResponse.Finding> findings) {
        new EpisodeDraftRuleValidator().validate(draft, request, findings);
    }


    private void applyApprovedFinalAnswerContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        FinalAnswerContractSupport.normalizeFinalAnswerKeywordItems(request);
        FinalAnswerContractSupport.repairWeakFinalAnswerKeywords(request);
        Map<String, String> approved = FinalAnswerContractSupport.approvedAnswers(request);
        FinalAnswerContractSupport.NameRole culprit = FinalAnswerContractSupport.splitNameRole(approved.get("CULPRIT"));
        approved.put("CULPRIT", culprit.name());
        List<String> values = SLOT_IDS.stream().map(approved::get).toList();
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerKeywords(values);
        draft.setFinalAnswerKeywordItems(SLOT_IDS.stream()
                .map(slot -> AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                        .slotId(slot).type(slot).displayType(SLOT_LABELS.get(slot)).label(SLOT_LABELS.get(slot))
                        .keyword(approved.get(slot)).value(approved.get(slot))
                        .personName("CULPRIT".equals(slot) ? culprit.name() : "")
                        .personRole("CULPRIT".equals(slot) ? culprit.role() : "")
                        .role("CULPRIT".equals(slot) ? culprit.role() : "")
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
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), investigationStoryText(i + 1, target)));
            } else if (start) {
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "사건 파일의 첫 장을 열고 피해자, 용의자, 사건 시간대를 확인합니다."));
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "조사 미션 8개 완료 시 자동 공개"));
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "모든 조사 단서를 대조한 뒤 범인, 흉기, 동기, 방법을 최종 입력합니다."));
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private String investigationStoryText(int order, String target) {
        return switch (order) {
            case 2 -> "사건 발생 시간대의 출입 기록과 내부 동선을 대조해 실제 접근 가능했던 인물을 좁힙니다.";
            case 3 -> "알리바이 기록, CCTV 공백, 휴대폰 위치 기록을 비교해 용의자들의 진술이 맞는지 확인합니다.";
            case 4 -> "피해자 주변에서 나온 물질 흔적을 분석해 사망 원인과 연결되는 물증을 확인합니다.";
            case 5 -> "현장에 남은 물건의 상태와 보관 위치를 대조해 어떤 도구가 범행에 쓰였는지 추적합니다.";
            case 6 -> "피해자와 용의자들 사이의 계약, 장부, 문서 기록을 확인해 사건의 이해관계를 찾습니다.";
            case 7 -> "사건 직전의 메시지, 통화, 목격 진술을 비교해 누가 가장 강한 압박을 받았는지 판단합니다.";
            case 8 -> "피해자가 반복하던 행동과 사건 당일 준비물의 변화를 대조해 범행 순서를 복원합니다.";
            case 9 -> "마지막 조작 흔적과 접근 순서를 맞춰 범행 방법이 어떻게 실행됐는지 확정합니다.";
            default -> "수집한 사건 기록을 대조해 " + SLOT_LABELS.getOrDefault(target, "정답") + " 판단에 필요한 단서를 확인합니다.";
        };
    }

    private void applyDeterministicCrimeMysteryGuardrail(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : warnings;
        Map<String, String> approved = FinalAnswerContractSupport.approvedAnswers(request);
        String culprit = approved.get("CULPRIT");
        String weapon = approved.get("WEAPON");
        String motive = approved.get("MOTIVE");
        String method = approved.get("METHOD");
        String routineLabel = methodRoutineLabel(method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        String motiveDocument = motiveDocumentLabel(motive);
        draft.setFictionSynopsis(defaultIfBlank(draft.getFictionSynopsis(),
                "중요한 행사 전날 밤 피해자가 제한된 공간에서 숨진 채 발견되었다. 외부 침입 흔적은 없고, 사건 시간대에 의미 있는 접근 권한을 가진 인물은 세 명뿐이었다. 조사 단서는 피해자의 " + routineLabel + ", 접근 기록, 독성 분석, 알리바이의 빈틈을 따라 하나의 진실로 수렴한다."));
        draft.setMissionDescription("8개 조사 단서로 범인, 흉기, 동기, 방법을 종합해 최종 진실을 판단합니다.");
        if (!finalTruthExplainsAnswers(draft, culprit, weapon, motive, method)) {
            draft.setFinalTruthSummary(String.format(
                    "범인: %s. 흉기: %s. 동기: %s. 방법: %s. 피해자의 %s, %s 접근 흔적, 독성 성분 분석, %s와 알리바이 검증 결과가 서로 맞물리며 이 네 가지 정답으로 수렴합니다.",
                    culprit, weapon, motive, method, routineLabel, containerLabel, motiveDocument));
            safeWarnings.add("GUARDRAIL_REPAIRED_FINAL_TRUTH_SUMMARY");
        }
        if (!DraftSuspectGuardrail.hasUsableSuspects(draft, culprit)) {
            draft.setSuspects(DraftSuspectGuardrail.canonicalSuspects(draft.getSuspects(), culprit));
            safeWarnings.add("GUARDRAIL_REPAIRED_SUSPECTS");
        }
        if (DraftNarrativeGuardrail.shouldRepairSynopsis(draft, request) || !DraftNarrativeGuardrail.synopsisMentionsAllSuspects(draft)) {
            draft.setFictionSynopsis(DraftNarrativeGuardrail.canonicalSynopsis(draft, weapon, motive, method));
            safeWarnings.add("GUARDRAIL_REPAIRED_SYNOPSIS_SUSPECTS");
        }
        if (DraftNarrativeGuardrail.redactRealPlaceNamesFromStoryFields(draft, request)) {
            safeWarnings.add("GUARDRAIL_REDACTED_REAL_PLACE_NAMES");
        }
        if (DraftNarrativeGuardrail.normalizeSuspectVictimReferences(draft)) {
            safeWarnings.add("GUARDRAIL_NORMALIZED_SUSPECT_VICTIM_REFERENCES");
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
            DraftInvestigationClueGuardrail.applyCanonicalInvestigationClues(draft, request);
            safeWarnings.add("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES");
            investigationClueIssues.forEach(issue -> safeWarnings.add("GUARDRAIL_INVESTIGATION_CLUES_" + issue));
        }
        if (!DraftEvidenceGuardrail.hasUsableEvidences(draft) || DraftEvidenceGuardrail.evidencesLeakFinalAnswerValues(draft)) {
            draft.setEvidences(DraftEvidenceGuardrail.canonicalEvidences(draft.getMissions()));
            safeWarnings.add("GUARDRAIL_REPAIRED_EVIDENCES");
        }
    }

    private boolean finalTruthExplainsAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit, String weapon, String motive, String method) {
        String truth = compact(draft.getFinalTruthSummary());
        return Stream.of(culprit, weapon, motive, method)
                .allMatch(value -> !blank(value) && truth.contains(compact(value)));
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
            case "MOTIVE" -> "이해관계가 드러난 인물";
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





















    private String evidenceObjectLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 용기";
        if (containsAny(text, "시약", "실험", "연구")) return "시료 용기";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약물 용기";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 봉투";
        return "현장 물증";
    }

    private String evidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 보관대";
        if (containsAny(text, "시약", "실험", "연구")) return "실험 준비물 보관함";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약통";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 보관함";
        return "증거 보관 지점";
    }

    private String motiveDocumentLabel(String motive) {
        if (containsAny(motive, "연구", "조작", "논문", "실험", "시약")) return "연구 감사 문서";
        if (containsAny(motive, "해고", "계약", "인수인계")) return "인사 문서";
        if (containsAny(motive, "채무", "손실", "횡령", "재정", "금전")) return "회계 문서";
        if (containsAny(motive, "유산", "상속")) return "상속 관련 문서";
        return "내부 문서";
    }

    private String methodRoutineLabel(String method) {
        if (containsAny(method, "음료", "커피", "차", "마시는")) return "매일 마시던 음료";
        if (containsAny(method, "약", "캡슐", "수면제", "복용")) return "매일 복용하던 약";
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        return "반복되던 준비물";
    }



















    private AiEpisodeDraftValidationResponse validationResponse(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        return DraftValidationResultFactory.build(findings);
    }


    private String buildPlanPrompt(AiEpisodeDraftRequest request) {
        return GeminiAnswerPlanPromptBuilder.build(request);
    }

    private String callGemini(String prompt) {
        return geminiContentClient.generateContent(prompt, geminiModel, geminiApiKey);
    }

    private void ensureApiKey() {
        if (blank(geminiApiKey)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING", "Gemini API 키가 설정되어 있지 않습니다.");
    }

    private void validatePlaces(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().size() != 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_COUNT", "AI 초안 생성에는 정확히 10개 장소가 필요합니다.");
    }

    private void normalizeFinalAnswerKeywordItems(AiEpisodeDraftRequest request) {
        FinalAnswerContractSupport.normalizeFinalAnswerKeywordItems(request);
    }


    private void validateFinalAnswerContract(AiEpisodeDraftRequest request) {
        FinalAnswerContractSupport.validateFinalAnswerContract(request);
    }


    private void repairWeakFinalAnswerKeywords(AiEpisodeDraftRequest request) {
        FinalAnswerContractSupport.repairWeakFinalAnswerKeywords(request);
    }






    private Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        return FinalAnswerContractSupport.approvedAnswers(request);
    }




    private String answerKeywordItemValue(AiEpisodeDraftResponse.AnswerKeywordItem item) {
        return FinalAnswerContractSupport.answerKeywordItemValue(item);
    }




    private List<AiEpisodePlanResponse.AnswerKeyword> answerPlanKeywords(AiEpisodeDraftRequest request) {
        ensureApiKey();
        return new GeminiAnswerPlanGenerator(objectMapper, this::callGemini).generate(request);
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

    private boolean weakFinalAnswerKeyword(String slot, String value) {
        return FinalAnswerContractSupport.weakFinalAnswerKeyword(slot, value);
    }

    private NameRole splitNameRole(String value) {
        FinalAnswerContractSupport.NameRole nameRole = FinalAnswerContractSupport.splitNameRole(value);
        return new NameRole(nameRole.name(), nameRole.role());
    }


    private record NameRole(String name, String role) {}

    private String playerFacingText(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return DraftClueQualityRules.playerFacingText(draft);
    }


    private boolean containsImmersionBreakingText(String text) {
        return DraftClueQualityRules.containsImmersionBreakingText(text);
    }


    private boolean isGenericFallbackClue(String clue) {
        return DraftClueQualityRules.isGenericFallbackClue(clue);
    }


    private boolean isSlotRelevantClue(String target, String clue) {
        return DraftClueQualityRules.isSlotRelevantClue(target, clue);
    }


    private boolean contradictsCulpritWithinSuspects(String clue) {
        return DraftClueQualityRules.contradictsCulpritWithinSuspects(clue);
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
        findings.add(DraftValidationResultFactory.finding(severity, code, message, missionOrder, fieldPath));
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

