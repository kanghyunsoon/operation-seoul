package com.operation.seoul.game.controller;

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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionRepository sessionRepository;
    private final VisionAiService visionAiService;
    private final GeminiAiService geminiAiService;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        return ResponseEntity.ok(visionAiService.verifyAndRecordMission(missionId, image, userId, isAdmin));
    }

    @PostMapping("/{missionId}/chat/stream")
    public ResponseBodyEmitter streamAnswer(
            @PathVariable Long missionId,
            @RequestBody ChatRequest request) {

        Long userId = request.getUserId() != null ? request.getUserId() : 1L;

        GameSession session = sessionRepository.findByUserIdAndMissionId(userId, missionId)
                .orElseGet(() -> {
                    GameSession newSession = new GameSession();
                    newSession.setUserId(userId);
                    newSession.setMissionId(missionId);
                    newSession.setStatus("IN_PROGRESS");
                    return sessionRepository.save(newSession);
                });

        boolean isCorrect = geminiAiService.verifyFinalAnswer(missionId, request.getUserAnswer());
        boolean isQuestion = !isCorrect && geminiAiService.isHintQuestion(request.getUserAnswer());

        if (isCorrect) {
            session.setStatus("CLEARED");
            sessionRepository.save(session);
        }

        return isQuestion
                ? geminiAiService.streamHintAnswer(missionId, request.getUserAnswer())
                : geminiAiService.streamNarration(missionId, request.getUserAnswer(), isCorrect);
    }

    @GetMapping("/{missionId}/status")
    public ResponseEntity<?> getSessionStatus(
            @PathVariable Long missionId,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        String status = sessionRepository.findByUserIdAndMissionId(userId, missionId)
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
        return ResponseEntity.ok(geminiAiService.generateClearReport(missionId, userId));
    }

    // 🚨 컴파일 및 JSON 파싱 에러 방지를 위해 반드시 public static class 로 선언!
    @Data
    public static class ChatRequest {
        private Long userId;
        private String userAnswer;
    }
}
