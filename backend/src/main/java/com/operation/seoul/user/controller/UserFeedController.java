package com.operation.seoul.user.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.user.dto.UserFeedResponse;
import com.operation.seoul.user.service.UserFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/feed")
@RequiredArgsConstructor
public class UserFeedController {
    private final CurrentUserResolver currentUserResolver;
    private final UserFeedService feedService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserFeedResponse>> myFeed(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok("User feed.", feedService.getMyFeed(currentUserResolver.requireCurrentUser(), page, size)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserFeedResponse>> userFeed(
            @PathVariable Long userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok("User feed.", feedService.getUserFeed(currentUserResolver.requireCurrentUser(), userId, page, size)));
    }
}
