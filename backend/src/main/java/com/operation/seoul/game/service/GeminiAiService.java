// backend/src/main/java/com/operation/seoul/game/service/GeminiAiService.java

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

    /**
     * 프롬프트 1: [핵심] 선택된 목적지 기반 작전 설계
     */
    public String generateCourseWithTarget(Map<String, Object> targetSpot, List<Map<String, Object>> subSpots) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();

        String prompt = "당신은 'Operation KOREA' 프로젝트의 수석 작전 기획자입니다.\n" +
                "이 프로젝트는 플레이어를 스파이로 몰입시켜 역사적 장소(랜드마크)와 주변 로컬 상권을 연결해 지역 경제를 활성화하는 것이 목적입니다.\n\n" +
                "📍 [작전 설계 데이터]\n" +
                "- 최종 목적지(역사적 장소): " + targetSpot + "\n" +
                "- 경유지 후보(로컬 상권 및 POI): " + subSpots + "\n\n" +
                "🚨 [작전 수립 절대 수칙]\n" +
                "1. 브리핑(regionDescription): 스파이 느와르 영화 도입부처럼 차갑고 비장하게 4문장 이상 작성하세요. 국가적 기밀이나 숨겨진 진실을 찾는 긴박함을 부여해야 합니다.\n" +
                "2. 장소명(title): '인근', '골목길', '근처', '벽면' 등 위치가 모호한 단어는 절대 금지! 제공된 데이터의 '공식 상호명/장소명'만 정확하게 사용하세요. 요원이 1m 오차 없이 찾아가야 합니다.\n" +
                "3. 인증 사물(visionKeyword): 🚨수식어 절대 금지! '고려삼계탕 한글 금속 간판'처럼 길게 쓰지 마세요. 무조건 '간판', '비석', '동상', '정문', '명판' 처럼 꾸밈없는 [1~2단어의 기본 명사]로만 작성하세요.\n" +
                "4. 진실 해금(clue): 사진 인증 성공 시, 단순 힌트가 아닌 해당 장소의 '진짜 역사적 가치'나 '숨겨진 스토리'를 스파이 톤으로 가공해 제공하세요.\n" +
                "5. 동선 구성: 경유지 후보 중 가장 매력적인 곳을 선택해 3~4개의 미션으로 구성하되, 마지막 미션은 반드시 제공된 '최종 목적지'여야 합니다.\n" +
                "6. 반환 형식: 아래 JSON 구조만 출력하고 마크다운 부가설명은 생략하세요.\n\n" +
                "{\n" +
                "  \"regionName\": \"작전명: [강렬한 작전 이름]\",\n" +
                "  \"regionDescription\": \"[고퀄리티 스파이 브리핑]\",\n" +
                "  \"missions\": [\n" +
                "    { \"title\": \"[공식명칭]\", \"lat\": [위도], \"lng\": [경도], \"visionKeyword\": \"[수식어 없는 단일 명사. 예: 간판, 비석]\", \"clue\": \"[역사적 진실이 담긴 힌트]\", \"isFinal\": false },\n" +
                "    { \"title\": \"[최종목적지 공식명]\", \"lat\": [위도], \"lng\": [경도], \"visionKeyword\": \"[수식어 없는 단일 명사. 예: 간판, 비석]\", \"answerKeyword\": \"[최종정답]\", \"isFinal\": true }\n" +
                "  ]\n" +
                "}";

        return callGeminiStandard(url, prompt);
    }

    /**
     * 프롬프트 2: 본부(HQ) 통신 나레이션 (스트리밍)
     */
    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(120000L);
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String missionTitle = mission.getTitle();

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();

        String prompt = String.format(
                "당신은 'Operation KOREA'의 본부(HQ) 지휘관입니다. 요원(플레이어)이 '%s' 현장에서 보고를 올렸습니다.\n" +
                        "요원 보고 내용: \"%s\"\n" +
                        "판독 결과: %b\n\n" +
                        "🚨 [HQ 통신 가이드라인]\n" +
                        "1. 성공(true) 시: '진실 해금(Truth Unlocked)' 원칙에 따라 이 장소가 가진 실제 역사적 중요성이나 로컬 가치를 요약 브리핑하세요. 그 후 '주변 상점에서 민간인으로 위장해 잠시 휴식(소비 유도)하며 다음 지령을 대기하라'고 지시하세요.\n" +
                        "2. 실패(false) 시: 차갑게 질책하며 '이동 가능한 사물에 속지 말고, 단단히 고정된 명판이나 표지석을 다시 스캔하라'고 단호하게 명령하세요.\n" +
                        "3. 말투: 철저하게 스파이 영화 속 지휘관 말투(하오체 또는 평어)를 유지하세요.",
                missionTitle, userAnswer, isCorrect
        );

        new Thread(() -> {
            try {
                String aiResponseText = callGeminiStandard(url, prompt);
                if (aiResponseText != null) {
                    for (char c : aiResponseText.toCharArray()) {
                        emitter.send(String.valueOf(c));
                        Thread.sleep(25);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 챗봇 답변 유사도 검증
     */
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if (answerKeyword == null) return true;

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();
        String prompt = String.format("정답: '%s', 요원답변: '%s'. 의미가 통하면 'TRUE', 틀리면 'FALSE'만 반환.", answerKeyword, userAnswer);

        String result = callGeminiStandard(url, prompt);
        return result != null && result.contains("TRUE");
    }

    private String callGeminiStandard(String url, String prompt) {
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            return text.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            log.error("🚨 Gemini 통신 실패: {}", e.getMessage());
            return null;
        }
    }
}