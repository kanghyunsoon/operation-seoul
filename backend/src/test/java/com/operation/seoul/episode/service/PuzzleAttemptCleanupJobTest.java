package com.operation.seoul.episode.service;

import com.operation.seoul.episode.repository.EpisodeRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuzzleAttemptCleanupJobTest {

    @Test
    void deletesExpiredAttempts() {
        EpisodeRepository repository = mock(EpisodeRepository.class);
        when(repository.deleteExpiredPuzzleAttempts()).thenReturn(4);

        new PuzzleAttemptCleanupJob(repository).deleteExpiredAttempts();

        verify(repository).deleteExpiredPuzzleAttempts();
    }

    @Test
    void databaseFailureDoesNotStopScheduler() {
        EpisodeRepository repository = mock(EpisodeRepository.class);
        when(repository.deleteExpiredPuzzleAttempts()).thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> new PuzzleAttemptCleanupJob(repository).deleteExpiredAttempts());
    }
}
