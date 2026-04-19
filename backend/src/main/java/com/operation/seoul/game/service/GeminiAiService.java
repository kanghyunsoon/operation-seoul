package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * [Service: 채팅 퀴즈 및 스토리텔링 엔진]
 */
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final RestTemplate restTemplate = new RestTemplate(); // HTTP 통신 객체

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 1. 최종 정답 검증기 (기존과 동일)
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if(answerKeyword == null || answerKeyword.isEmpty()) return true;
        String cleanUserAnswer = userAnswer.replace(" ", "").toLowerCase();
        String cleanAnswerKeyword = answerKeyword.replace(" ", "").toLowerCase();
        return cleanUserAnswer.contains(cleanAnswerKeyword);
    }

    // 2. 찐(Real) Gemini API 대사 생성기
    public String generateNarration(boolean isCorrect) {
        // 구글 Gemini API 엔드포인트

        // v1beta가 아니라 v1으로, 그리고 모델명을 gemini-1.5-flash로 정확히 지정합니다.
        // v1beta 주소를 쓰되 모델명을 gemini-1.5-flash로 명확히 타격합니다.
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        // AI에게 내릴 명령(Prompt) 세팅
        String prompt = isCorrect ?
                "너는 비밀 작전을 수행하는 요원들의 지휘관이야. 유저가 방탈출 미션 정답을 맞췄어. '훌륭하다 요원.' 으로 시작하는 몰입감 있는 칭찬 대사를 2문장으로 해줘." :
                "너는 비밀 작전을 수행하는 요원들의 지휘관이야. 유저가 미션 정답을 틀렸어. 단서를 다시 확인해보라고 냉철하지만 조력자 느낌으로 2문장으로 말해줘.";

        // JSON 바디 만들기 (Map 이용)
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            // 1. 구글 서버로 POST 요청 (JsonNode 대신 Map.class 사용)
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            // 2. Map을 이용해 구글의 응답에서 텍스트만 쏙 빼오기
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            Map<String, Object> contentMap = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");

            String aiMessage = (String) parts.get(0).get("text");
            System.out.println("🤖 지휘관의 통신: " + aiMessage); // 콘솔에서 확인용

            return aiMessage;

        } catch (Exception e) {
            System.err.println("Gemini API 호출 에러: " + e.getMessage());
            return "통신 상태가 고르지 않다. 요원, 다시 한번 말해달라.";
        }
    }
}