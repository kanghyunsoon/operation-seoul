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
        DraftCrimeMysteryGuardrailApplier.apply(draft, request, warnings, this::logInvestigationClueRepairSnapshot);
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






    private List<AiEpisodePlanResponse.AnswerKeyword> answerPlanKeywords(AiEpisodeDraftRequest request) {
        ensureApiKey();
        return new GeminiAnswerPlanGenerator(objectMapper, this::callGemini).generate(request);
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
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String abbreviateForLog(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

