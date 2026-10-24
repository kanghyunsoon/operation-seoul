package com.operation.seoul.game.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.VisionAiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.service.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSessionControllerTest {
    private final GameSessionRepository sessionRepository = mock(GameSessionRepository.class);
    private final GeminiAiService geminiAiService = mock(GeminiAiService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private final MissionRepository missionRepository = mock(MissionRepository.class);
    private final MissionService missionService = mock(MissionService.class);
    private final GameSessionController controller = new GameSessionController(
            sessionRepository,
            mock(VisionAiService.class),
            geminiAiService,
            currentUserResolver,
            missionRepository,
            missionService
    );

    @BeforeEach
    void setUp() {
        when(currentUserResolver.resolveUserId(any())).thenReturn(7L);
    }

    @Test
    void hintMissionChatDoesNotClearMission() {
        GameSession session = session();
        when(missionRepository.findById(3L)).thenReturn(Optional.of(mission(3L, false)));
        when(sessionRepository.findByUserIdAndMissionId(7L, 3L)).thenReturn(Optional.of(session));

        controller.streamAnswer(3L, chat("아무 말", 60L));

        assertEquals("IN_PROGRESS", session.getStatus());
        verify(geminiAiService, never()).verifyFinalAnswer(any(), any());
    }

    @Test
    void lockedFinalMissionIsNotClearedByCorrectAnswer() {
        GameSession session = session();
        when(missionRepository.findById(9L)).thenReturn(Optional.of(mission(9L, true)));
        when(sessionRepository.findByUserIdAndMissionId(7L, 9L)).thenReturn(Optional.of(session));
        when(missionService.isFinalUnlocked(1L, 7L)).thenReturn(false);
        when(geminiAiService.verifyFinalAnswer(9L, "광복")).thenReturn(true);

        controller.streamAnswer(9L, chat("광복", 60L));

        assertEquals("IN_PROGRESS", session.getStatus());
        assertNull(session.getClearedAt());
    }

    @Test
    void unlockedFinalMissionKeepsFirstClearRecord() {
        GameSession session = session();
        when(missionRepository.findById(9L)).thenReturn(Optional.of(mission(9L, true)));
        when(sessionRepository.findByUserIdAndMissionId(7L, 9L)).thenReturn(Optional.of(session));
        when(missionService.isFinalUnlocked(1L, 7L)).thenReturn(true);
        when(geminiAiService.verifyFinalAnswer(9L, "광복")).thenReturn(true);

        controller.streamAnswer(9L, chat("광복", 600L));
        LocalDateTime firstClearedAt = session.getClearedAt();
        Integer firstScore = session.getScore();
        controller.streamAnswer(9L, chat("광복", 1L));

        assertEquals("CLEARED", session.getStatus());
        assertEquals(firstClearedAt, session.getClearedAt());
        assertEquals(firstScore, session.getScore());
        assertEquals(600L, session.getElapsedSeconds());
        verify(sessionRepository, times(1)).save(session);
    }

    private Mission mission(Long id, boolean finalMission) {
        Mission mission = new Mission();
        mission.setId(id);
        mission.setRegionId(1L);
        mission.setFinal(finalMission);
        return mission;
    }

    private GameSession session() {
        GameSession session = new GameSession();
        session.setId(1L);
        session.setUserId(7L);
        session.setStatus("IN_PROGRESS");
        session.setStartedAt(LocalDateTime.now().minusMinutes(10));
        return session;
    }

    private GameSessionController.ChatRequest chat(String answer, Long elapsedSeconds) {
        GameSessionController.ChatRequest request = new GameSessionController.ChatRequest();
        request.setUserAnswer(answer);
        request.setElapsedSeconds(elapsedSeconds);
        return request;
    }
}
