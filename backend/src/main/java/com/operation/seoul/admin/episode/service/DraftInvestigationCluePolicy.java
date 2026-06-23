package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DraftInvestigationCluePolicy {
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;

    private DraftInvestigationCluePolicy() {
    }

    static boolean hasUsableInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return investigationClueIssues(draft).isEmpty();
    }

    static List<String> investigationClueIssues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        Set<String> issues = new LinkedHashSet<>();
        List<AiEpisodeDraftResponse.MissionDraft> investigation = investigationMissions(draft);
        if (investigation.size() != 8) issues.add("COUNT");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
            if (!blank(clue) && DraftClueQualityRules.isGenericFallbackClue(clue)) issues.add("GENERIC");
            if (!blank(clue) && !clues.add(compact(clue))) issues.add("DUPLICATE");
            if (!blank(clue) && answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
                issues.add("DIRECT_ANSWER_LEAK");
            }
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) {
                issues.add("TARGET_SLOT");
            } else {
                if ("CULPRIT".equals(target) && DraftClueQualityRules.contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
                if (!DraftClueQualityRules.isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
                counts.computeIfPresent(target, (key, count) -> count + 1);
            }
            List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                    .map(DraftInvestigationCluePolicy::normalize)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
            if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        }
        if (!SLOT_IDS.stream().allMatch(slot -> counts.getOrDefault(slot, 0) == 2)) issues.add("SLOT_BALANCE");
        return new ArrayList<>(issues);
    }

    static List<String> missionIssues(AiEpisodeDraftResponse.MissionDraft mission, List<String> answers) {
        Set<String> issues = new LinkedHashSet<>();
        if (mission == null) return List.of("NULL_MISSION");
        String clue = trim(mission.getRewardClue());
        if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
        if (!blank(clue) && DraftClueQualityRules.isGenericFallbackClue(clue)) issues.add("GENERIC");
        if (!blank(clue) && safeList(answers).stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
            issues.add("DIRECT_ANSWER_LEAK");
        }
        String target = normalize(mission.getTargetKeywordType());
        if (!SLOT_IDS.contains(target)) {
            issues.add("TARGET_SLOT");
        } else {
            if ("CULPRIT".equals(target) && DraftClueQualityRules.contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
            if (!DraftClueQualityRules.isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
        }
        List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                .map(DraftInvestigationCluePolicy::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
        if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        return new ArrayList<>(issues);
    }

    static boolean redactFinalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answers = answerValues(draft);
        if (answers.stream().allMatch(DraftInvestigationCluePolicy::blank)) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || isNonInvestigationMission(mission)) {
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

    static boolean redactSuspectNames(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.isEmpty()) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || isNonInvestigationMission(mission)) {
                continue;
            }
            String clue = mission.getRewardClue();
            if (blank(clue)) continue;
            String redacted = clue;
            for (AiEpisodeDraftResponse.SuspectDraft suspect : suspects) {
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

    static boolean rewriteGenericSuspectReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
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

    static List<AiEpisodeDraftResponse.MissionDraft> investigationMissions(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return safeList(draft.getMissions()).stream()
                .filter(mission -> mission != null)
                .filter(mission -> !isNonInvestigationMission(mission))
                .toList();
    }

    static List<String> answerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) return List.of();
        if (draft.getFinalAnswerKeywordItems() != null && !draft.getFinalAnswerKeywordItems().isEmpty()) {
            return draft.getFinalAnswerKeywordItems().stream()
                    .map(FinalAnswerContractSupport::answerKeywordItemValue)
                    .toList();
        }
        return safeList(draft.getFinalAnswerKeywords());
    }

    private static boolean isNonInvestigationMission(AiEpisodeDraftResponse.MissionDraft mission) {
        return "START".equals(normalize(mission.getMarkerType()))
                || Boolean.TRUE.equals(mission.getFinalPlace())
                || "FINAL".equals(normalize(mission.getMarkerType()));
    }

    private static String rewriteGenericSuspectReference(String text, String targetKeywordType) {
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

    private static String genericSuspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "이해관계가 드러난 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
    }

    private static String redactAnswerValue(String text, String answer, String replacement) {
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

    private static String normalizePersonReferenceParticles(String text) {
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

    private static String naturalizeRedactedSuspectReferences(String text) {
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

    private static String suspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "이해관계가 드러난 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
    }

    private static String indirectAnswerReference(String slot) {
        return switch (normalize(slot)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "해당 물증";
            case "MOTIVE" -> "해당 동기";
            case "METHOD" -> "해당 실행 방식";
            default -> "해당 단서";
        };
    }

    private static boolean containsForbiddenPlaceHint(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = String.join(" ",
                trim(mission.getMarkerType()),
                trim(mission.getPublicMarkerType()),
                trim(mission.getClueRole()),
                trim(mission.getRewardClueSlotId()),
                trim(mission.getRewardClue()),
                trim(mission.getQuestionText()),
                trim(mission.getStoryText()),
                trim(mission.getPuzzleAnswerSource()));
        return containsAny(text,
                "DESTINATION_HINT",
                "DESTINATION_CLUE",
                "FINAL_DESTINATION",
                "PLACE_HINT",
                "장소 힌트",
                "장소 정답",
                "최종 장소를 찾",
                "최종 목적지를 찾");
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}
