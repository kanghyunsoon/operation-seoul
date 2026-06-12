package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiEpisodeDraftSaveRequest {
    private AiEpisodeDraftResponse.EpisodeDraft draft;
    private AiEpisodeDraftRequest sourceInput;
    private AiEpisodeDraftValidationResponse validationResult;

    private String status;
    private String saveReason;
    private Boolean adminReviewed;
    private List<String> adminOverrideReasons;
}