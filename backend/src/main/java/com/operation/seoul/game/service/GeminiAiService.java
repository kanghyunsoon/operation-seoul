package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

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
                    // 🚀 수정 포인트 2: 구글 API가 에러(200 아님)를 뱉으면 화면이 꺼지지 않게 에러 메시지를 쏴줌!
                    int statusCode = response.statusCode();
                    if (statusCode != 200) {
                        System.err.println("🚨 [긴급] 구글 API 요청 거부! 상태 코드: " + statusCode);
                        response.body().forEach(line -> System.err.println("거부 사유: " + line)); // 에러 사유 상세 출력

                        try {
                            emitter.send("본부 통신망에 알 수 없는 노이즈가 발생했다. 질문을 바꿔서 다시 시도하라.");
                            emitter.complete();
                        } catch (Exception ignored) {}
                        return; // 스트리밍 종료
                    }

                    // 정상 동작 (200 OK)
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
}