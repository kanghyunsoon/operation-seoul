package com.operation.seoul.user.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.challenge.service.ChallengeService;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.playeranalysis.dto.PlayerAnalysisResponse;
import com.operation.seoul.playeranalysis.service.PlayerAnalysisService;
import com.operation.seoul.user.dto.UserFeedProfileResponse;
import com.operation.seoul.user.dto.UserFeedResponse;
import com.operation.seoul.user.repository.UserFeedRepository;
import com.operation.seoul.user.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFeedService {
    private static final int DEFAULT_SIZE = 5;
    private static final int MAX_SIZE = 20;

    private final UserRepository userRepository;
    private final UserFeedRepository feedRepository;
    private final UserFollowRepository followRepository;
    private final PlayerAnalysisService playerAnalysisService;
    private final ChallengeService challengeService;

    @Transactional(readOnly = true)
    public UserFeedResponse getMyFeed(User currentUser, Integer requestedPage, Integer requestedSize) {
        return getUserFeed(currentUser, currentUser.getId(), requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public UserFeedResponse getUserFeed(User viewer, Long targetUserId, Integer requestedPage, Integer requestedSize) {
        User freshUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."));
        boolean ownProfile = viewer != null && freshUser.getId().equals(viewer.getId());
        boolean visible = ownProfile || (viewer != null && viewer.isAdmin()) || freshUser.isProfilePublic();
        if (!visible) {
            return UserFeedResponse.builder()
                    .privateProfile(true)
                    .message("프로필 정보가 비공개된 사용자입니다.")
                    .profile(UserFeedProfileResponse.builder()
                            .userId(freshUser.getId())
                            .nickname(freshUser.getNickname())
                            .profileImageUrl(freshUser.getProfileImageUrl())
                            .build())
                    .build();
        }
        int page = Math.max(0, requestedPage == null ? 0 : requestedPage);
        int size = Math.max(1, Math.min(MAX_SIZE, requestedSize == null ? DEFAULT_SIZE : requestedSize));
        int totalPosts = feedRepository.countCommunityPosts(freshUser.getId());
        int totalPages = totalPosts == 0 ? 0 : (int) Math.ceil((double) totalPosts / size);
        if (totalPages > 0 && page >= totalPages) {
            page = totalPages - 1;
        }
        int offset = page * size;

        PlayerAnalysisResponse latestAnalysis = playerAnalysisService.latestAnalysis(freshUser.getId(), freshUser);
        return UserFeedResponse.builder()
                .privateProfile(false)
                .profile(UserFeedProfileResponse.builder()
                        .userId(freshUser.getId())
                        .nickname(freshUser.getNickname())
                        .statusMessage(freshUser.getStatusMessage())
                        .profileImageUrl(freshUser.getProfileImageUrl())
                        .postCount(totalPosts)
                        .achievedChallengeCount(challengeService.countAchievedChallenges(freshUser.getId()))
                        .followerCount(followRepository.countFollowers(freshUser.getId()))
                        .followingCount(followRepository.countFollowing(freshUser.getId()))
                        .build())
                .playerAnalysis(latestAnalysis)
                .communityPosts(UserFeedResponse.CommunityPostPage.builder()
                        .items(feedRepository.findCommunityPosts(freshUser.getId(), size, offset))
                        .page(page)
                        .size(size)
                        .totalItems(totalPosts)
                        .totalPages(totalPages)
                        .hasPrevious(page > 0)
                        .hasNext(totalPages > 0 && page + 1 < totalPages)
                        .build())
                .clearMaps(feedRepository.findClearedMaps(freshUser.getId()))
                .build();
    }
}
