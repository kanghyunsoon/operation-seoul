package com.operation.seoul.challenge.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.challenge.dto.ChallengeResponse;
import com.operation.seoul.challenge.dto.ChallengeSummaryResponse;
import com.operation.seoul.challenge.dto.ChallengeSummaryResponse.ChallengeGoal;
import com.operation.seoul.challenge.repository.ChallengeMetricRepository;
import com.operation.seoul.challenge.repository.ChallengeRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private static final int[] SINGLE_SCORE_TARGETS = {300, 500, 700, 900};
    private static final int[] TOTAL_SCORE_TARGETS = {1000, 3000, 5000, 10000};
    private static final int[] FRIEND_TARGETS = {1, 3, 5, 10};
    private static final int[] PLAY_DAY_TARGETS = {1, 3, 7, 14};

    private final ChallengeRepository challengeRepository;
    private final ChallengeMetricRepository metricRepository;

    public List<ChallengeResponse> getChallenges(User user) {
        List<ChallengeResponse> challenges = challengeRepository.findActiveChallenges(user.getId());
        boolean changed = false;
        for (ChallengeResponse challenge : challenges) {
            changed = refreshCompletion(user, challenge) || changed;
        }
        if (changed) {
            challenges = challengeRepository.findActiveChallenges(user.getId());
            challenges.forEach(this::applyComputedFields);
        }
        return challenges;
    }

    public List<ChallengeResponse> getMyChallenges(User user) {
        List<ChallengeResponse> challenges = challengeRepository.findMyChallenges(user.getId());
        boolean changed = false;
        for (ChallengeResponse challenge : challenges) {
            changed = refreshCompletion(user, challenge) || changed;
        }
        if (changed) {
            challenges = challengeRepository.findMyChallenges(user.getId());
            challenges.forEach(this::applyComputedFields);
        }
        return challenges;
    }

    public ChallengeResponse join(User user, Long challengeId) {
        ChallengeResponse challenge = requireChallenge(user, challengeId);
        challengeRepository.join(challengeId, user.getId());
        ChallengeResponse joined = requireChallenge(user, challengeId);
        refreshCompletion(user, joined);
        return requireChallenge(user, challengeId);
    }

    public ChallengeSummaryResponse getSummary(User user) {
        int singleScore = metricRepository.maxSingleScore(user.getId());
        int totalScore = metricRepository.totalScore(user.getId());
        int friends = metricRepository.mutualFriendCount(user.getId());
        int playDays = metricRepository.playDays(user.getId());

        List<ChallengeGoal> allGoals = List.of(
                currentGoal("SINGLE_SCORE", "개별 미션 최고 달성 점수", singleScore, SINGLE_SCORE_TARGETS, "점 이상"),
                currentGoal("TOTAL_SCORE", "누적 미션 달성 점수", totalScore, TOTAL_SCORE_TARGETS, "점 이상"),
                currentGoal("MUTUAL_FRIEND", "친구 수", friends, FRIEND_TARGETS, "명 이상"),
                currentGoal("PLAY_DAYS", "플레이일수", playDays, PLAY_DAY_TARGETS, "일 이상")
        );
        List<ChallengeGoal> completedGoals = new ArrayList<>();
        addCompletedGoals(completedGoals, "SINGLE_SCORE", "개별 미션 최고 달성 점수", singleScore, SINGLE_SCORE_TARGETS, "점 이상");
        addCompletedGoals(completedGoals, "TOTAL_SCORE", "누적 미션 달성 점수", totalScore, TOTAL_SCORE_TARGETS, "점 이상");
        addCompletedGoals(completedGoals, "MUTUAL_FRIEND", "친구 수", friends, FRIEND_TARGETS, "명 이상");
        addCompletedGoals(completedGoals, "PLAY_DAYS", "플레이일수", playDays, PLAY_DAY_TARGETS, "일 이상");

        return ChallengeSummaryResponse.builder()
                .activeGoals(allGoals)
                .completedGoals(completedGoals)
                .completedCount(completedGoals.size() + metricRepository.completedEntryCount(user.getId()))
                .build();
    }

    public int countAchievedChallenges(Long userId) {
        int count = metricRepository.completedEntryCount(userId);
        count += countTargets(metricRepository.maxSingleScore(userId), SINGLE_SCORE_TARGETS);
        count += countTargets(metricRepository.totalScore(userId), TOTAL_SCORE_TARGETS);
        count += countTargets(metricRepository.mutualFriendCount(userId), FRIEND_TARGETS);
        count += countTargets(metricRepository.playDays(userId), PLAY_DAY_TARGETS);
        return count;
    }

    private ChallengeResponse requireChallenge(User user, Long challengeId) {
        if (challengeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHALLENGE_REQUIRED", "Challenge is required.");
        }
        ChallengeResponse challenge = challengeRepository.findById(challengeId, user.getId());
        if (challenge == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND", "Challenge not found.");
        }
        applyComputedFields(challenge);
        return challenge;
    }

    private boolean refreshCompletion(User user, ChallengeResponse challenge) {
        applyComputedFields(challenge);
        boolean changed = false;
        if (Boolean.TRUE.equals(challenge.getJoined()) && Boolean.TRUE.equals(challenge.getCompleted()) && !"COMPLETED".equals(challenge.getEntryStatus())) {
            challengeRepository.complete(challenge.getId(), user.getId());
            challenge.setEntryStatus("COMPLETED");
            changed = true;
            Long nextChallengeId = challengeRepository.findNextHigherChallengeId(user.getId(), challenge.getTargetCount() == null ? 0 : challenge.getTargetCount());
            if (nextChallengeId != null) {
                challengeRepository.join(nextChallengeId, user.getId());
            }
        }
        return changed;
    }

    private void applyComputedFields(ChallengeResponse challenge) {
        int progress = challenge.getProgressCount() == null ? 0 : challenge.getProgressCount();
        int target = challenge.getTargetCount() == null ? 1 : challenge.getTargetCount();
        challenge.setCompleted(progress >= target);
        if (challenge.getEntryStatus() == null && Boolean.TRUE.equals(challenge.getJoined())) {
            challenge.setEntryStatus(Boolean.TRUE.equals(challenge.getCompleted()) ? "COMPLETED" : "JOINED");
        }
    }

    private ChallengeGoal currentGoal(String type, String title, int currentValue, int[] targets, String suffix) {
        int target = targets[targets.length - 1];
        for (int value : targets) {
            if (currentValue < value) {
                target = value;
                break;
            }
        }
        boolean completed = currentValue >= target;
        return ChallengeGoal.builder()
                .type(type)
                .title(title)
                .description(target + suffix)
                .currentValue(currentValue)
                .targetValue(target)
                .completed(completed)
                .build();
    }

    private void addCompletedGoals(List<ChallengeGoal> goals, String type, String title, int currentValue, int[] targets, String suffix) {
        for (int target : targets) {
            if (currentValue >= target) {
                goals.add(ChallengeGoal.builder()
                        .type(type)
                        .title(title)
                        .description(target + suffix)
                        .currentValue(currentValue)
                        .targetValue(target)
                        .completed(true)
                        .build());
            }
        }
    }

    private int countTargets(int currentValue, int[] targets) {
        int count = 0;
        for (int target : targets) {
            if (currentValue >= target) count += 1;
        }
        return count;
    }
}
