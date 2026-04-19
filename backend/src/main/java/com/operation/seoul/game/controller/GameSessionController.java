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

@RestController
@RequestMapping("/api/v1/sessions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionRepository sessionRepository;
    private final VisionAiService visionAiService;
    private final GeminiAiService geminiAiService;

    /**
     * [API 1] 미션 시작 (Location 모듈에서 도착 성공 시 호출됨)
     * 용도: 유저가 30m 반경에 들어오면 세이브 파일을 만들고 상태를 "ARRIVED"로 저장.
     */
    @PostMapping("/start/{missionId}")
    public ResponseEntity<Long> startGameSession(@PathVariable Long missionId) {
        Long userId = 1L; // 임시 유저 ID

        // 이미 진행 중인 세션이 있다면 가져오고, 없으면 새로 만듭니다.
        GameSession session = sessionRepository.findByUserIdAndMissionId(userId, missionId)
                .orElse(new GameSession());

        session.setUserId(userId);
        session.setMissionId(missionId);
        session.setStatus("ARRIVED"); // 상태 업데이트!

        sessionRepository.save(session);

        // 프론트엔드에게 세션 번호를 알려줍니다.
        return ResponseEntity.ok(session.getId());
    }

    /**
     * [API 2] 사진 촬영 및 Vision AI 검증
     * 용도: 찰칵! 하고 사진을 보내면 OCR을 돌려 통과 시 상태를 "PHOTO_VERIFIED"로 승급.
     */
    @PostMapping("/{sessionId}/vision")
    public ResponseEntity<String> verifyVision(
            @PathVariable Long sessionId,
            @RequestParam("image") MultipartFile image) {

        GameSession session = sessionRepository.findById(sessionId).orElseThrow();

        // 1. 서비스 호출: 사진에서 글씨 뽑기
        String extractedText = visionAiService.extractTextFromImage(image);
        session.setExtractedLog(extractedText); // 로그 저장

        // 2. 서비스 호출: 키워드 검증
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
     * [API 3] 최종 정답 제출 및 AI 채팅 대화
     * 용도: 타자기 효과용 텍스트를 검증하고 미션을 "CLEARED" 처리.
     */
    /**
     * [API 3] 최종 정답 제출 및 AI 실시간 스트리밍 대화
     * 용도: 한 글자씩 생성되는 지휘관의 대사를 실시간으로 프론트엔드에 전달합니다.
     */
    @PostMapping("/{sessionId}/chat/stream") // 주소를 /chat/stream으로 변경하여 명시성을 높임
    public ResponseBodyEmitter streamAnswer(
            @PathVariable Long sessionId,
            @RequestBody ChatRequest request) {

        // 1. DB에서 세션 정보 가져오기
        GameSession session = sessionRepository.findById(sessionId).orElseThrow();

        // 2. 서비스 호출: 정답 확인 (이 로직은 동기적으로 빠르게 처리)
        boolean isCorrect = geminiAiService.verifyFinalAnswer(session.getMissionId(), request.getUserAnswer());

        // 3. 정답이면 DB 상태 업데이트
        if (isCorrect) {
            session.setStatus("CLEARED");
            sessionRepository.save(session);
        }

        // 4. 🔥 핵심: 한 글자씩 쏴주는 스트리밍 서비스 호출 (ResponseBodyEmitter 반환)
        // GeminiAiService에 우리가 미리 만들어둔 streamNarration을 호출합니다.
        return geminiAiService.streamNarration(isCorrect);
    }
}

/**
 * 프론트에서 보내는 JSON {"userAnswer": "헤이그 특사"} 를 받기 위한 그릇
 */
@Data
class ChatRequest {
    private String userAnswer;
}