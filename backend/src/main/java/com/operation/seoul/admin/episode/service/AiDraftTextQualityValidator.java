package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.ArrayList;
import java.util.List;

final class AiDraftTextQualityValidator {
    private static final String MOJIBAKE_CHARS = "�譏硫濫뙿먕쨌짰鈺筌揶疫袁癰域雅甕";

    private AiDraftTextQualityValidator() {
    }

    static boolean containsMojibake(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.contains("???") || value.contains("????")) {
            return true;
        }
        for (int i = 0; i < MOJIBAKE_CHARS.length(); i++) {
            if (value.indexOf(MOJIBAKE_CHARS.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    static List<AiEpisodeDraftValidationResponse.Finding> findings(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        if (draft == null) {
            return findings;
        }
        check(findings, "episodeTitle", null, draft.getEpisodeTitle());
        check(findings, "subtitle", null, draft.getSubtitle());
        check(findings, "genre", null, draft.getGenre());
        check(findings, "fictionSynopsis", null, draft.getFictionSynopsis());
        check(findings, "missionDescription", null, draft.getMissionDescription());
        check(findings, "finalAnswerType", null, draft.getFinalAnswerType());
        check(findings, "finalAnswer", null, draft.getFinalAnswer());
        check(findings, "finalQuestion", null, draft.getFinalQuestion());
        check(findings, "finalTruthSummary", null, draft.getFinalTruthSummary());
        check(findings, "actualHistorySummary", null, draft.getActualHistorySummary());
        checkList(findings, "finalAnswerKeywords", null, draft.getFinalAnswerKeywords());
        checkList(findings, "finalAnswerAliases", null, draft.getFinalAnswerAliases());
        checkList(findings, "deductionSecretFacts", null, draft.getDeductionSecretFacts());
        checkList(findings, "deductionForbiddenReveals", null, draft.getDeductionForbiddenReveals());

        if (draft.getFinalAnswers() != null) {
            check(findings, "finalAnswers.culprit", null, draft.getFinalAnswers().getCulprit());
            check(findings, "finalAnswers.weapon", null, draft.getFinalAnswers().getWeapon());
            check(findings, "finalAnswers.motive", null, draft.getFinalAnswers().getMotive());
            check(findings, "finalAnswers.method", null, draft.getFinalAnswers().getMethod());
        }
        if (draft.getFinalAnswerKeywordItems() != null) {
            for (int i = 0; i < draft.getFinalAnswerKeywordItems().size(); i++) {
                AiEpisodeDraftResponse.AnswerKeywordItem item = draft.getFinalAnswerKeywordItems().get(i);
                check(findings, "finalAnswerKeywordItems[" + i + "].displayType", null, item == null ? null : item.getDisplayType());
                check(findings, "finalAnswerKeywordItems[" + i + "].value", null, item == null ? null : first(item.getValue(), item.getKeyword(), item.getPersonName()));
                checkList(findings, "finalAnswerKeywordItems[" + i + "].aliases", null, item == null ? null : item.getAliases());
            }
        }
        if (draft.getMissions() != null) {
            for (int i = 0; i < draft.getMissions().size(); i++) {
                AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(i);
                Integer order = mission == null ? i + 1 : mission.getOrder();
                String prefix = "missions[" + i + "]";
                check(findings, prefix + ".placeName", order, mission == null ? null : mission.getPlaceName());
                check(findings, prefix + ".storyText", order, mission == null ? null : mission.getStoryText());
                check(findings, prefix + ".questionText", order, mission == null ? null : mission.getQuestionText());
                check(findings, prefix + ".answer", order, mission == null ? null : mission.getAnswer());
                check(findings, prefix + ".rewardClue", order, mission == null ? null : mission.getRewardClue());
                checkList(findings, prefix + ".hints", order, mission == null ? null : mission.getHints());
                check(findings, prefix + ".groundRule", order, mission == null ? null : mission.getGroundRule());
            }
        }
        if (draft.getSuspects() != null) {
            for (int i = 0; i < draft.getSuspects().size(); i++) {
                AiEpisodeDraftResponse.SuspectDraft suspect = draft.getSuspects().get(i);
                String prefix = "suspects[" + i + "]";
                check(findings, prefix + ".displayName", null, suspect == null ? null : suspect.getDisplayName());
                check(findings, prefix + ".alias", null, suspect == null ? null : suspect.getAlias());
                check(findings, prefix + ".shortDescription", null, suspect == null ? null : suspect.getShortDescription());
                check(findings, prefix + ".relationToVictim", null, suspect == null ? null : suspect.getRelationToVictim());
                check(findings, prefix + ".suspiciousPoint", null, suspect == null ? null : suspect.getSuspiciousPoint());
                check(findings, prefix + ".alibiSummary", null, suspect == null ? null : suspect.getAlibiSummary());
            }
        }
        return findings;
    }

    private static void checkList(List<AiEpisodeDraftValidationResponse.Finding> findings, String fieldPath, Integer order, List<String> values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            check(findings, fieldPath + "[" + i + "]", order, values.get(i));
        }
    }

    private static void check(List<AiEpisodeDraftValidationResponse.Finding> findings, String fieldPath, Integer order, String value) {
        if (!containsMojibake(value)) {
            return;
        }
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                .severity("ERROR")
                .code("MOJIBAKE_TEXT_DETECTED")
                .message("AI 생성 결과에 깨진 한글 또는 인코딩 오류 문자가 포함되어 있습니다. 다시 생성해 주세요.")
                .missionOrder(order)
                .fieldPath(fieldPath)
                .currentValue(trim(value))
                .autoFixable(false)
                .fixType("REGENERATE")
                .build());
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 120);
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
