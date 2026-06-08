package com.operation.seoul.recommendation.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.recommendation.dto.EpisodeRecommendationResponse;
import com.operation.seoul.recommendation.service.EpisodeRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class EpisodeRecommendationController {
    private final CurrentUserResolver currentUserResolver;
    private final EpisodeRecommendationService recommendationService;

    @GetMapping("/episodes")
    public ResponseEntity<ApiResponse<List<EpisodeRecommendationResponse>>> recommendEpisodes(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Episode recommendations.",
                recommendationService.getRecommendations(currentUserResolver.requireCurrentUser(), limit)
        ));
    }
}
