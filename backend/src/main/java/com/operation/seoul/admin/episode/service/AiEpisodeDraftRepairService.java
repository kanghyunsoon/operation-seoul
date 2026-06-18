package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.List;

final class AiEpisodeDraftRepairService {
    private static final int MAX_DRAFT_REPAIR_ATTEMPTS = 2;

    private final RepairActions actions;

    AiEpisodeDraftRepairService(RepairActions actions) {
        this.actions = actions;
    }

    void repairDraftUntilClean(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings) {
        actions.forceImagePromptTextConstraints(draft);
        for (int attempt = 1; attempt <= MAX_DRAFT_REPAIR_ATTEMPTS; attempt++) {
            List<AiEpisodeDraftValidationResponse.Finding> findings = actions.validationFindings(draft, request);
            List<AiEpisodeDraftValidationResponse.Finding> repairable = findings.stream()
                    .filter(actions::isRepairableDraftFinding)
                    .toList();
            if (repairable.isEmpty()) {
                break;
            }
            boolean changed = false;
            boolean objectiveMismatch = hasCode(repairable, "STORY_OBJECTIVE_MISMATCH");
            boolean titleRepair = hasAnyCode(repairable,
                    "ACTUAL_PLACE_NAME_IN_TITLE");
            boolean synopsisRepair = hasAnyCode(repairable,
                    "GENERIC_FICTION_SYNOPSIS",
                    "ACTUAL_PLACE_NAME_IN_SYNOPSIS");
            boolean missionDescriptionRepair = hasAnyCode(repairable,
                    "GENERIC_MISSION_DESCRIPTION",
                    "ACTUAL_PLACE_NAME_IN_MISSION_DESCRIPTION");
            boolean evidenceRepair = repairable.stream()
                    .map(AiEpisodeDraftValidationResponse.Finding::getCode)
                    .map(this::normalizeCode)
                    .anyMatch(code -> code.contains("EVIDENCE"));
            if (hasCode(repairable, "IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING")) {
                actions.forceImagePromptTextConstraints(draft);
                changed = true;
            }
            if (objectiveMismatch) {
                changed = actions.rewriteObjectiveAlignedTopFields(draft, request, warnings) || changed;
            } else {
                List<String> topFields = new java.util.ArrayList<>();
                if (titleRepair) {
                    topFields.add("episodeTitle");
                }
                if (synopsisRepair) {
                    topFields.add("fictionSynopsis");
                }
                if (!topFields.isEmpty()) {
                    changed = actions.rewriteTopLevelFields(draft, request, topFields, warnings) || changed;
                }
                if (missionDescriptionRepair) {
                    changed = actions.rewriteMissionDescription(draft, request, warnings) || changed;
                }
            }
            if (hasOtherPlayerFacingRepair(repairable)) {
                actions.rewriteUnsafePlayerFacingFields(draft, request, warnings);
                changed = true;
            }
            if (evidenceRepair && actions.repairEvidenceCardsWithGemini(draft, request, repairable, warnings)) {
                changed = true;
            }
            List<AiEpisodeDraftValidationResponse.Finding> afterFindings = actions.validationFindings(draft, request);
            boolean stillRepairable = afterFindings.stream().anyMatch(actions::isRepairableDraftFinding);
            if (!stillRepairable) {
                break;
            }
        }
        List<AiEpisodeDraftValidationResponse.Finding> remaining = actions.validationFindings(draft, request).stream()
                .filter(actions::isRepairableDraftFinding)
                .toList();
        if (remaining.isEmpty()) {
            actions.removeTransientRepairWarnings(warnings);
        }
    }

    private boolean same(String a, String b) {
        return normalizeCode(a).equals(normalizeCode(b));
    }

    private boolean hasCode(
            List<AiEpisodeDraftValidationResponse.Finding> findings,
            String expectedCode) {
        return findings.stream().anyMatch(finding -> same(finding.getCode(), expectedCode));
    }

    private boolean hasAnyCode(
            List<AiEpisodeDraftValidationResponse.Finding> findings,
            String... expectedCodes) {
        return java.util.Arrays.stream(expectedCodes)
                .anyMatch(expected -> hasCode(findings, expected));
    }

    private boolean hasOtherPlayerFacingRepair(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        return findings.stream()
                .map(AiEpisodeDraftValidationResponse.Finding::getCode)
                .map(this::normalizeCode)
                .anyMatch(code -> !code.contains("EVIDENCE") && !List.of(
                        "STORY_OBJECTIVE_MISMATCH",
                        "ACTUAL_PLACE_NAME_IN_TITLE",
                        "GENERIC_FICTION_SYNOPSIS",
                        "ACTUAL_PLACE_NAME_IN_SYNOPSIS",
                        "GENERIC_MISSION_DESCRIPTION",
                        "ACTUAL_PLACE_NAME_IN_MISSION_DESCRIPTION",
                        "ACTUAL_PLACE_NAME_IN_EVIDENCE_CARD",
                        "IMAGE_PROMPT_TEXT_CONSTRAINT_MISSING").contains(code));
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("ERROR_") ? normalized.substring("ERROR_".length()) : normalized;
    }

    interface RepairActions {
        void forceImagePromptTextConstraints(AiEpisodeDraftResponse.EpisodeDraft draft);

        List<AiEpisodeDraftValidationResponse.Finding> validationFindings(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request);

        boolean isRepairableDraftFinding(AiEpisodeDraftValidationResponse.Finding finding);

        boolean rewriteObjectiveAlignedTopFields(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<String> warnings);

        boolean rewriteMissionDescription(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<String> warnings);

        boolean rewriteTopLevelFields(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<String> fields,
                List<String> warnings);

        void rewriteUnsafePlayerFacingFields(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<String> warnings);

        boolean repairEvidenceCardsWithGemini(
                AiEpisodeDraftResponse.EpisodeDraft draft,
                AiEpisodeDraftRequest request,
                List<AiEpisodeDraftValidationResponse.Finding> repairableFindings,
                List<String> warnings);

        void removeTransientRepairWarnings(List<String> warnings);
    }
}
