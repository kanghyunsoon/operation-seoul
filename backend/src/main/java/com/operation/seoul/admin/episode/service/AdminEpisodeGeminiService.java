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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class AdminEpisodeGeminiService {
    private static final String GENRE_NAME = "범죄 미스터리";
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
        DraftStructureNormalizer.normalizeDraft(draft, request);
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
        DraftFinalAnswerContractApplier.apply(draft, request);
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
        if (!DraftFinalTruthGuardrail.explainsAnswers(draft, culprit, weapon, motive, method)) {
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
        if (DraftInvestigationCluePolicy.redactSuspectNames(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES");
        }
        if (DraftInvestigationCluePolicy.rewriteGenericSuspectReferences(draft)) {
            safeWarnings.add("GUARDRAIL_REWROTE_GENERIC_SUSPECT_REFERENCES");
        }
        if (DraftInvestigationCluePolicy.redactFinalAnswerValues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_ANSWER_VALUES");
        }
        List<String> investigationClueIssues = DraftInvestigationCluePolicy.investigationClueIssues(draft);
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


    private void logInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        if (!log.isWarnEnabled()) return;
        String debugPath = writeInvestigationClueRepairSnapshot(draft, issues);
        log.warn("ai_draft_investigation_clues_repaired issues={} title={} debugPath={}",
                issues,
                abbreviateForLog(draft.getEpisodeTitle(), 80),
                debugPath);
    }

    private String writeInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        List<AiEpisodeDraftResponse.MissionDraft> investigation = DraftInvestigationCluePolicy.investigationMissions(draft);
        List<String> answers = DraftInvestigationCluePolicy.answerValues(draft);
        List<Map<String, Object>> missions = investigation.stream()
                .map(mission -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", mission.getOrder());
                    item.put("targetKeywordType", normalize(mission.getTargetKeywordType()));
                    item.put("supportsKeywordSlots", safeList(mission.getSupportsKeywordSlots()).stream().map(this::normalize).toList());
                    item.put("issues", DraftInvestigationCluePolicy.missionIssues(mission, answers));
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




    private List<AiEpisodePlanResponse.AnswerKeyword> answerPlanKeywords(AiEpisodeDraftRequest request) {
        ensureApiKey();
        return new GeminiAnswerPlanGenerator(objectMapper, this::callGemini).generate(request);
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

