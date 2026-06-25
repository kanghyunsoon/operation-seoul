package com.operation.seoul.recommendation.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.ai.service.UserAiInsightService;
import com.operation.seoul.recommendation.dto.EpisodeRecommendationResponse;
import com.operation.seoul.recommendation.repository.EpisodeRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EpisodeRecommendationService {
    private final EpisodeRecommendationRepository recommendationRepository;
    private final UserAiInsightService userAiInsightService;

    public List<EpisodeRecommendationResponse> getRecommendations(User user, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 6 : limit, 20));
        int clearedCount = recommendationRepository.countCleared(user.getId());
        List<EpisodeRecommendationResponse> recommendations = recommendationRepository.findCandidates(user.getId()).stream()
                .peek(candidate -> enrich(user, candidate, clearedCount))
                .sorted(Comparator
                        .comparing(EpisodeRecommendationResponse::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EpisodeRecommendationResponse::getEpisodeId))
                .limit(safeLimit)
                .toList();
        applyAiReasons(recommendations, clearedCount);
        return recommendations;
    }

    private void applyAiReasons(List<EpisodeRecommendationResponse> recommendations, int clearedCount) {
        List<String> aiReasons = userAiInsightService.recommendationReasons(recommendations, clearedCount);
        if (aiReasons.size() != recommendations.size()) {
            return;
        }
        for (int i = 0; i < recommendations.size(); i++) {
            String reason = aiReasons.get(i);
            if (reason != null && !reason.isBlank()) {
                recommendations.get(i).setReason(reason);
            }
        }
    }

    private void enrich(User user, EpisodeRecommendationResponse candidate, int clearedCount) {
        int score = 50;
        if (Boolean.TRUE.equals(candidate.getCleared())) {
            score -= 60;
        } else {
            score += 30;
        }
        if (Boolean.TRUE.equals(candidate.getFavorited())) {
            score += 18;
        }
        int genreMatches = recommendationRepository.countClearedGenre(user.getId(), candidate.getGenre());
        int difficultyMatches = recommendationRepository.countClearedDifficulty(user.getId(), candidate.getDifficulty());
        if (genreMatches > 0) {
            score += 14;
        }
        if (difficultyMatches > 0) {
            score += 8;
        }
        if (clearedCount == 0 && isStarterDifficulty(candidate.getDifficulty())) {
            score += 16;
        }
        candidate.setScore(Math.max(0, score));
        candidate.setReason(buildReason(candidate, clearedCount, genreMatches, difficultyMatches));
    }

    private boolean isStarterDifficulty(String difficulty) {
        if (difficulty == null) {
            return true;
        }
        String normalized = difficulty.trim().toUpperCase();
        return normalized.contains("EASY") || normalized.contains("NORMAL") || normalized.contains("쉬움") || normalized.contains("보통");
    }

    private String buildReason(EpisodeRecommendationResponse candidate, int clearedCount, int genreMatches, int difficultyMatches) {
        if (Boolean.TRUE.equals(candidate.getCleared())) {
            return "이미 클리어한 사건입니다. 다시 플레이하거나 기록을 비교하고 싶을 때 적합합니다.";
        }
        if (Boolean.TRUE.equals(candidate.getFavorited())) {
            return "관심 목록에 담아둔 사건이라 우선 플레이 후보로 추천합니다.";
        }
        if (genreMatches > 0) {
            return "이전에 클리어한 사건과 장르가 비슷해 플레이 흐름이 익숙할 가능성이 높습니다.";
        }
        if (difficultyMatches > 0) {
            return "이전에 클리어한 사건과 난이도가 비슷해 부담 없이 이어가기 좋습니다.";
        }
        if (clearedCount == 0) {
            return "첫 플레이에 적합한 공개 미션 메모입니다. 브리핑부터 시작해 흐름을 익히기 좋습니다.";
        }
        return "아직 플레이하지 않은 공개 미션 메모이라 다음 후보로 추천합니다.";
    }
}
