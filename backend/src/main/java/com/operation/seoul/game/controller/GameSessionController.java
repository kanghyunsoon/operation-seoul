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

        Long tempUserId = 1L; // 🚨 추후 JWT 적용 시 로그인한 유저 ID로 교체

        // 1. 세션이 없으면 생성 (500 에러 방지)
        GameSession session = sessionRepository.findByUserIdAndMissionId(tempUserId, missionId)
                .orElseGet(() -> {
                    GameSession newSession = new GameSession();
                    newSession.setUserId(tempUserId);
                    newSession.setMissionId(missionId);
                    newSession.setStatus("ARRIVED");
                    return sessionRepository.save(newSession);
                });

        // 2. 실제 Vision AI 서비스 호출 (MultipartFile 그대로 전달)
        boolean isSuccess = visionAiService.validateKeyword(missionId, image);

        if (isSuccess) {
            session.setStatus("PHOTO_VERIFIED");
            sessionRepository.save(session);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "인증 성공! 본부와의 보안 통신망이 확보되었습니다.",
                    "status", "PHOTO_VERIFIED"
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

        Long tempUserId = 1L;

        GameSession session = sessionRepository.findByUserIdAndMissionId(tempUserId, missionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. 스캔 인증을 먼저 진행하십시오."));

        boolean isCorrect = geminiAiService.verifyFinalAnswer(missionId, request.getUserAnswer());

        if (isCorrect) {
            session.setStatus("CLEARED");
            sessionRepository.save(session);
        }

        return geminiAiService.streamNarration(missionId, request.getUserAnswer(), isCorrect);
    }

    // 🚨 컴파일 및 JSON 파싱 에러 방지를 위해 반드시 public static class 로 선언!
    @Data
    public static class ChatRequest {
        private String userAnswer;
    }
}