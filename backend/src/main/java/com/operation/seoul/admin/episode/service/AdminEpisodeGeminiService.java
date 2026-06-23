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
import java.util.List;

@Service
@Slf4j
public class AdminEpisodeGeminiService {
    private static final String GENRE_NAME = "범죄 미스터리";
    private final ObjectMapper objectMapper;
    private final GeminiContentClient geminiContentClient;
    private final DraftInvestigationClueDebugSnapshotWriter investigationClueDebugSnapshotWriter;

    public AdminEpisodeGeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.geminiContentClient = new GeminiContentClient(objectMapper);
        this.investigationClueDebugSnapshotWriter = new DraftInvestigationClueDebugSnapshotWriter(objectMapper);
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
        return DraftResponseAssembler.build(draft, request, new ArrayList<>(), this::validateDraft, this::logInvestigationClueRepairSnapshot);
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


    private void logInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        if (!log.isWarnEnabled()) return;
        String debugPath = investigationClueDebugSnapshotWriter.write(draft, issues);
        log.warn("ai_draft_investigation_clues_repaired issues={} title={} debugPath={}",
                issues,
                abbreviateForLog(draft.getEpisodeTitle(), 80),
                debugPath);
    }














    private AiEpisodeDraftValidationResponse validationResponse(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        return DraftValidationResultFactory.build(findings);
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

    private List<AiEpisodePlanResponse.AnswerKeyword> answerPlanKeywords(AiEpisodeDraftRequest request) {
        ensureApiKey();
        return new GeminiAnswerPlanGenerator(objectMapper, this::callGemini).generate(request);
    }

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder, String fieldPath) {
        findings.add(DraftValidationResultFactory.finding(severity, code, message, missionOrder, fieldPath));
    }


    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String abbreviateForLog(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

