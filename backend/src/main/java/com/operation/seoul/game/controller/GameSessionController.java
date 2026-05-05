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

        // 1. 유저 ID와 미션 ID로 세션을 조회하고, 없으면 새로 만듭니다. (500 에러 방지)
        GameSession session = sessionRepository.findByUserIdAndMissionId(tempUserId, missionId)
                .orElseGet(() -> {
                    GameSession newSession = new GameSession();
                    newSession.setUserId(tempUserId);
                    newSession.setMissionId(missionId);
                    newSession.setStatus("ARRIVED");
                    return sessionRepository.save(newSession);
                });

        // 2. Vision AI + Gemini 지능형 판독 실행
        boolean isSuccess = visionAiService.validateKeyword(missionId, image);

        if (isSuccess) {
            // 3. 인증 성공 시 상태 업데이트
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

    @Data
    static class ChatRequest {
        private String userAnswer;
    }
}