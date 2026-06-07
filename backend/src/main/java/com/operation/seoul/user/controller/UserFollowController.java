package com.operation.seoul.user.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.user.dto.UserFollowResponse;
import com.operation.seoul.user.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserFollowController {
    private final CurrentUserResolver currentUserResolver;
    private final UserFollowService followService;

    @GetMapping("/me/following")
    public ResponseEntity<ApiResponse<List<UserFollowResponse>>> following() {
        return ResponseEntity.ok(ApiResponse.ok("Following users.", followService.getFollowing(currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/me/followers")
    public ResponseEntity<ApiResponse<List<UserFollowResponse>>> followers() {
        return ResponseEntity.ok(ApiResponse.ok("Follower users.", followService.getFollowers(currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/{targetUserId}/follow")
    public ResponseEntity<ApiResponse<UserFollowResponse>> follow(@PathVariable Long targetUserId) {
        return ResponseEntity.ok(ApiResponse.ok("User followed.", followService.follow(currentUserResolver.requireCurrentUser(), targetUserId)));
    }

    @DeleteMapping("/{targetUserId}/follow")
    public ResponseEntity<ApiResponse<UserFollowResponse>> unfollow(@PathVariable Long targetUserId) {
        return ResponseEntity.ok(ApiResponse.ok("User unfollowed.", followService.unfollow(currentUserResolver.requireCurrentUser(), targetUserId)));
    }
}
