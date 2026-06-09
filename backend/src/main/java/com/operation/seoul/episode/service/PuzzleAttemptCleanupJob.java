package com.operation.seoul.episode.service;

import com.operation.seoul.episode.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PuzzleAttemptCleanupJob {
    private final EpisodeRepository episodeRepository;

    @Scheduled(
            initialDelayString = "${app.puzzle.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.puzzle.cleanup-interval-ms:3600000}"
    )
    public void deleteExpiredAttempts() {
        try {
            int deleted = episodeRepository.deleteExpiredPuzzleAttempts();
            if (deleted > 0) {
                log.info("puzzle_attempt_cleanup deleted={}", deleted);
            }
        } catch (Exception exception) {
            log.error("puzzle_attempt_cleanup_failed", exception);
        }
    }
}
