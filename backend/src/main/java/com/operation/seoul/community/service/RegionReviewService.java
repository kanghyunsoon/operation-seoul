package com.operation.seoul.community.service;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.community.domain.RegionReview;
import com.operation.seoul.community.dto.RegionReviewListResponse;
import com.operation.seoul.community.dto.RegionReviewRequest;
import com.operation.seoul.community.dto.RegionReviewResponse;
import com.operation.seoul.community.dto.RegionReviewSummary;
import com.operation.seoul.community.repository.RegionReviewRepository;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegionReviewService {

    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final RegionReviewRepository reviewRepository;
    private final CurrentUserResolver currentUserResolver;

    public RegionReviewListResponse getReviews(Long regionId, String sort, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        String normalizedSort = normalizeSort(sort);
        List<RegionReviewResponse> reviews = reviewRepository.findResponsesByRegionId(regionId, normalizedSort, userId);
        reviews.forEach(review -> review.setMine(review.getUserId().equals(userId)));
        RegionReviewSummary summary = reviewRepository.findSummaryByRegionId(regionId);
        RegionReview myReview = reviewRepository.findByRegionIdAndUserId(regionId, userId);

        return RegionReviewListResponse.builder()
                .reviews(reviews)
                .averageRating(summary == null || summary.getAverageRating() == null ? 0.0 : summary.getAverageRating())
                .reviewCount(summary == null || summary.getReviewCount() == null ? 0 : summary.getReviewCount())
                .canReview(hasClearedFinalMission(regionId, userId))
                .myReviewId(myReview == null ? null : myReview.getId())
                .build();
    }

    public RegionReviewResponse createReview(Long regionId, RegionReviewRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        requireCleared(regionId, userId);
        if (reviewRepository.findByRegionIdAndUserId(regionId, userId) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 이 작전에 리뷰를 작성했습니다.");
        }

        RegionReview review = new RegionReview();
        review.setRegionId(regionId);
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(cleanText(request.getContent()));
        reviewRepository.insert(review);
        return findVisibleReview(regionId, review.getId(), userId);
    }

    public RegionReviewResponse updateReview(Long regionId, Long reviewId, RegionReviewRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionReview review = requireReview(regionId, reviewId);
        requireOwnerOrAdmin(review.getUserId(), userId);
        requireCleared(regionId, review.getUserId());

        review.setRating(request.getRating());
        review.setContent(cleanText(request.getContent()));
        reviewRepository.update(review);
        return findVisibleReview(regionId, reviewId, userId);
    }

    public void deleteReview(Long regionId, Long reviewId, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionReview review = requireReview(regionId, reviewId);
        requireOwnerOrAdmin(review.getUserId(), userId);
        reviewRepository.deleteLikesByReviewId(reviewId);
        int deleted = reviewRepository.deleteById(reviewId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "리뷰를 삭제하지 못했습니다.");
        }
    }

    public RegionReviewResponse toggleReviewLike(Long regionId, Long reviewId, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        requireReview(regionId, reviewId);
        if (reviewRepository.countLikeByReviewIdAndUserId(reviewId, userId) > 0) {
            reviewRepository.deleteReviewLike(reviewId, userId);
        } else {
            reviewRepository.insertReviewLike(reviewId, userId);
        }
        return findVisibleReview(regionId, reviewId, userId);
    }

    public RegionReviewSummary getSummary(Long regionId) {
        requireRegion(regionId);
        RegionReviewSummary summary = reviewRepository.findSummaryByRegionId(regionId);
        if (summary != null) {
            return summary;
        }
        RegionReviewSummary empty = new RegionReviewSummary();
        empty.setRegionId(regionId);
        empty.setAverageRating(0.0);
        empty.setReviewCount(0);
        return empty;
    }

    public boolean hasClearedFinalMission(Long regionId, Long userId) {
        Optional<Mission> finalMission = missionRepository.findByRegionId(regionId).stream()
                .filter(Mission::isFinal)
                .findFirst();
        if (finalMission.isEmpty()) {
            return false;
        }
        return gameSessionRepository.findByUserIdAndMissionId(userId, finalMission.get().getId())
                .filter(session -> "CLEARED".equals(session.getStatus()))
                .isPresent();
    }

    private RegionReviewResponse findVisibleReview(Long regionId, Long reviewId, Long userId) {
        return reviewRepository.findResponsesByRegionId(regionId, "latest", userId).stream()
                .filter(item -> item.getId().equals(reviewId))
                .peek(item -> item.setMine(item.getUserId().equals(userId)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."));
    }

    private void requireRegion(Long regionId) {
        if (!regionRepository.existsById(regionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "작전을 찾을 수 없습니다.");
        }
    }

    private RegionReview requireReview(Long regionId, Long reviewId) {
        RegionReview review = reviewRepository.findByIdAndRegionId(reviewId, regionId);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다.");
        }
        return review;
    }

    private void requireCleared(Long regionId, Long userId) {
        if (!hasClearedFinalMission(regionId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "클리어한 작전에만 리뷰를 남길 수 있습니다.");
        }
    }

    private void requireOwnerOrAdmin(Long ownerId, Long userId) {
        if (!ownerId.equals(userId) && !currentUserResolver.resolveIsAdmin(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수정할 수 있습니다.");
        }
    }

    private String normalizeSort(String sort) {
        String value = sort == null ? "latest" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "rating_desc", "rating_asc", "clear_time" -> value;
            default -> "latest";
        };
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }
}
