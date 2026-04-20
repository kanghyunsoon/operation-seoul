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

/**
 * [Controller: 게임 세션 상태 제어]
 * - 역할: 유저의 게임 진행 단계별 상태 관리 및 AI 인터페이스 연동
 */
@RestController
@RequestMapping("/api/v1/sessions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionRepository sessionRepository;
    private final VisionAiService visionAiService;
    private final GeminiAiService geminiAiService;

    /**
     * [API 1: 미션 시작 및 세션 초기화]
     * - 수행 내용: Location 모듈의 위치 인증 성공 시 호출되며, 게임 진행을 위한 세션 데이터를 생성함
     * - 상태 변경: "ARRIVED" (현장 도착 완료)
     * - 매개 변수: Long missionId (진행할 미션 번호)
     * - 반환 값: ResponseEntity<Long> (생성 또는 갱신된 세션 ID)
     */
    @PostMapping("/start/{missionId}")
    public ResponseEntity<Long> startGameSession(@PathVariable Long missionId) {
        Long userId = 1L; // 프로토타입용 고정 유저 식별자

        // 기존 진행 이력 확인 후 신규 세션 생성 또는 기존 세션 획득
        GameSession session = sessionRepository.findByUserIdAndMissionId(userId, missionId)
                .orElse(new GameSession());

        session.setUserId(userId);
        session.setMissionId(missionId);
        session.setStatus("ARRIVED"); // 상태값 설정을 통한 진행 단계 기록

        sessionRepository.save(session);

        return ResponseEntity.ok(session.getId());
    }

    /**
     * [API 2: 사진 인증 및 시각 지능 검증]
     * - 수행 내용: 업로드된 이미지에서 텍스트를 추출하여 미션 정답 키워드와 비교함
     * - 상태 변경: 인증 성공 시 "PHOTO_VERIFIED"
     * - 매개 변수:
     * 1. Long sessionId (현재 게임 세션 번호)
     * 2. MultipartFile image (유저가 촬영한 증거 사진)
     * - 반환 값: ResponseEntity<String> (인증 결과 메시지)
     */
    @PostMapping("/{sessionId}/vision")
    public ResponseEntity<String> verifyVision(
            @PathVariable Long sessionId,
            @RequestParam("image") MultipartFile image) {

        GameSession session = sessionRepository.findById(sessionId).orElseThrow();

        // 1. Vision AI 서비스를 통한 이미지 내 텍스트 추출
        String extractedText = visionAiService.extractTextFromImage(image);
        session.setExtractedLog(extractedText); // 분석 로그 DB 기록

        // 2. 추출된 텍스트 기반 키워드 매칭 검증
        boolean isPassed = visionAiService.validateKeyword(session.getMissionId(), extractedText);

        if (isPassed) {
            session.setStatus("PHOTO_VERIFIED");
            sessionRepository.save(session);
            return ResponseEntity.ok("인증 성공! AI 채팅 채널을 엽니다.");
        } else {
            return ResponseEntity.badRequest().body("안내판의 글귀가 잘 보이지 않습니다. 다시 촬영해주세요.");
        }
    }

    /**
     * [API 3: 최종 정답 검증 및 AI 실시간 대화 스트리밍]
     * - 수행 내용: 유저의 답변을 검증하고, Gemini AI를 통해 실시간 피드백을 전달함
     * - 상태 변경: 정답 시 "CLEARED" (최종 클리어)
     * - 매개 변수:
     * 1. Long sessionId (현재 게임 세션 번호)
     * 2. ChatRequest request (유저가 입력한 정답 문자열)
     * - 반환 값: ResponseBodyEmitter (실시간 텍스트 스트리밍 객체)
     */
    @PostMapping("/{sessionId}/chat/stream")
    public ResponseBodyEmitter streamAnswer(
            @PathVariable Long sessionId,
            @RequestBody ChatRequest request) {

        // 1. 세션 데이터 조회
        GameSession session = sessionRepository.findById(sessionId).orElseThrow();

        // 2. GeminiAiService를 통한 최종 정답 여부 판별
        boolean isCorrect = geminiAiService.verifyFinalAnswer(session.getMissionId(), request.getUserAnswer());

        // 3. 정답 일치 시 해당 세션을 완료 상태로 전환
        if (isCorrect) {
            session.setStatus("CLEARED");
            sessionRepository.save(session);
        }

        // 4. 비동기 스트리밍 방식으로 지휘관의 대사 전송 (타자기 효과 지원)
        return geminiAiService.streamNarration(session.getMissionId(), request.getUserAnswer(), isCorrect);
    }
}

/**
 * [DTO: 채팅 요청 데이터 규격]
 * - 용도: 프론트엔드에서 제출한 유저 답변 전달
 */
@Data
class ChatRequest {
    /** 사용자가 입력한 최종 정답 키워드 또는 질문 */
    private String userAnswer;
}