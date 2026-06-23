package com.operation.seoul.episode.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.episode.dto.*;
import com.operation.seoul.episode.service.EpisodePlayService;
import com.operation.seoul.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EpisodePlayController {
    private final EpisodePlayService episodePlayService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/episodes")
    public ResponseEntity<ApiResponse<List<EpisodeListItemResponse>>> getEpisodes(@RequestParam(required = false) String areaCode) {
        return ResponseEntity.ok(ApiResponse.ok("에피소드 목록입니다.", episodePlayService.getEpisodes(currentUserResolver.requireCurrentUser(), areaCode)));
    }

    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<EpisodeDetailResponse>> getEpisode(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("에피소드 상세입니다.", episodePlayService.getEpisode(episodeId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/episodes/{episodeId}/start")
    public ResponseEntity<ApiResponse<EpisodeDetailResponse>> startEpisode(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("에피소드를 시작했습니다.", episodePlayService.startEpisode(episodeId, currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/episodes/{episodeId}/map")
    public ResponseEntity<ApiResponse<EpisodeMapResponse>> getEpisodeMap(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("에피소드 지도입니다.", episodePlayService.getMap(episodeId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/episodes/{episodeId}/spots/{spotId}/arrive")
    public ResponseEntity<ApiResponse<ArriveResponse>> arrive(@PathVariable Long episodeId, @PathVariable Long spotId, @Valid @RequestBody ArriveRequest request) {
        ArriveResponse response = episodePlayService.arrive(episodeId, spotId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    @PostMapping("/episodes/{episodeId}/final-arrive")
    public ResponseEntity<ApiResponse<ArriveResponse>> arriveFinalPlace(@PathVariable Long episodeId, @Valid @RequestBody ArriveRequest request) {
        ArriveResponse response = episodePlayService.arriveFinalPlace(episodeId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    @GetMapping("/spots/{spotId}/puzzle")
    public ResponseEntity<ApiResponse<PuzzleResponse>> getPuzzle(@PathVariable Long spotId) {
        return ResponseEntity.ok(ApiResponse.ok("퍼즐 정보입니다.", episodePlayService.getPuzzle(spotId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/puzzles/{puzzleId}/submit")
    public ResponseEntity<ApiResponse<PuzzleSubmitResponse>> submitPuzzle(@PathVariable Long puzzleId, @Valid @RequestBody PuzzleSubmitRequest request) {
        PuzzleSubmitResponse response = episodePlayService.submitPuzzle(puzzleId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    @GetMapping("/episodes/{episodeId}/clue-board")
    public ResponseEntity<ApiResponse<ClueBoardResponse>> getClueBoard(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("단서 보드입니다.", episodePlayService.getClueBoard(episodeId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/episodes/{episodeId}/deduction/start")
    public ResponseEntity<ApiResponse<DeductionStartResponse>> startDeduction(@PathVariable Long episodeId) {
        DeductionStartResponse response = episodePlayService.startDeduction(episodeId, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    @PostMapping("/deduction/{sessionId}/ask")
    public ResponseEntity<ApiResponse<DeductionAskResponse>> askDeduction(@PathVariable Long sessionId, @Valid @RequestBody DeductionAskRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("추리 질문 답변입니다.", episodePlayService.askDeduction(sessionId, request, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/deduction/{sessionId}/hypothesis")
    public ResponseEntity<ApiResponse<DeductionHypothesisResponse>> verifyDeductionHypothesis(@PathVariable Long sessionId, @Valid @RequestBody DeductionHypothesisRequest request) {
        DeductionHypothesisResponse response = episodePlayService.verifyDeductionHypothesis(sessionId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }
    @GetMapping("/deduction/{sessionId}/questions")
    public ResponseEntity<ApiResponse<List<DeductionQuestionResponse>>> getDeductionQuestions(@PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("추리 질문 기록입니다.", episodePlayService.getDeductionQuestions(sessionId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/episodes/{episodeId}/final-answer")
    public ResponseEntity<ApiResponse<FinalAnswerResponse>> submitFinalAnswer(@PathVariable Long episodeId, @Valid @RequestBody FinalAnswerRequest request) {
        FinalAnswerResponse response = episodePlayService.submitFinalAnswer(episodeId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    @GetMapping("/episodes/{episodeId}/clear-report")
    public ResponseEntity<ApiResponse<ClearReportResponse>> getClearReport(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("클리어 리포트입니다.", episodePlayService.getClearReport(episodeId, currentUserResolver.requireCurrentUser())));
    }
}
