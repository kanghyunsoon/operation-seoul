package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();

        if (answerKeyword == null || answerKeyword.trim().isEmpty()) {
            return false;
        }

        String cleanUserAnswer = userAnswer.replace(" ", "").toLowerCase();
        String cleanAnswerKeyword = answerKeyword.replace(" ", "").toLowerCase();
        return cleanUserAnswer.contains(cleanAnswerKeyword);
    }

    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(60000L);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:streamGenerateContent?alt=sse";

        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String safeKeyword = (answerKeyword != null && !answerKeyword.isEmpty()) ? answerKeyword : "비밀 암호";

        String prompt;
        if (isCorrect) {
            prompt = "너는 본부 AI 지휘관이다. 요원이 암호(" + safeKeyword + ")를 정확히 맞췄다. '임무 완료! 훌륭하다 요원.'으로 시작하는 짧고 강렬한 칭찬 대사를 해라.";
        } else {
            String safeUserAnswer = userAnswer.replace("\"", "'").replace("\n", " ");
            prompt = String.format(
                    "너는 작전을 지휘하는 '본부 AI 지휘관'이다. 요원이 질문을 던졌다. 정답 키워드는 [%s]이다. 4가지 문장 중 하나로만 극도로 짧게 대답하라. 1. 그렇다. 2. 아니다. 잘못된 접근이다. 3. 작전과 무관하다. 4. 예리하다. 그것이 핵심 단서다. 요원의 통신: %s",
                    safeKeyword, safeUserAnswer
            );
        }

        String requestBody;
        try {
            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> parts = Map.of("parts", List.of(textPart));
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("contents", List.of(parts));
            requestBody = objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw new RuntimeException("JSON 조립 실패", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        try {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (!data.equals("[DONE]")) {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode candidates = node.path("candidates");
                                    if (!candidates.isMissingNode() && candidates.isArray() && candidates.size() > 0) {
                                        String textChunk = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                                        emitter.send(textChunk);
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    });
                    emitter.complete();
                });
        return emitter;
    }

    public String generateDynamicMissions(List<Map<String, String>> spots) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent";

        // 🚨 유실되었던 clue와 answerKeyword 필드 완벽 복구!
        String prompt = "너는 'Operation: SEOUL' 작전 본부의 특수 AI 통제관이다. " +
                "다음 제공된 실제 관광지 데이터를 바탕으로 비밀요원이 수행할 방탈출 작전을 기획해라.\n\n" +
                "[수집된 관광지 데이터]: " + spots.toString() + "\n\n" +
                "요원은 이 장소들을 순서대로 거쳐 힌트를 얻고, 최종 목적지에 도달해야 한다. " +
                "목적지의 실제 이름을 직접 말하지 말고, 일제강점기나 독립운동 등 역사적이고 비밀스러운 스토리로 브리핑을 작성해라.\n" +
                "또한, 각 경유지마다 다음 장소를 추리할 수 있는 단서(clue)를 제공하고, 마지막 미션에는 모든 단서를 조합해 풀 수 있는 최종 정답(answerKeyword)을 포함해라.\n" +
                "반드시 마크다운 포맷 없이, 오직 아래 형태의 순수한 JSON 객체 하나만 응답해라:\n" +
                "{\n" +
                "  \"regionName\": \"작전명: 정동길의 그림자\",\n" +
                "  \"regionDescription\": \"요원에게 하달하는 몰입감 있는 작전 브리핑\",\n" +
                "  \"missions\": [\n" +
                "    {\n" +
                "      \"title\": \"장소명 힌트\",\n" +
                "      \"lat\": 37.1234,\n" +
                "      \"lng\": 126.1234,\n" +
                "      \"visionKeyword\": \"간판글자\",\n" +
                "      \"isFinal\": false,\n" +
                "      \"clue\": \"이곳에 붉은 벽돌 뒤에 숨겨진 글자가 있다.\",\n" +
                "      \"answerKeyword\": \"대한독립\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String rawBody = response.getBody();

            if (rawBody == null) return null;

            rawBody = rawBody.trim();
            if (rawBody.startsWith("data:")) {
                rawBody = rawBody.substring(5).trim();
            }

            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isMissingNode() && candidates.isArray() && candidates.size() > 0) {
                String aiResponseText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                return aiResponseText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            System.err.println("🚨 Gemini 미션 생성 통신 실패: " + e.getMessage());
        }
        return null;
    }

    public String generateCourseWithTarget(Map<String, Object> targetSpot, List<Map<String, Object>> candidateSpots) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent";

        String prompt = "너는 'Operation: SEOUL' 작전 본부의 특수 AI 통제관이다.\n" +
                "최종 목적지는 [" + targetSpot.get("title") + "] 이다.\n" +
                "다음 제공된 주변 관광지 데이터에서 3곳의 경유지를 골라, 총 4개(경유지 3곳 + 최종 목적지 1곳)의 방탈출 작전 코스를 기획해라.\n\n" +
                "[주변 관광지 데이터]: " + candidateSpots.toString() + "\n\n" +
                "요원은 3곳의 경유지를 순서대로 거쳐 단서(clue)를 얻고, 마지막 4번째 미션에서 최종 목적지인 [" + targetSpot.get("title") + "]에 도달하여 '최종 정답'을 맞혀야 한다.\n" +
                "각 미션의 'isFinal' 값은 경유지는 false, 최종 목적지는 true 로 설정해라.\n" +
                "최종 목적지에는 모든 단서를 조합해 풀 수 있는 'answerKeyword'를 반드시 포함해라.\n\n" +
                "반드시 마크다운 포맷 없이, 오직 아래 형태의 순수한 JSON 객체 하나만 응답해라:\n" +
                "{\n" +
                "  \"regionName\": \"작전명: 붉은 노을의 비밀\",\n" +
                "  \"regionDescription\": \"요원에게 하달하는 몰입감 있는 작전 브리핑\",\n" +
                "  \"missions\": [\n" +
                "    {\n" +
                "      \"title\": \"경유지 1 이름\",\n" +
                "      \"lat\": 37.1234,\n" +
                "      \"lng\": 126.1234,\n" +
                "      \"visionKeyword\": \"간판글자\",\n" +
                "      \"isFinal\": false,\n" +
                "      \"clue\": \"다음 장소를 가리키는 힌트\",\n" +
                "      \"answerKeyword\": null\n" +
                "    },\n" +
                "    // ... 경유지 2, 3 추가 ...\n" +
                "    {\n" +
                "      \"title\": \"" + targetSpot.get("title") + "\",\n" +
                "      \"lat\": " + targetSpot.get("mapY") + ",\n" +
                "      \"lng\": " + targetSpot.get("mapX") + ",\n" +
                "      \"visionKeyword\": \"간판글자\",\n" +
                "      \"isFinal\": true,\n" +
                "      \"clue\": \"이곳이 마지막이다.\",\n" +
                "      \"answerKeyword\": \"최종정답\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String rawBody = response.getBody();

            if (rawBody == null) return null;

            rawBody = rawBody.trim();
            if (rawBody.startsWith("data:")) {
                rawBody = rawBody.substring(5).trim();
            }

            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isMissingNode() && candidates.isArray() && candidates.size() > 0) {
                String aiResponseText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                return aiResponseText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            System.err.println("🚨 Gemini 미션 생성 통신 실패: " + e.getMessage());
        }
        return null;
    }
}