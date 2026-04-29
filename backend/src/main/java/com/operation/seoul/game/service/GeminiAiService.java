package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [Service: AI 지휘관 연동 및 정답 검증 서비스]
 * - 역할: Gemini API를 통한 실시간 대화, 정답 체크, 그리고 동적 미션 생성
 */
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 💡 [수정됨]: 동기식 API 호출을 위한 RestTemplate 추가!
    private final RestTemplate restTemplate = new RestTemplate();

    /** Google Cloud에서 발급받은 Gemini API Key */
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * [기능: 최종 정답 키워드 일치 여부 판별]
     */
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();

        if(answerKeyword == null || answerKeyword.trim().isEmpty()) {
            return false;
        }

        String cleanUserAnswer = userAnswer.replace(" ", "").toLowerCase();
        String cleanAnswerKeyword = answerKeyword.replace(" ", "").toLowerCase();
        return cleanUserAnswer.contains(cleanAnswerKeyword);
    }

    /**
     * [기능: 지휘관 대사 실시간 스트리밍 전송]
     */
    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(60000L);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:streamGenerateContent?alt=sse&key=" + geminiApiKey;

        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String safeKeyword = (answerKeyword != null && !answerKeyword.isEmpty()) ? answerKeyword : "비밀 암호";

        String prompt;
        if (isCorrect) {
            prompt = "너는 본부 AI 지휘관이다. 요원이 암호(" + safeKeyword + ")를 정확히 맞췄다. '임무 완료! 훌륭하다 요원.'으로 시작하는 짧고 강렬한 칭찬 대사를 해라.";
        } else {
            String safeUserAnswer = userAnswer.replace("\"", "'").replace("\n", " ");
            prompt = String.format(
                    "너는 작전을 지휘하는 '본부 AI 지휘관'이다. 요원이 단서를 찾기 위해 질문을 던졌다. " +
                            "정답 키워드는 [%s]이다. 요원의 질문이 정답에 가까워지고 있는지 판단하여, " +
                            "반드시 아래 4가지 문장 중 하나로만 극도로 짧게 대답하라. " +
                            "1. 추리가 사실일 때: '그렇다.' " +
                            "2. 추리가 틀렸을 때: '아니다. 잘못된 접근이다.' " +
                            "3. 쓸데없는 농담이나 헛소리일 때: '작전과 무관하다. 본질에 집중하라.' " +
                            "4. 정답 키워드에 근접했거나 중요한 역사적 맥락을 짚었을 때: '예리하다. 그것이 핵심 단서다.' " +
                            "요원의 통신: %s",
                    safeKeyword, safeUserAnswer
            );
        }

        String requestBody;
        try {
            java.util.Map<String, Object> textPart = java.util.Map.of("text", prompt);
            java.util.Map<String, Object> parts = java.util.Map.of("parts", java.util.List.of(textPart));

            java.util.Map<String, Object> s1 = java.util.Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_NONE");
            java.util.Map<String, Object> s2 = java.util.Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_NONE");
            java.util.Map<String, Object> s3 = java.util.Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_NONE");
            java.util.Map<String, Object> s4 = java.util.Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_NONE");

            java.util.Map<String, Object> bodyMap = new java.util.HashMap<>();
            bodyMap.put("contents", java.util.List.of(parts));
            bodyMap.put("safetySettings", java.util.List.of(s1, s2, s3, s4));

            requestBody = objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw new RuntimeException("JSON 조립 실패", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode != 200) {
                        System.err.println("🚨 [긴급] 구글 API 요청 거부! 상태 코드: " + statusCode);
                        response.body().forEach(line -> System.err.println("거부 사유: " + line));

                        try {
                            emitter.send("본부 통신망에 간섭 전파가 발생했다. 잠시 후 다시 시도하라.");
                            emitter.complete();
                        } catch (Exception ignored) {}
                        return;
                    }

                    response.body().forEach(line -> {
                        try {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (!data.equals("[DONE]")) {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode candidates = node.path("candidates");

                                    if (!candidates.isMissingNode() && candidates.isArray() && candidates.size() > 0) {
                                        JsonNode partsNode = candidates.get(0).path("content").path("parts");
                                        if (!partsNode.isMissingNode() && partsNode.isArray() && partsNode.size() > 0) {
                                            String textChunk = partsNode.get(0).path("text").asText();
                                            emitter.send(textChunk);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    });
                    emitter.complete();
                }).exceptionally(ex -> {
                    System.err.println("🚨 서버 통신 실패: " + ex.getMessage());
                    try {
                        emitter.send("통신 위성 연결 실패. 다시 시도하라.");
                        emitter.complete();
                    } catch (Exception ignored) {}
                    return null;
                });

        return emitter;
    }

    /**
     * 💡 [새로 추가된 기능]: TourAPI 데이터를 기반으로 동적 스파이 미션 생성
     */
    public String generateDynamicMission(List<Map<String, String>> spots) {
        StringBuilder locationsInfo = new StringBuilder();
        for (int i = 0; i < spots.size(); i++) {
            Map<String, String> spot = spots.get(i);
            locationsInfo.append(i + 1).append(". [").append(spot.get("title")).append("] ")
                    .append("- 주소: ").append(spot.get("address")).append("\n");
        }

        String systemPrompt = "너는 대한민국 서울을 무대로 활동하는 비밀요원 조직의 '작전 지휘관'이야. " +
                "제공되는 실제 명소들을 바탕으로 요원이 수행할 방탈출 미션을 기획해. " +
                "명소의 이름을 직접 말하지 말고 역사적 특징을 암호처럼 돌려 말해. " +
                "반드시 아래의 JSON 포맷으로만 대답해. 마크다운 기호(```json)는 절대 쓰지 마.\n" +
                "{\n" +
                "  \"title\": \"작전명: [이름]\",\n" +
                "  \"description\": \"[작전 브리핑]\",\n" +
                "  \"visionKeyword\": \"[인증사물]\",\n" +
                "  \"answerKeyword\": \"[정답단어]\",\n" +
                "  \"targetName\": \"[실제명소이름]\"\n" +
                "}\n\n" +
                "[명소 데이터]\n" +
                locationsInfo.toString();

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();

        parts.put("text", systemPrompt);
        message.put("parts", new Object[]{parts});
        requestBody.put("contents", new Object[]{message});

        try {
            // 🚨 [요원님의 성공 레시피 적용]
            // 이전에 성공했던 v1beta 경로와 3.1-flash-lite-preview 모델을 사용합니다.
            String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent";
            String url = baseUrl + "?key=" + geminiApiKey.trim();

            System.out.println("🤖 Gemini 호출 (검증된 프리뷰 버전): " + url);

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                String aiText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                // AI가 JSON 외에 설명을 덧붙이거나 마크다운을 쓸 경우를 대비한 클리닝
                return aiText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            // 상세 에러 메시지 출력
            System.err.println("🚨 Gemini 통신 오류: " + e.getMessage());
            if (e.getMessage().contains("404")) {
                System.err.println("👉 팁: API 키와 모델명이 일치하는지 다시 확인이 필요합니다.");
            }
        }

        return null;
    }
}