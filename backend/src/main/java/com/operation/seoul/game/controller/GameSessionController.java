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

    /**
     * 현장 힌트 사진을 검증하고 성공 시 GameSession을 CLEARED로 기록합니다.
     * MapView와 AiChatView 모두 이 endpoint를 통해 Vision 인증을 수행합니다.
     */
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

    /**
     * 최종 미션 채팅 endpoint입니다.
     * 정답이면 세션을 클리어 처리하고, 질문이면 힌트 스트림, 일반 답변이면 판정 내레이션 스트림을 반환합니다.
     */
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

    /** 채팅 응답 직후 프론트가 최종 클리어 여부를 확인할 때 사용합니다. */
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

    /** 클리어 화면에서 역사 해설, 단서 해석, 점수 기록을 가져옵니다. */
    @GetMapping("/{missionId}/clear-report")
    public ResponseEntity<?> getClearReport(
            @PathVariable Long missionId,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        return ResponseEntity.ok(geminiAiService.generateClearReport(missionId, effectiveUserId));
    }

    @Data
    public static class ChatRequest {
        /** 토큰 기반 인증 이전 화면들과의 호환용 사용자 id입니다. 인증 사용자가 있으면 무시됩니다. */
        private Long userId;
        /** 사용자가 입력한 정답 후보 또는 질문 문장입니다. */
        private String userAnswer;
        /** 프론트가 로컬에 기록한 작전 소요 시간입니다. 없으면 서버 시간이 보조 계산합니다. */
        private Long elapsedSeconds;
        /** 프론트 GPS 추적으로 누적한 이동 거리입니다. */
        private Double routeDistanceMeters;
    }

    /** 프론트 보고 시간이 있으면 우선 사용하고, 없으면 세션 시작/완료 시각으로 보정합니다. */
    private Long resolveElapsedSeconds(GameSession session, Long reportedElapsedSeconds) {
        if (reportedElapsedSeconds != null && reportedElapsedSeconds > 0) {
            return reportedElapsedSeconds;
        }
        if (session.getStartedAt() == null || session.getClearedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(session.getStartedAt(), session.getClearedAt()).getSeconds());
    }

    /** 클라이언트에서 비정상 거리 값이 넘어와도 점수 계산이 깨지지 않게 0 이상으로 정리합니다. */
    private Double sanitizeRouteDistance(Double routeDistanceMeters) {
        if (routeDistanceMeters == null || routeDistanceMeters.isNaN() || routeDistanceMeters.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, routeDistanceMeters);
    }

    /** 빠르게, 덜 우회해서 클리어할수록 높은 점수를 주는 단순 감점식 점수 계산입니다. */
    private int calculateScore(Long elapsedSeconds, Double routeDistanceMeters) {
        long minutes = elapsedSeconds == null ? 0L : Math.max(0L, (long) Math.ceil(elapsedSeconds / 60.0));
        double distanceMeters = routeDistanceMeters == null ? 0.0 : Math.max(0.0, routeDistanceMeters);
        int timePenalty = (int) Math.min(500, minutes * 4);
        int routePenalty = (int) Math.min(300, Math.round(distanceMeters / 100.0) * 2);
        return Math.max(100, 1000 - timePenalty - routePenalty);
    }
}
