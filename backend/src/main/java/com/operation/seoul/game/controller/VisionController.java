package com.operation.seoul.game.controller;

import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.game.service.VisionAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class VisionController {

    private final VisionAiService visionAiService;
    // 🚨 세션(진행 상태) 저장을 위해 Repository 의존성 추가
    private final GameSessionRepository gameSessionRepository;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "userId", defaultValue = "1") Long userId, // 🚨 유저 식별자 파라미터 추가
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        boolean isSuccess = false;

        // 1. 관리자 프리패스 or AI 실제 판독 분기
        if (isAdmin) {
            isSuccess = true; // 관리자는 무조건 통과
        } else {
            isSuccess = visionAiService.validateKeyword(missionId, image);
        }

        // 2. 🚨 판독 성공 시: DB에 진행 상태를 'CLEARED'로 저장 (누락되었던 핵심 로직!)
        if (isSuccess) {
            // 해당 유저의 미션 세션 기록이 있는지 조회, 없으면 새로 생성
            GameSession session = gameSessionRepository.findByUserIdAndMissionId(userId, missionId)
                    .orElseGet(() -> {
                        GameSession newSession = new GameSession();
                        newSession.setUserId(userId);
                        newSession.setMissionId(missionId);
                        return newSession;
                    });

            // 상태를 CLEARED로 업데이트하고 DB에 반영
            session.setStatus("CLEARED");
            gameSessionRepository.save(session);

            String keywordMsg = isAdmin ? "판독 성공 (관리자 프리패스)" : "판독 성공";
            return ResponseEntity.ok(Map.of("success", true, "keyword", keywordMsg));
        } else {
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}