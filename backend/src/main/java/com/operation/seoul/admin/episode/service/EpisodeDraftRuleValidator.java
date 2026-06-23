package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class EpisodeDraftRuleValidator implements AiEpisodeDraftValidator.DraftRuleValidator {
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;

    @Override
    public void validate(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest request,
            List<AiEpisodeDraftValidationResponse.Finding> findings) {
        validateFinalAnswers(draft, findings);
        validateAnswerCoherence(draft, findings);
        validateNarrativeFields(draft, findings);
        validateMissions(draft, findings);
        validateSuspects(draft, findings);
        validateEvidences(draft, findings);
        validatePlaceSafety(draft, findings);
        findings.addAll(AiDraftTextQualityValidator.findings(draft));
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
        if (items.size() != 4 || !itemSlots.equals(new LinkedHashSet<>(SLOT_IDS)) || items.stream().anyMatch(item -> blank(FinalAnswerContractSupport.answerKeywordItemValue(item)))) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED", "finalAnswerKeywordItems must contain exactly CULPRIT, WEAPON, MOTIVE, METHOD with non-empty values.", null, "finalAnswerKeywordItems");
        }
        for (AiEpisodeDraftResponse.AnswerKeywordItem item : items) {
            String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
            String value = FinalAnswerContractSupport.answerKeywordItemValue(item);
            if (SLOT_IDS.contains(slot) && weakFinalAnswerKeyword(slot, value)) {
                addFinding(findings, "ERROR", "CONCRETE_FINAL_KEYWORD_REQUIRED", "최종 정답 키워드는 구체적인 인물, 물건, 동기, 범행 과정이어야 합니다.", null, "finalAnswerKeywordItems");
            }
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
            if (blank(mission.getStoryText())) {
                addFinding(findings, "ERROR", "MISSION_STORY_TEXT_REQUIRED", "Investigation mission storyText is required.", mission.getOrder(), "storyText");
            }
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
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                addFinding(findings, "ERROR", "SUSPECT_DETAILS_REQUIRED", "Suspect details are required.", null, "suspects[" + i + "]");
            }
            if (!blank(suspect.getDisplayName()) && !names.add(compact(suspect.getDisplayName()))) {
                addFinding(findings, "ERROR", "SUSPECT_NAMES_MUST_BE_UNIQUE", "Suspect names must be unique.", null, "suspects[" + i + "].displayName");
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
        validateSynopsisDomain(draft, findings);
    }

    private void validateSynopsisDomain(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String synopsis = compact(draft.getFictionSynopsis());
        String keywords = compact(safeList(draft.getFinalAnswerKeywordItems()).stream()
                .flatMap(item -> Stream.of(item.getValue(), item.getKeyword(), item.getPersonName()))
                .filter(value -> !blank(value))
                .collect(Collectors.joining(" ")));
        if (containsAny(keywords, "항만", "화물", "밀수", "장부", "서류", "봉투")) {
            if (!containsAny(synopsis, "항만", "화물", "자료", "서류", "장부", "물류")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "미술품", "갤러리", "전시", "작품")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
        if (containsAny(keywords, "미술", "전시", "갤러리", "작품", "위작", "붓펜", "잉크", "서명")) {
            if (!containsAny(synopsis, "미술", "갤러리", "전시", "작품", "감정", "서명")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "항만", "화물", "밀수")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
        if (containsAny(keywords, "연구", "실험", "시약", "논문", "특허")) {
            if (!containsAny(synopsis, "연구", "실험", "회의실", "연구동", "특허")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "갤러리", "미술품", "항만")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
    }

    private void validateEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        List<String> answerValues = answerValues(draft).stream()
                .filter(value -> !blank(value))
                .map(this::compact)
                .toList();
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
            String text = compact(String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary())));
            if (answerValues.stream().anyMatch(value -> !blank(value) && text.contains(value))) {
                addFinding(findings, "ERROR", "EVIDENCE_ANSWER_LEAK", "Evidence cards must not directly reveal final answer values.", order, "evidences[" + i + "]");
            }
        }
    }

    private void validatePlaceSafety(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String text = String.join(" ", trim(draft.getFictionSynopsis()), trim(draft.getMissionDescription()), safeList(draft.getMissions()).stream().map(AiEpisodeDraftResponse.MissionDraft::getStoryText).map(this::trim).collect(Collectors.joining(" ")));
        if (containsAny(text, "실제로 발생한 살인", "실제 범죄 현장", "이 장소에서 살해", "이곳에서 실제로")) {
            addFinding(findings, "ERROR", "REAL_PLACE_CRIME_IMPLICATION", "실제 장소에서 실제 범죄가 발생한 것처럼 쓰면 안 됩니다.", null, "fictionSynopsis");
        }
        if (playerFacingTextContainsImmersionBreakingText(draft)) {
            addFinding(findings, "ERROR", "IMMERSION_BREAKING_TEXT", "Player-facing text must not mention implementation, review, or fiction disclaimers.", null, "draft");
        }
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

    private boolean playerFacingTextContainsImmersionBreakingText(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return DraftClueQualityRules.containsImmersionBreakingText(DraftClueQualityRules.playerFacingText(draft));
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

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder, String fieldPath) {
        findings.add(DraftValidationResultFactory.finding(severity, code, message, missionOrder, fieldPath));
    }

    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String defaultIfBlank(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}