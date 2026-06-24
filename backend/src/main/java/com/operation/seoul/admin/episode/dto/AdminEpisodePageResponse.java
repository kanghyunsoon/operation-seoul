package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminEpisodePageResponse {
    private List<AdminEpisodeListResponse> items;
    private int limit;
    private int offset;
    private boolean hasMore;
    private int totalCount;
}
