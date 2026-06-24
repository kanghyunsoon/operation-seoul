package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EpisodePageResponse {
    private List<EpisodeListItemResponse> items;
    private int limit;
    private int offset;
    private boolean hasMore;
    private Integer totalCount;
}
