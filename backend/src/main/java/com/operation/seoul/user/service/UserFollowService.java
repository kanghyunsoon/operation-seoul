package com.operation.seoul.user.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.user.dto.UserFollowResponse;
import com.operation.seoul.user.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFollowService {
    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;

    public UserFollowResponse follow(User currentUser, Long targetUserId) {
        User target = requireTarget(currentUser, targetUserId);
        followRepository.follow(currentUser.getId(), targetUserId);
        return toResponse(currentUser.getId(), target);
    }

    public UserFollowResponse unfollow(User currentUser, Long targetUserId) {
        User target = requireTarget(currentUser, targetUserId);
        followRepository.unfollow(currentUser.getId(), targetUserId);
        return toResponse(currentUser.getId(), target);
    }

    public List<UserFollowResponse> getFollowing(User currentUser) {
        return followRepository.findFollowing(currentUser.getId());
    }

    public List<UserFollowResponse> getFollowers(User currentUser) {
        return followRepository.findFollowers(currentUser.getId(), currentUser.getId());
    }

    private User requireTarget(User currentUser, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FOLLOW_TARGET", "You cannot follow yourself.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."));
        if (!target.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_INACTIVE", "Inactive users cannot be followed.");
        }
        return target;
    }

    private UserFollowResponse toResponse(Long viewerId, User target) {
        return UserFollowResponse.builder()
                .userId(target.getId())
                .nickname(target.getNickname())
                .profileImageUrl(target.getProfileImageUrl())
                .following(followRepository.isFollowing(viewerId, target.getId()) > 0)
                .followerCount(followRepository.countFollowers(target.getId()))
                .followingCount(followRepository.countFollowing(target.getId()))
                .build();
    }
}
