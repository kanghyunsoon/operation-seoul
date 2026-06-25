package com.operation.seoul.user.dto;

import com.operation.seoul.playeranalysis.dto.PlayerAnalysisResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserFeedResponse {
    private boolean privateProfile;
    private String message;
    private UserFeedProfileResponse profile;
    private PlayerAnalysisResponse playerAnalysis;
    private CommunityPostPage communityPosts;
    private List<UserFeedClearMapResponse> clearMaps;

    @Data
    @Builder
    public static class CommunityPostPage {
        private List<UserFeedPostResponse> items;
        private int page;
        private int size;
        private int totalItems;
        private int totalPages;
        private boolean hasPrevious;
        private boolean hasNext;
    }
}
