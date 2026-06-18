package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.ArrayList;
import java.util.List;

final class AiEpisodeDraftValidator {
    private final DraftRuleValidator draftRuleValidator;

    AiEpisodeDraftValidator(DraftRuleValidator draftRuleValidator) {
        this.draftRuleValidator = draftRuleValidator;
    }

    List<AiEpisodeDraftValidationResponse.Finding> validationFindings(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        draftRuleValidator.validate(draft, request, findings);
        return findings;
    }

    boolean hasBlockingValidationFindings(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request) {
        return validationFindings(draft, request).stream()
                .anyMatch(finding -> "ERROR".equalsIgnoreCase(finding.getSeverity()));
    }

    boolean isRepairableDraftFinding(AiEpisodeDraftValidationResponse.Finding finding) {
        if (finding == null || !"ERROR".equalsIgnoreCase(finding.getSeverity())) {
            return false;
        }
        String code = normalize(finding.getCode());
        return code.contains("META_TEXT")
                || code.contains("GENERIC")
                || code.contains("ACTUAL_PLACE_NAME")
                || code.contains("FINAL_KEYWORD_IN")
                || code.contains("FULL_FINAL_ANSWER_AS_REWARD")
                || code.contains("IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING")
                || code.contains("STORY_OBJECTIVE_MISMATCH");
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("ERROR_") ? normalized.substring("ERROR_".length()) : normalized;
    }

    interface DraftRuleValidator {
        void validate(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<AiEpisodeDraftValidationResponse.Finding> findings);
    }
}
