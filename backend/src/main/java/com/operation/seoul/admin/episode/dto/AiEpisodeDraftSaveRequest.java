package com.operation.seoul.admin.episode.dto;

import lombok.Data;

@Data
public class AiEpisodeDraftSaveRequest {
    private AiEpisodeDraftResponse.EpisodeDraft draft;
    private String status;
}