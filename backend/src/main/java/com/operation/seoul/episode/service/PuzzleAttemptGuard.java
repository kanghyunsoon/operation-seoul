package com.operation.seoul.episode.service;

import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PuzzleAttemptGuard {
    private final EpisodeRepository episodeRepository;

    @Value("${app.puzzle.attempt-limit:8}")
    private int attemptLimit;

    @Value("${app.puzzle.attempt-window-seconds:600}")
    private long attemptWindowSeconds;

    public void enforce(Long userId, Long puzzleId) {
        Integer wrongCount = episodeRepository.findActivePuzzleWrongCount(userId, puzzleId);
        if (wrongCount != null && wrongCount >= Math.max(1, attemptLimit)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PUZZLE_ATTEMPT_LIMIT_EXCEEDED",
                    "오답 제출 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요."
            );
        }
    }

    @Transactional
    public void recordWrong(Long userId, Long puzzleId) {
        episodeRepository.recordPuzzleWrongAttempt(userId, puzzleId, Math.max(60L, attemptWindowSeconds));
    }

    @Transactional
    public void clear(Long userId, Long puzzleId) {
        episodeRepository.clearPuzzleAttempts(userId, puzzleId);
    }
}
