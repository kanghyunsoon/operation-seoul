package com.operation.seoul.challenge.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.challenge.dto.ChallengeResponse;
import com.operation.seoul.challenge.repository.ChallengeRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;

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
}
