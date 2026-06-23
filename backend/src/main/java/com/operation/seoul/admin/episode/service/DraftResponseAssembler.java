package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class DraftResponseAssembler {
    private DraftResponseAssembler() {
    }

    static AiEpisodeDraftResponse build(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings,
            Function<AiEpisodeDraftValidationRequest, AiEpisodeDraftValidationResponse> validator,
            BiConsumer<AiEpisodeDraftResponse.EpisodeDraft, List<String>> investigationClueRepairLogger) {
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        DraftFinalAnswerContractApplier.apply(draft, request);
        DraftStructureNormalizer.normalizeDraft(draft, request);
        DraftCrimeMysteryGuardrailApplier.apply(draft, request, safeWarnings, investigationClueRepairLogger);

        AiEpisodeDraftValidationRequest validationRequest = new AiEpisodeDraftValidationRequest();
        validationRequest.setDraft(draft);
        validationRequest.setSourceInput(request);
        AiEpisodeDraftValidationResponse validation = validator.apply(validationRequest);

        return AiEpisodeDraftResponse.builder()
                .generatorType("GEMINI_CRIME_MYSTERY")
                .message("장소 배경을 바탕으로 구성한 범죄 미스터리 초안입니다.")
                .publishable(validation.isValid())
                .draft(draft)
                .validationWarnings(mergeWarnings(safeWarnings, validation))
                .nextSteps(List.of("8개 조사 단서의 중복과 정답 노출 여부를 검수하세요."))
                .build();
    }

    private static List<String> mergeWarnings(List<String> warnings, AiEpisodeDraftValidationResponse validation) {
        List<String> result = new ArrayList<>();
        if (warnings != null) result.addAll(warnings);
        if (validation != null && validation.getFindings() != null) {
            validation.getFindings().stream()
                    .filter(finding -> "ERROR".equals(finding.getSeverity()))
                    .map(AiEpisodeDraftValidationResponse.Finding::getMessage)
                    .forEach(result::add);
        }
        return result;
    }
}
