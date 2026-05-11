package com.operation.seoul.game.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.VisionAiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionRepository sessionRepository;
    private final VisionAiService visionAiService;
    private final GeminiAiService geminiAiService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        boolean effectiveIsAdmin = currentUserResolver.resolveIsAdmin(isAdmin);
        return ResponseEntity.ok(visionAiService.verifyAndRecordMission(missionId, image, effectiveUserId, effectiveIsAdmin));
    }

    @PostMapping("/{missionId}/chat/stream")
    public ResponseBodyEmitter streamAnswer(
            @PathVariable Long missionId,
            @RequestBody ChatRequest request) {

        Long userId = currentUserResolver.resolveUserId(request.getUserId());

        GameSession session = sessionRepository.findByUserIdAndMissionId(userId, missionId)
                .orElseGet(() -> {
                    GameSession newSession = new GameSession();
                    newSession.setUserId(userId);
                    newSession.setMissionId(missionId);
                    newSession.setStatus("IN_PROGRESS");
                    newSession.setStartedAt(LocalDateTime.now());
                    return sessionRepository.save(newSession);
                });
        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }

        boolean isCorrect = geminiAiService.verifyFinalAnswer(missionId, request.getUserAnswer());
        boolean isQuestion = !isCorrect && geminiAiService.isHintQuestion(request.getUserAnswer());

        if (isCorrect) {
            session.setStatus("CLEARED");
            session.setClearedAt(LocalDateTime.now());
            session.setElapsedSeconds(resolveElapsedSeconds(session, request.getElapsedSeconds()));
            session.setRouteDistanceMeters(sanitizeRouteDistance(request.getRouteDistanceMeters()));
            session.setScore(calculateScore(session.getElapsedSeconds(), session.getRouteDistanceMeters()));
            sessionRepository.save(session);
        }

        return isQuestion
                ? geminiAiService.streamHintAnswer(missionId, userId, request.getUserAnswer())
                : geminiAiService.streamNarration(missionId, request.getUserAnswer(), isCorrect);
    }

    @GetMapping("/{missionId}/status")
    public ResponseEntity<?> getSessionStatus(
            @PathVariable Long missionId,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        String status = sessionRepository.findByUserIdAndMissionId(effectiveUserId, missionId)
                .map(GameSession::getStatus)
                .orElse("NONE");

        return ResponseEntity.ok(Map.of(
                "missionId", missionId,
                "status", status,
                "cleared", "CLEARED".equals(status)
        ));
    }

    @GetMapping("/{missionId}/clear-report")
    public ResponseEntity<?> getClearReport(
            @PathVariable Long missionId,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        return ResponseEntity.ok(geminiAiService.generateClearReport(missionId, effectiveUserId));
    }

    @Data
    public static class ChatRequest {
        private Long userId;
        private String userAnswer;
        private Long elapsedSeconds;
        private Double routeDistanceMeters;
    }

    private Long resolveElapsedSeconds(GameSession session, Long reportedElapsedSeconds) {
        if (reportedElapsedSeconds != null && reportedElapsedSeconds > 0) {
            return reportedElapsedSeconds;
        }
        if (session.getStartedAt() == null || session.getClearedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(session.getStartedAt(), session.getClearedAt()).getSeconds());
    }

    private Double sanitizeRouteDistance(Double routeDistanceMeters) {
        if (routeDistanceMeters == null || routeDistanceMeters.isNaN() || routeDistanceMeters.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, routeDistanceMeters);
    }

    private int calculateScore(Long elapsedSeconds, Double routeDistanceMeters) {
        long minutes = elapsedSeconds == null ? 0L : Math.max(0L, (long) Math.ceil(elapsedSeconds / 60.0));
        double distanceMeters = routeDistanceMeters == null ? 0.0 : Math.max(0.0, routeDistanceMeters);
        int timePenalty = (int) Math.min(500, minutes * 4);
        int routePenalty = (int) Math.min(300, Math.round(distanceMeters / 100.0) * 2);
        return Math.max(100, 1000 - timePenalty - routePenalty);
    }
}
