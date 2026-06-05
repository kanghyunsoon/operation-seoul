package com.operation.seoul.review.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.review.domain.EpisodeReview;
import com.operation.seoul.review.dto.EpisodeReviewListResponse;
import com.operation.seoul.review.dto.EpisodeReviewRequest;
import com.operation.seoul.review.dto.EpisodeReviewResponse;
import com.operation.seoul.review.repository.EpisodeReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EpisodeReviewService {
    private final EpisodeReviewRepository reviewRepository;
    private final EpisodeRepository episodeRepository;

    public EpisodeReviewListResponse getEpisodeReviews(Long episodeId, User user) {
        requireEpisode(episodeId);
        List<EpisodeReviewResponse> reviews = reviewRepository.findByEpisodeId(episodeId).stream()
                .map(review -> toResponse(review, user))
                .toList();
        EpisodeReview myReview = reviewRepository.findByEpisodeIdAndUserId(episodeId, user.getId());
        boolean cleared = canReview(episodeId, user.getId());
        boolean canReview = cleared && myReview == null;
        String message = canReview
                ? "리뷰를 작성할 수 있습니다."
                : (myReview != null ? "이미 이 에피소드에 리뷰를 작성했습니다." : "클리어한 에피소드에만 리뷰를 작성할 수 있습니다.");
        return EpisodeReviewListResponse.builder()
                .reviews(reviews)
                .averageRating(reviewRepository.averageRating(episodeId))
                .averageDifficultyRating(reviewRepository.averageDifficultyRating(episodeId))
                .reviewCount(reviewRepository.countVisible(episodeId))
                .canReview(canReview)
                .myReviewId(myReview == null ? null : myReview.getId())
                .message(message)
                .build();
    }

    public List<EpisodeReviewResponse> getMyReviews(User user) {
        return reviewRepository.findByUserId(user.getId()).stream()
                .map(review -> toResponse(review, user))
                .toList();
    }

    public EpisodeReviewResponse createReview(Long episodeId, EpisodeReviewRequest request, User user) {
        requireEpisode(episodeId);
        requireCleared(episodeId, user.getId());
        if (reviewRepository.findByEpisodeIdAndUserId(episodeId, user.getId()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "이미 이 에피소드에 리뷰를 작성했습니다.");
        }
        EpisodeReview review = new EpisodeReview();
        review.setEpisodeId(episodeId);
        review.setUserId(user.getId());
        applyRequest(review, request);
        reviewRepository.insert(review);
        return toResponse(reviewRepository.findById(review.getId()), user);
    }

    public EpisodeReviewResponse updateReview(Long reviewId, EpisodeReviewRequest request, User user) {
        EpisodeReview review = requireReview(reviewId);
        requireOwnerOrAdmin(review, user);
        requireCleared(review.getEpisodeId(), review.getUserId());
        applyRequest(review, request);
        reviewRepository.update(review);
        return toResponse(reviewRepository.findById(reviewId), user);
    }

    public void deleteReview(Long reviewId, User user) {
        EpisodeReview review = requireReview(reviewId);
        requireOwnerOrAdmin(review, user);
        reviewRepository.softDelete(reviewId);
    }

    private void requireCleared(Long episodeId, Long userId) {
        if (!canReview(episodeId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EPISODE_NOT_CLEARED", "클리어한 에피소드에만 리뷰를 작성할 수 있습니다.");
        }
    }

    private boolean canReview(Long episodeId, Long userId) {
        return reviewRepository.countClearedProgress(episodeId, userId) > 0;
    }

    private Episode requireEpisode(Long episodeId) {
        Episode episode = episodeRepository.findEpisodeById(episodeId);
        if (episode == null || !"PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "에피소드를 찾을 수 없습니다.");
        }
        return episode;
    }

    private EpisodeReview requireReview(Long reviewId) {
        EpisodeReview review = reviewRepository.findById(reviewId);
        if (review == null || "DELETED".equals(review.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다.");
        }
        return review;
    }

    private void requireOwnerOrAdmin(EpisodeReview review, User user) {
        if (!review.getUserId().equals(user.getId()) && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
        }
    }

    private void applyRequest(EpisodeReview review, EpisodeReviewRequest request) {
        int rating = request.getRating() == null ? 0 : request.getRating();
        int difficulty = request.getDifficultyRating() == null ? 0 : request.getDifficultyRating();
        if (rating < 1 || rating > 5 || difficulty < 1 || difficulty > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RATING", "별점과 난이도는 1점부터 5점까지 입력할 수 있습니다.");
        }
        if (request.getContent() == null || request.getContent().trim().length() < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT", "리뷰 내용은 5자 이상 입력해 주세요.");
        }
        review.setRating(rating);
        review.setDifficultyRating(difficulty);
        review.setContent(request.getContent().trim());
        review.setSpoiler(Boolean.TRUE.equals(request.getSpoiler()));
    }

    private EpisodeReviewResponse toResponse(EpisodeReview review, User user) {
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
                .mine(user != null && review.getUserId().equals(user.getId()))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}