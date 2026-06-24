package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.List;
import java.util.Map;

final class DraftFinalAnswerContractApplier {
    private static final String GENRE_NAME = "범죄 미스터리";
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;
    private static final Map<String, String> SLOT_LABELS = FinalAnswerSlots.LABELS;

    private DraftFinalAnswerContractApplier() {
    }

    static void apply(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        if (draft == null) return;

        FinalAnswerContractSupport.normalizeFinalAnswerKeywordItems(request);

        Map<String, String> approved = FinalAnswerContractSupport.approvedAnswers(request);
        FinalAnswerContractSupport.NameRole culprit = FinalAnswerContractSupport.splitNameRole(approved.get("CULPRIT"));
        approved.put("CULPRIT", culprit.name());

        List<String> values = SLOT_IDS.stream().map(approved::get).toList();
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerKeywords(values);
        draft.setFinalAnswerKeywordItems(SLOT_IDS.stream()
                .map(slot -> AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                        .slotId(slot).type(slot).displayType(SLOT_LABELS.get(slot)).label(SLOT_LABELS.get(slot))
                        .keyword(approved.get(slot)).value(approved.get(slot))
                        .personName("CULPRIT".equals(slot) ? culprit.name() : "")
                        .personRole("CULPRIT".equals(slot) ? culprit.role() : "")
                        .role("CULPRIT".equals(slot) ? culprit.role() : "")
                        .aliases(List.of()).build())
                .toList());
        draft.setFinalAnswers(AiEpisodeDraftResponse.FinalAnswers.builder()
                .culprit(approved.get("CULPRIT")).weapon(approved.get("WEAPON"))
                .motive(approved.get("MOTIVE")).method(approved.get("METHOD"))
                .relatedPerson(approved.get("CULPRIT")).coreClue(approved.get("WEAPON"))
                .finalLocation(approved.get("METHOD")).build());
        draft.setFinalAnswer(String.format("%s: %s / %s: %s / %s: %s / %s: %s",
                SLOT_LABELS.get("CULPRIT"), approved.get("CULPRIT"),
                SLOT_LABELS.get("WEAPON"), approved.get("WEAPON"),
                SLOT_LABELS.get("MOTIVE"), approved.get("MOTIVE"),
                SLOT_LABELS.get("METHOD"), approved.get("METHOD")));
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setFinalQuestion(defaultIfBlank(draft.getFinalQuestion(), "범인, 흉기, 동기, 사인을 각각 입력하세요."));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }
}
