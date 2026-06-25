package com.operation.seoul.challenge.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.challenge.dto.ChallengeResponse;
import com.operation.seoul.challenge.dto.ChallengeSummaryResponse;
import com.operation.seoul.challenge.service.ChallengeService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {
    private final CurrentUserResolver currentUserResolver;
    private final ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> challenges() {
        return ResponseEntity.ok(ApiResponse.ok("Challenges.", challengeService.getChallenges(currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> myChallenges() {
        return ResponseEntity.ok(ApiResponse.ok("My challenges.", challengeService.getMyChallenges(currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ChallengeSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok("Challenge summary.", challengeService.getSummary(currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/{challengeId}/join")
    public ResponseEntity<ApiResponse<ChallengeResponse>> join(@PathVariable Long challengeId) {
        return ResponseEntity.ok(ApiResponse.ok("Challenge joined.", challengeService.join(currentUserResolver.requireCurrentUser(), challengeId)));
    }
}
