package com.operation.seoul.admin.episode.service;

import java.util.List;

record TourApiPlanContext(
        List<String> storyAnchors,
        List<String> includedInputs,
        List<String> excludedInputs,
        String historicalContext,
        String answerSeedContext
) {
    static TourApiPlanContext empty() {
        return new TourApiPlanContext(List.of(), List.of(), List.of(), "", "");
    }
}
