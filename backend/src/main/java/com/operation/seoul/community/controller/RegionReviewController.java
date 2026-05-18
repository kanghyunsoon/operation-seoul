package com.operation.seoul.community.controller;

import com.operation.seoul.community.dto.RegionReviewListResponse;
import com.operation.seoul.community.dto.RegionReviewRequest;
import com.operation.seoul.community.dto.RegionReviewResponse;
import com.operation.seoul.community.dto.RegionReviewSummary;
import com.operation.seoul.community.service.RegionReviewService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions/{regionId}/reviews")
@RequiredArgsConstructor
public class RegionReviewController {

    private final RegionReviewService reviewService;

    @GetMapping
    public ResponseEntity<RegionReviewListResponse> getReviews(
            @PathVariable Long regionId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(reviewService.getReviews(regionId, sort, userId));
    }

    @GetMapping("/summary")
    public ResponseEntity<RegionReviewSummary> getSummary(@PathVariable Long regionId) {
        return ResponseEntity.ok(reviewService.getSummary(regionId));
    }

    @PostMapping
    public ResponseEntity<RegionReviewResponse> createReview(
            @PathVariable Long regionId,
            @Valid @RequestBody RegionReviewRequest request,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(regionId, request, userId));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<RegionReviewResponse> updateReview(
            @PathVariable Long regionId,
            @PathVariable Long reviewId,
            @Valid @RequestBody RegionReviewRequest request,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(reviewService.updateReview(regionId, reviewId, request, userId));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long regionId,
            @PathVariable Long reviewId,
            @RequestParam(value = "userId", required = false) Long userId) {
        reviewService.deleteReview(regionId, reviewId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reviewId}/like")
    public ResponseEntity<RegionReviewResponse> toggleReviewLike(
            @PathVariable Long regionId,
            @PathVariable Long reviewId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(reviewService.toggleReviewLike(regionId, reviewId, userId));
    }
}
