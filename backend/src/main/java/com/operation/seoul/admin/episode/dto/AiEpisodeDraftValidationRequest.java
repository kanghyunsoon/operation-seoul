package com.operation.seoul.admin.episode.dto;

import lombok.Data;

@Data
public class AiEpisodeDraftValidationRequest {
    private AiEpisodeDraftResponse.EpisodeDraft draft;
    private AiEpisodeDraftRequest sourceInput;
    private boolean useGemini;
}
