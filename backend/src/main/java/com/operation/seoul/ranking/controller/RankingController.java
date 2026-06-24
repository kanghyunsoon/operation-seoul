package com.operation.seoul.ranking.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.ranking.dto.RankingEntryResponse;
import com.operation.seoul.ranking.dto.PlayerRankingResponse;
import com.operation.seoul.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {
    private final CurrentUserResolver currentUserResolver;
    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RankingEntryResponse>>> rankings(
            @RequestParam(value = "episodeId", required = false) Long episodeId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok("Ranking list.", rankingService.getRankings(episodeId, limit)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RankingEntryResponse>>> myRankings(
            @RequestParam(value = "episodeId", required = false) Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("My clear rankings.", rankingService.getMyClears(currentUserResolver.requireCurrentUser(), episodeId)));
    }

    @GetMapping("/players")
    public ResponseEntity<ApiResponse<List<PlayerRankingResponse>>> playerRankings(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok("Player rankings.", rankingService.getPlayerRankings(limit)));
    }
}
