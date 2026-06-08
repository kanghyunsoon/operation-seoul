package com.operation.seoul.coaching.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.coaching.dto.CoachingReportResponse;
import com.operation.seoul.coaching.dto.CoachingSummaryResponse;
import com.operation.seoul.coaching.service.CoachingService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coaching")
@RequiredArgsConstructor
public class CoachingController {
    private final CurrentUserResolver currentUserResolver;
    private final CoachingService coachingService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CoachingSummaryResponse>> myCoaching() {
        return ResponseEntity.ok(ApiResponse.ok("Coaching summary.", coachingService.getSummary(currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<CoachingReportResponse>> episodeCoaching(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("Episode coaching report.", coachingService.getEpisodeReport(currentUserResolver.requireCurrentUser(), episodeId)));
    }
}
