package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.List;

final class DraftPlayerTextSanitizer {
    private DraftPlayerTextSanitizer() {
    }

    static boolean sanitize(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) return false;
        boolean changed = false;
        changed |= setIfChanged(draft::getEpisodeTitle, draft::setEpisodeTitle);
        changed |= setIfChanged(draft::getSubtitle, draft::setSubtitle);
        changed |= setIfChanged(draft::getFictionSynopsis, draft::setFictionSynopsis);
        changed |= setIfChanged(draft::getMissionDescription, draft::setMissionDescription);
        changed |= setIfChanged(draft::getFinalTruthSummary, draft::setFinalTruthSummary);
        changed |= setIfChanged(draft::getActualHistorySummary, draft::setActualHistorySummary);
        changed |= setIfChanged(draft::getFinalQuestion, draft::setFinalQuestion);
        changed |= sanitizeList(draft.getFinalAnswerAliases(), draft::setFinalAnswerAliases);
        changed |= sanitizeList(draft.getDeductionSecretFacts(), draft::setDeductionSecretFacts);
        changed |= sanitizeList(draft.getDeductionForbiddenReveals(), draft::setDeductionForbiddenReveals);

        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            changed |= setIfChanged(mission::getStoryText, mission::setStoryText);
            changed |= setIfChanged(mission::getQuestionText, mission::setQuestionText);
            changed |= setIfChanged(mission::getRewardClue, mission::setRewardClue);
            changed |= setIfChanged(mission::getRewardClueLabel, mission::setRewardClueLabel);
            changed |= setIfChanged(mission::getGroundRule, mission::setGroundRule);
            changed |= sanitizeList(mission.getHints(), mission::setHints);
        }
        for (AiEpisodeDraftResponse.SuspectDraft suspect : safeList(draft.getSuspects())) {
            if (suspect == null) continue;
            changed |= setIfChanged(suspect::getAlias, suspect::setAlias);
            changed |= setIfChanged(suspect::getShortDescription, suspect::setShortDescription);
            changed |= setIfChanged(suspect::getRelationToVictim, suspect::setRelationToVictim);
            changed |= setIfChanged(suspect::getSuspiciousPoint, suspect::setSuspiciousPoint);
            changed |= setIfChanged(suspect::getAlibiSummary, suspect::setAlibiSummary);
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            changed |= setIfChanged(evidence::getTitle, evidence::setTitle);
            changed |= setIfChanged(evidence::getTextSummary, evidence::setTextSummary);
        }
        return changed;
    }

    static String sanitizeText(String value) {
        if (value == null || value.isBlank()) return value;
        return value
                .replace("물증가", "증거가")
                .replace("메모의 대상자가", "기록에 나온 인물이")
                .replace("메모의 대상자는", "기록에 나온 인물은")
                .replace("메모의 대상자", "기록에 나온 인물")
                .replace("중요한 증거", "중요한 보존 흔적")
                .replace("결정적 단서", "주요 보존 흔적")
                .replace("미션메모", "")
                .replace("\"제목\"", "")
                .replace("'제목'", "")
                .replace("제목:", "")
                .replace("제목：", "")
                .replace("보상 단서", "증거")
                .replace("rewardClue", "증거")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static boolean setIfChanged(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        String before = getter.get();
        String after = sanitizeText(before);
        if (java.util.Objects.equals(before, after)) return false;
        setter.accept(after);
        return true;
    }

    private static boolean sanitizeList(List<String> values, java.util.function.Consumer<List<String>> setter) {
        if (values == null) return false;
        List<String> sanitized = new ArrayList<>();
        boolean changed = false;
        for (String value : values) {
            String after = sanitizeText(value);
            sanitized.add(after);
            changed |= !java.util.Objects.equals(value, after);
        }
        if (changed) setter.accept(sanitized);
        return changed;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
