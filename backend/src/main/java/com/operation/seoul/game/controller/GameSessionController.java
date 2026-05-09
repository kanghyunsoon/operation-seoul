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
            @RequestParam("image") MultipartFile image) {

        boolean isSuccess = visionAiService.validateKeyword(missionId, image);

        if (isSuccess) {
            // 👇 [핵심 추가] 비전 판독에 성공하면 DB에 클리어 도장을 찍어줍니다!
            Long tempUserId = 1L; // (추후 로그인 토큰 연동 시 변경)

            GameSession session = sessionRepository.findByUserIdAndMissionId(tempUserId, missionId)
                    .orElseGet(() -> {
                        GameSession newSession = new GameSession();
                        newSession.setUserId(tempUserId);
                        newSession.setMissionId(missionId);
                        return newSession;
                    });

            // 만약 미션 정보(Mission 엔티티)를 조회해서 분기할 수 있다면 가장 좋습니다.
            // 일반 힌트 미션 -> "CLEARED" 처리 (단서 해금)
            // 최종 목적지 미션 -> "PHOTO_VERIFIED" 처리 (채팅 해금 대기)

            // 임시로 무조건 일반 미션으로 간주하여 "CLEARED"로 업데이트하려면 아래처럼 박아줍니다.
            session.setStatus("CLEARED");
            sessionRepository.save(session);
            // 👆 여기까지 추가 및 수정

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "인증 성공! 단서가 해금되었습니다." // 메시지도 상황에 맞게 수정
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "목표 단서를 식별할 수 없습니다. 프레임에 정확히 담아주십시오."
            ));
        }
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
