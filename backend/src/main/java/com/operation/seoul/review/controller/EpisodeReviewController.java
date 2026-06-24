package com.operation.seoul.review.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.review.dto.EpisodeReviewCommentRequest;
import com.operation.seoul.review.dto.EpisodeReviewCommentResponse;
import com.operation.seoul.review.dto.EpisodeReviewListResponse;
import com.operation.seoul.review.dto.EpisodeReviewRequest;
import com.operation.seoul.review.dto.EpisodeReviewResponse;
import com.operation.seoul.review.service.EpisodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EpisodeReviewController {
    private final EpisodeReviewService reviewService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/episodes/{episodeId}/reviews")
    public ResponseEntity<ApiResponse<EpisodeReviewListResponse>> getEpisodeReviews(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("리뷰 목록입니다.", reviewService.getEpisodeReviews(episodeId, currentUserResolver.requireCurrentUser())));
    }

    @PostMapping("/episodes/{episodeId}/reviews")
    public ResponseEntity<ApiResponse<EpisodeReviewResponse>> createReview(@PathVariable Long episodeId, @RequestBody EpisodeReviewRequest request) {
        EpisodeReviewResponse response = reviewService.createReview(episodeId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("리뷰가 등록되었습니다.", response));
    }

    @GetMapping("/users/me/reviews")
    public ResponseEntity<ApiResponse<List<EpisodeReviewResponse>>> getMyReviews() {
        return ResponseEntity.ok(ApiResponse.ok("내 리뷰 목록입니다.", reviewService.getMyReviews(currentUserResolver.requireCurrentUser())));
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<EpisodeReviewResponse>> updateReview(@PathVariable Long reviewId, @RequestBody EpisodeReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 수정되었습니다.", reviewService.updateReview(reviewId, request, currentUserResolver.requireCurrentUser())));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("리뷰가 삭제되었습니다."));
    }

    @PostMapping("/reviews/{reviewId}/comments")
    public ResponseEntity<ApiResponse<EpisodeReviewCommentResponse>> createComment(@PathVariable Long reviewId, @RequestBody EpisodeReviewCommentRequest request) {
        EpisodeReviewCommentResponse response = reviewService.createComment(reviewId, request, currentUserResolver.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("댓글이 등록되었습니다.", response));
    }

    @PutMapping("/review-comments/{commentId}")
    public ResponseEntity<ApiResponse<EpisodeReviewCommentResponse>> updateComment(@PathVariable Long commentId, @RequestBody EpisodeReviewCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("댓글이 수정되었습니다.", reviewService.updateComment(commentId, request, currentUserResolver.requireCurrentUser())));
    }

    @DeleteMapping("/review-comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long commentId) {
        reviewService.deleteComment(commentId, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다."));
    }
}
