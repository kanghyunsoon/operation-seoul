package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiEpisodeDraftValidationRequest {
    private AiEpisodeDraftResponse.EpisodeDraft draft;
    private AiEpisodeDraftRequest sourceInput;

    private boolean useGemini;

    // 추가
    private String validationMode; // PLAN, DRAFT_SAVE, PUBLISH, RECHECK
    private Boolean strictMode;
    private List<String> enabledRuleCodes;
    private List<String> ignoredRuleCodes;
}