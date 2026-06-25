package com.operation.seoul.playeranalysis.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.playeranalysis.dto.PlayerAnalysisRequest;
import com.operation.seoul.playeranalysis.dto.PlayerAnalysisResponse;
import com.operation.seoul.playeranalysis.service.PlayerAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/player-analysis")
@RequiredArgsConstructor
public class PlayerAnalysisController {
    private final PlayerAnalysisService playerAnalysisService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<PlayerAnalysisResponse>> createAnalysis(@RequestBody PlayerAnalysisRequest request) {
        PlayerAnalysisResponse response = playerAnalysisService.createAnalysis(request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("AI 플레이 분석 결과입니다.", response));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<PlayerAnalysisResponse>> latestAnalysis(@RequestParam(required = false) Long userId) {
        PlayerAnalysisResponse response = playerAnalysisService.latestAnalysis(userId, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("최근 AI 플레이 분석 결과입니다.", response));
    }
}
