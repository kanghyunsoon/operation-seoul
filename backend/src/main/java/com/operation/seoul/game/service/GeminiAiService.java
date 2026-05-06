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

        // 👇 프롬프트 수정: visionKeyword에 대한 강력한 제약 및 예시 추가
        String prompt = "너는 'Operation: SEOUL' 작전 본부의 특수 AI 통제관이다. " +
                "다음 제공된 실제 장소 데이터를 바탕으로 비밀요원이 수행할 방탈출 작전을 기획해라.\n\n" +
                "[수집된 장소 데이터]: " + spots.toString() + "\n\n" +
                "요원은 이 장소들을 순서대로 거쳐 힌트를 얻고, 최종 목적지에 도달해야 한다. " +
                "목적지의 실제 이름을 직접 말하지 말고, 일제강점기나 독립운동 등 역사적이고 비밀스러운 스토리로 브리핑을 작성해라.\n" +
                "또한, 각 경유지마다 다음 장소를 추리할 수 있는 단서(clue)를 제공하고, 마지막 미션에는 모든 단서를 조합해 풀 수 있는 최종 정답(answerKeyword)을 포함해라.\n\n" +
                "🚨 [매우 중요한 규칙]: 'visionKeyword'는 플레이어가 현장에 가서 스마트폰 카메라로 찍었을 때, 비전 AI가 즉시 식별할 수 있는 **매우 구체적이고 시각적인 사물이나 텍스트**여야 한다.\n" +
                "❌ 나쁜 예시 (절대 금지): '대한문', '성공회 서울주방성당', '독립서점' (장소 이름이나 추상적인 개념)\n" +
                "✅ 좋은 예시 (필수 권장): '붉은색 벽돌 담장', '나무로 된 현판', '파란색 안내 표지판', '돌로 만든 십자가', '입구의 아치형 기둥', '가게 유리에 적힌 영업시간'\n\n" +
                "반드시 마크다운 포맷 없이, 오직 아래 형태의 순수한 JSON 객체 하나만 응답해라:\n" +
                "{\n" +
                "  \"regionName\": \"작전명: 정동길의 그림자\",\n" +
                "  \"regionDescription\": \"요원에게 하달하는 몰입감 있는 작전 브리핑\",\n" +
                "  \"missions\": [\n" +
                "    {\n" +
                "      \"title\": \"장소명 힌트\",\n" +
                "      \"lat\": 37.1234,\n" +
                "      \"lng\": 126.1234,\n" +
                "      \"visionKeyword\": \"붉은 벽돌 담장\",\n" +
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

        // 👇 프롬프트 수정: 다양한 장소 선택 유도 및 visionKeyword 제약 추가
        String prompt = "당신은 지역 상권 활성화를 위한 야외 방탈출 'Operation: SEOUL'의 작전 설계자입니다.\n" +
                "제공된 주변 장소 리스트에서 최종 목적지인 [" + targetSpot.get("title") + "]를 포함해 총 4곳의 동선을 짜주세요.\n\n" +
                "🚨 [미션 설계 핵심 원칙]\n" +
                "1. 난이도 조절: 사용자가 GPS를 보고 이미 해당 지점 근처에 와 있으므로, 너무 복잡한 수수께끼나 획수 계산 같은 문제는 피하세요. '무엇을 찾아서 찍으세요'라는 명확한 지시가 담긴 짧은 지문을 만드세요.\n" +
                "2. 장소의 다양성: 리스트에 있는 장소들 중 박물관 같은 큰 곳 외에도 '작은 카페', '책방', '골목길의 조형물' 등 골목 상권의 매력이 담긴 곳을 최소 1곳 이상 포함하세요.\n" +
                "3. visionKeyword (매우 중요): 사진 판독을 위해 '장소 이름'이나 '추상적 개념'을 절대 쓰지 마세요. \n" +
                "   - 나쁜 예: '대한문', '성공회 성당', '역사적 가치'\n" +
                "   - 좋은 예: '입구 옆 빨간색 우체통', '건물 외벽의 금색 현판', '가게 앞 둥근 간판', '성당 담장의 돌 십자가'\n" +
                "4. 스토리텔링: 요원이 비밀리에 정보를 수집하는 분위기로 'clue'(단서)와 'regionDescription'을 작성하세요.\n\n" +
                "반드시 마크다운 없이 순수 JSON 객체로만 응답하세요.\n" +
                "{\n" +
                "  \"regionName\": \"작전명: 붉은 노을의 비밀\",\n" +
                "  \"regionDescription\": \"요원에게 하달하는 몰입감 있는 작전 브리핑\",\n" +
                "  \"missions\": [\n" +
                "    {\n" +
                "      \"title\": \"경유지 1 이름\",\n" +
                "      \"lat\": 37.1234,\n" +
                "      \"lng\": 126.1234,\n" +
                "      \"visionKeyword\": \"둥근 간판\",\n" +
                "      \"isFinal\": false,\n" +
                "      \"clue\": \"다음 장소를 가리키는 힌트\",\n" +
                "      \"answerKeyword\": null\n" +
                "    },\n" +
                "    // ... 경유지 2, 3 추가 ...\n" +
                "    {\n" +
                "      \"title\": \"" + targetSpot.get("title") + "\",\n" +
                "      \"lat\": " + targetSpot.get("mapY") + ",\n" +
                "      \"lng\": " + targetSpot.get("mapX") + ",\n" +
                "      \"visionKeyword\": \"나무 현판\",\n" +
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