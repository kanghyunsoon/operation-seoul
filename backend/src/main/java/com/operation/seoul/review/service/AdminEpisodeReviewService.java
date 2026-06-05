package com.operation.seoul.review.service;

import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.review.domain.EpisodeReview;
import com.operation.seoul.review.dto.EpisodeReviewResponse;
import com.operation.seoul.review.repository.EpisodeReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminEpisodeReviewService {
    private static final Set<String> ALLOWED_STATUS = Set.of("VISIBLE", "HIDDEN", "DELETED");

    private final EpisodeReviewRepository reviewRepository;

    public List<EpisodeReviewResponse> getReviews(Long episodeId, String status, String keyword) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return reviewRepository.findAdminReviews(episodeId, normalizedStatus, normalizedKeyword).stream()
                .map(this::toResponse)
                .toList();
    }

    public EpisodeReviewResponse getReview(Long reviewId) {
        return toResponse(requireReview(reviewId));
    }

    public EpisodeReviewResponse hideReview(Long reviewId) {
        requireReview(reviewId);
        reviewRepository.hide(reviewId);
        return toResponse(requireReview(reviewId));
    }

    public EpisodeReviewResponse restoreReview(Long reviewId) {
        requireReview(reviewId);
        reviewRepository.restore(reviewId);
        return toResponse(requireReview(reviewId));
    }

    public void deleteReview(Long reviewId) {
        requireReview(reviewId);
        reviewRepository.softDelete(reviewId);
    }

    private EpisodeReview requireReview(Long reviewId) {
        EpisodeReview review = reviewRepository.findById(reviewId);
        if (review == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다.");
        }
        return review;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "리뷰 상태 필터가 올바르지 않습니다.");
        }
        return normalized;
    }

    private EpisodeReviewResponse toResponse(EpisodeReview review) {
        return EpisodeReviewResponse.builder()
                .id(review.getId())
                .episodeId(review.getEpisodeId())
                .episodeTitle(review.getEpisodeTitle())
                .userId(review.getUserId())
                .authorNickname(review.getAuthorNickname())
                .rating(review.getRating())
                .difficultyRating(review.getDifficultyRating())
                .content(review.getContent())
                .spoiler(review.getSpoiler())
                .status(review.getStatus())
                .mine(false)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}