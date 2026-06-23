package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

final class AnswerPlanResponseFactory {
    private static final String GENRE_ID = "CRIME_MYSTERY";
    private static final String GENRE_NAME = "범죄 미스터리";

    private AnswerPlanResponseFactory() {
    }

    static AiEpisodePlanResponse build(TourApiPlanContext planContext, List<AiEpisodePlanResponse.AnswerKeyword> keywords) {
        List<String> storyAnchors = planContext == null ? List.of() : planContext.storyAnchors();
        attachSourceBasis(keywords, storyAnchors);
        return AiEpisodePlanResponse.builder()
                .selectedGenreId(GENRE_ID)
                .selectedGenreName(GENRE_NAME)
                .answerSlots(answerSlotPlans())
                .finalAnswerKeywords(keywords)
                .finalAnswerKeywordItems(keywords)
                .finalAnswers(finalAnswers(keywords))
                .finalQuestionGuide("조사 미션 8개를 완료한 뒤 범인, 흉기, 동기, 방법을 각각 입력합니다.")
                .rationale(storyAnchors.isEmpty()
                        ? "장르는 범죄 미스터리로 고정하고, 최종 정답 키워드는 선택 장소의 검수 문맥을 바탕으로 구체화합니다."
                        : "장르는 범죄 미스터리로 고정하고, 최종 정답 키워드는 TourAPI 역사/사건 앵커를 바탕으로 구체화합니다.")
                .tourApiStoryAnchors(storyAnchors)
                .tourApiPlanInputs(planContext == null ? List.of() : planContext.includedInputs())
                .excludedPlanInputs(planContext == null ? List.of() : planContext.excludedInputs())
                .planReviewRequired(false)
                .reviewReason("")
                .fieldVerificationRecommended(true)
                .rejectedGenreReasons(List.of("장소 힌트나 최종 장소 추리 구조는 사용하지 않습니다."))
                .validationWarnings(List.of())
                .nextSteps(List.of("4개 정답 슬롯을 검수하고 AI 초안을 생성하세요."))
                .build();
    }

    private static List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlotPlans() {
        return FinalAnswerSlots.IDS.stream()
                .map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder()
                        .slotId(slot)
                        .label(FinalAnswerSlots.LABELS.get(slot))
                        .description(FinalAnswerSlots.LABELS.get(slot) + " 정답 슬롯")
                        .minClueCount(2)
                        .build())
                .toList();
    }

    private static AiEpisodePlanResponse.FinalAnswers finalAnswers(List<AiEpisodePlanResponse.AnswerKeyword> keywords) {
        Map<String, String> values = new LinkedHashMap<>();
        for (AiEpisodePlanResponse.AnswerKeyword keyword : keywords == null ? List.<AiEpisodePlanResponse.AnswerKeyword>of() : keywords) {
            values.put(normalize(keyword.getSlotId()), keyword.getKeyword());
        }
        return AiEpisodePlanResponse.FinalAnswers.builder()
                .culprit(values.get("CULPRIT"))
                .weapon(values.get("WEAPON"))
                .motive(values.get("MOTIVE"))
                .method(values.get("METHOD"))
                .build();
    }

    private static void attachSourceBasis(List<AiEpisodePlanResponse.AnswerKeyword> keywords, List<String> storyAnchors) {
        if (keywords == null || keywords.isEmpty() || storyAnchors == null || storyAnchors.isEmpty()) return;
        String basis = String.join(" / ", storyAnchors);
        for (AiEpisodePlanResponse.AnswerKeyword keyword : keywords) {
            if (keyword != null) {
                keyword.setSourceBasis(basis);
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
