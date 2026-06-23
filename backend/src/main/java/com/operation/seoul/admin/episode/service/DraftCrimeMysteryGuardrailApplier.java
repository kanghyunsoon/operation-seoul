package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

final class DraftCrimeMysteryGuardrailApplier {
    private DraftCrimeMysteryGuardrailApplier() {
    }

    static void apply(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<String> warnings,
            BiConsumer<AiEpisodeDraftResponse.EpisodeDraft, List<String>> investigationClueRepairLogger) {
        if (draft == null) return;
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : warnings;
        Map<String, String> approved = FinalAnswerContractSupport.approvedAnswers(request);
        String culprit = approved.get("CULPRIT");
        String weapon = approved.get("WEAPON");
        String motive = approved.get("MOTIVE");
        String method = approved.get("METHOD");
        String routineLabel = DraftCrimeMysteryLabeler.methodRoutineLabel(method);
        String containerLabel = DraftCrimeMysteryLabeler.evidenceContainerLabel(weapon, method);
        String motiveDocument = DraftCrimeMysteryLabeler.motiveDocumentLabel(motive);

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
            if (investigationClueRepairLogger != null) {
                investigationClueRepairLogger.accept(draft, investigationClueIssues);
            }
            DraftInvestigationClueGuardrail.applyCanonicalInvestigationClues(draft, request);
            safeWarnings.add("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES");
            investigationClueIssues.forEach(issue -> safeWarnings.add("GUARDRAIL_INVESTIGATION_CLUES_" + issue));
        }
        if (!DraftEvidenceGuardrail.hasUsableEvidences(draft) || DraftEvidenceGuardrail.evidencesLeakFinalAnswerValues(draft)) {
            draft.setEvidences(DraftEvidenceGuardrail.canonicalEvidences(draft.getMissions()));
            safeWarnings.add("GUARDRAIL_REPAIRED_EVIDENCES");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }
}
