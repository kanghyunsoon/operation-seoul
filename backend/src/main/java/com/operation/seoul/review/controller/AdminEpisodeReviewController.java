package com.operation.seoul.review.controller;

import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.review.dto.EpisodeReviewResponse;
import com.operation.seoul.review.service.AdminEpisodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminEpisodeReviewController {
    private final AdminEpisodeReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EpisodeReviewResponse>>> getReviews(
            @RequestParam(value = "episodeId", required = false) Long episodeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.ok("관리자 리뷰 목록입니다.", reviewService.getReviews(episodeId, status, keyword)));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<EpisodeReviewResponse>> getReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(ApiResponse.ok("관리자 리뷰 상세입니다.", reviewService.getReview(reviewId)));
    }

    @PutMapping("/{reviewId}/hide")
    public ResponseEntity<ApiResponse<EpisodeReviewResponse>> hideReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 숨김 처리되었습니다.", reviewService.hideReview(reviewId)));
    }

    @PutMapping("/{reviewId}/restore")
    public ResponseEntity<ApiResponse<EpisodeReviewResponse>> restoreReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 복구되었습니다.", reviewService.restoreReview(reviewId)));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 삭제 처리되었습니다."));
    }
}