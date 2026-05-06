package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 🚨 수칙 2번 준수: 최신 모델명으로 고정
    private static final String GEMINI_MODEL = "gemini-3.1-flash-lite-preview";

    /**
     * [개선] AI 기반 정답 검증 로직
     * 단순 contains 비교가 아니라 유저 대답의 '의미'를 파악하여 정답을 유연하게 인정합니다.
     */
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();

        if (answerKeyword == null || answerKeyword.trim().isEmpty()) {
            return false;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";

        String prompt = String.format(
                "너는 보안 정답 검증 시스템이다. 아래 정답 키워드와 요원의 대답을 비교해라.\n" +
                        "정답 키워드: '%s'\n" +
                        "요원의 대답: '%s'\n" +
                        "요원이 정답의 핵심 의미를 맞췄다면 오직 'TRUE', 틀렸다면 'FALSE'라고만 답해라.",
                answerKeyword, userAnswer
        );

        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String result = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
            return result.equalsIgnoreCase("TRUE");
        } catch (Exception e) {
            log.error("🚨 정답 AI 검증 실패 (단순 비교로 전환): {}", e.getMessage());
            // API 통신 에러 시, 기존의 안전한 단순 문자열 비교 방식으로 폴백(Fallback) 처리
            return userAnswer.replace(" ", "").toLowerCase().contains(answerKeyword.replace(" ", "").toLowerCase());
        }
    }

    /**
     * [개선] SSE 스트리밍 지문 생성 로직
     * 프론트엔드의 EventSource 규격(data: 내용\n\n)을 준수하도록 포맷팅을 수정했습니다.
     */
    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(60000L);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":streamGenerateContent?alt=sse";

        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String safeUserAnswer = userAnswer != null ? userAnswer.replace("\"", "'").replace("\n", " ") : "";
        String prompt;

        if (isCorrect) {
            prompt = String.format("너는 본부 AI 지휘관이다. 요원이 정답을 맞췄다. '%s' 장소의 역사적 의의를 섞어 짧고 강렬한 칭찬 대사를 해라.", mission.getTitle());
        } else {
            prompt = String.format("너는 본부 AI 지휘관이다. 요원이 오답(%s)을 제출했다. 단호하게 꾸짖고 다시 시도하라고 명령해라. 짧게 2문장 내외.", safeUserAnswer);
        }

        try {
            Map<String, Object> bodyMap = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            String requestBody = objectMapper.writeValueAsString(bodyMap);

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
                                            // 프론트엔드 수신을 위해 표준 SSE 포맷으로 전송
                                            emitter.send("data: " + textChunk + "\n\n");
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
                        try {
                            emitter.send("data: [DONE]\n\n");
                            emitter.complete();
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            log.error("🚨 SSE 전송 설정 실패: ", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * [유지] 기존 동적 미션 생성 로직 (누락 복구)
     * 프롬프트: visionKeyword에 대한 강력한 제약 및 예시 포함
     */
    public String generateDynamicMissions(List<Map<String, String>> spots) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";
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

        return callGeminiStandard(url, prompt);
    }

    /**
     * [유지] 기존 목적지 기반 코스 생성 로직 (누락 복구 및 기존 파라미터 타입 유지)
     */
    public String generateCourseWithTarget(Map<String, Object> targetSpot, List<Map<String, Object>> candidateSpots) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";
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

        return callGeminiStandard(url, prompt);
    }

    /**
     * 공통 API 호출 헬퍼 메서드 (중복 코드 제거)
     */
    private String callGeminiStandard(String url, String prompt) {
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");

            if (!candidates.isMissingNode() && candidates.isArray() && candidates.size() > 0) {
                String aiResponseText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                return aiResponseText.replace("```json", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            log.error("🚨 Gemini API 생성 통신 실패: {}", e.getMessage());
        }
        return null;
    }
}