package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EpisodeFilterOptionsResponse {
    private List<String> eras;
}
