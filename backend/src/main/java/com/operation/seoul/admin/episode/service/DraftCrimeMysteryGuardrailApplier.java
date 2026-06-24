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

        draft.setMissionDescription("8개 조사 단서로 범인, 흉기, 동기, 사인을 종합해 최종 진실을 판단합니다.");

        if (!DraftFinalTruthGuardrail.explainsAnswers(draft, culprit, weapon, motive, method)) {
            safeWarnings.add("최종 진실 요약이 범인, 흉기, 동기, 사인을 모두 설명하지 않습니다.");
        }
        if (!DraftSuspectGuardrail.hasUsableSuspects(draft, culprit)) {
            safeWarnings.add("용의자 카드가 부족하거나 범인이 용의자 3명 안에 포함되지 않았습니다.");
        }
        if (DraftNarrativeGuardrail.shouldRepairSynopsis(draft, request) || !DraftNarrativeGuardrail.synopsisMentionsAllSuspects(draft)) {
            safeWarnings.add("사건 줄거리가 크라임씬식 사건 개요로 충분하지 않습니다.");
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
        if (DraftPlayerTextSanitizer.sanitize(draft)) {
            safeWarnings.add("GUARDRAIL_SANITIZED_PLAYER_TEXT");
        }

        List<String> investigationClueIssues = DraftInvestigationCluePolicy.investigationClueIssues(draft);
        if (!investigationClueIssues.isEmpty()) {
            if (investigationClueRepairLogger != null) {
                investigationClueRepairLogger.accept(draft, investigationClueIssues);
            }
            safeWarnings.add("조사 미션 단서가 구체적인 추리 증거로 생성되지 않았습니다.");
            investigationClueIssues.forEach(issue -> safeWarnings.add("조사 단서 검증 실패: " + issue));
        }
        if (!DraftEvidenceGuardrail.hasUsableEvidences(draft) || DraftEvidenceGuardrail.evidencesLeakFinalAnswerValues(draft)) {
            safeWarnings.add("증거 카드가 부족하거나 최종 정답을 직접 노출합니다.");
        }
    }
}
