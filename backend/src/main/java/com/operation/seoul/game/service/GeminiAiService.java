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
                "1. 먼저 최종 목적지와 강하게 연결되는 역사적 사건, 인물, 유명 일화, 도시 전설 중 하나를 정하고 answerKeyword는 장소명이 아니라 그 사건/인물/일화의 핵심어로 작성하세요.\n" +
                "2. 브리핑(regionDescription): 최종 목적지의 이름을 바로 노출하지 말고, 사건의 긴장감과 조사해야 할 이유를 스파이 느와르 톤으로 4문장 이상 작성하세요.\n" +
                "3. 장소명(title): '인근', '골목길', '근처', '벽면' 등 위치가 모호한 단어는 절대 금지! 제공된 데이터의 '공식 상호명/장소명'만 정확하게 사용하세요.\n" +
                "4. 인증 사물(visionKeyword): 장소에 도착해도 바로 찾기 어렵도록 너무 일반적인 '간판'만 반복하지 말고, 현장에서 관찰 가능한 '명판', '문양', '기둥', '동상', '표지석', '현판' 같은 1~2단어 사물명으로 섞어 작성하세요.\n" +
                "5. 진실 해금(clue): 각 힌트 목적지의 단서는 최종 목적지와 최종 사건을 동시에 암시하되, 정답을 직접 말하지 마세요. 플레이어가 여러 단서를 합쳐 사건을 유추해야 합니다.\n" +
                "6. 동선 구성: 경유지 후보 중 가장 매력적인 곳을 선택해 4개의 미션으로 구성하되, 마지막 미션은 반드시 제공된 '최종 목적지'여야 합니다.\n" +
                "7. 반환 형식: 아래 JSON 구조만 출력하고 마크다운 부가설명은 생략하세요.\n\n" +
                "{\n" +
                "  \"regionName\": \"작전명: [강렬한 작전 이름]\",\n" +
                "  \"regionDescription\": \"[고퀄리티 스파이 브리핑]\",\n" +
                "  \"missions\": [\n" +
                "    { \"title\": \"[공식명칭]\", \"lat\": [위도], \"lng\": [경도], \"visionKeyword\": \"[현장 관찰 사물명]\", \"clue\": \"[최종 장소와 사건을 모호하게 암시하는 단서]\", \"isFinal\": false },\n" +
                "    { \"title\": \"[최종목적지 공식명]\", \"lat\": [위도], \"lng\": [경도], \"visionKeyword\": \"현장탐색\", \"answerKeyword\": \"[장소명이 아닌 사건/인물/일화 핵심어]\", \"isFinal\": true }\n" +
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
                "당신은 'Operation KOREA'의 HQ 안내관입니다. 요원은 '%s' 현장에서 다음 보고를 보냈습니다.\n" +
                        "요원 보고: \"%s\"\n" +
                        "판정: %b\n\n" +
                        "[응답 규칙]\n" +
                        "- 한국어로 2문장 이하, 120자 이하로 답하세요.\n" +
                        "- 모욕, 조롱, 인신공격, 과한 질책은 금지합니다.\n" +
                        "- 성공이면 정답 확인만 짧게 알리고, 상세 역사 해설은 임무 종료 기록에서 제공된다고 말하세요.\n" +
                        "- 실패이면 관찰이 부족하다고만 정중히 알리고, 수집한 단서를 다시 대조하라고 안내하세요.\n" +
                        "- 마크다운 제목, 대괄호 장식, 긴 서론은 쓰지 마세요.",
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

    public ResponseBodyEmitter streamHintAnswer(Long missionId, String userQuestion) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();

        String prompt = String.format(
                "당신은 역사 추리 게임의 정중한 힌트 진행자입니다.\n" +
                        "정답 키워드: '%s'\n" +
                        "플레이어 질문: '%s'\n\n" +
                        "[응답 규칙]\n" +
                        "- 한국어로 2문장 이하, 140자 이하로 답하세요.\n" +
                        "- 질문 내용이 정답과 얼마나 가까운지 '관련 있음/부분적으로 관련 있음/거리가 있음' 중 하나로 알려주세요.\n" +
                        "- 정답 키워드 자체는 절대 말하지 마세요.\n" +
                        "- 관련 인물, 장소, 시대 배경은 힌트로 언급해도 되지만 결론을 직접 확정하지 마세요.\n" +
                        "- 모욕, 조롱, 과한 질책, 긴 역사 해설은 금지합니다.",
                answerKeyword, userQuestion
        );

        return streamPrompt(url, prompt);
    }

    public boolean isHintQuestion(String userAnswer) {
        if (userAnswer == null) return false;
        String trimmed = userAnswer.trim();
        return trimmed.endsWith("?")
                || trimmed.contains("관련")
                || trimmed.contains("맞아")
                || trimmed.contains("인가")
                || trimmed.contains("일까")
                || trimmed.contains("힌트");
    }

    public Map<String, Object> generateClearReport(Long missionId) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String realStory = mission.getRealStory();

        String report = realStory;
        if (report == null || report.isBlank()) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();
            String prompt = String.format(
                    "당신은 역사 추리 게임의 클리어 화면에 들어갈 해설을 쓰는 역사 해설자입니다.\n" +
                            "장소: %s\n" +
                            "핵심 키워드: %s\n\n" +
                            "[작성 규칙]\n" +
                            "- 한국어로 10문장 이상 14문장 이하로 작성하세요.\n" +
                            "- 문장마다 빈 줄을 넣어 모바일 화면에서도 읽기 쉽게 나누세요.\n" +
                            "- 플레이어가 방금 추리를 끝낸 뒤 읽는 글처럼, 단서를 따라오며 몰입했던 경험을 실제 역사와 연결해 흥미롭게 풀어 쓰세요.\n" +
                            "- 논문식 요약이나 백과사전식 정의가 아니라, 사건이 왜 벌어졌고 현장에서 무엇을 떠올리면 좋은지 이야기하듯 설명하세요.\n" +
                            "- 첩보/픽션 말투를 쓰지 말고, 실제 역사 해설처럼 정중하게 설명하세요.\n" +
                            "- 역사적 사실로 널리 알려진 내용만 말하세요.\n" +
                            "- 확실하지 않은 세부사항은 단정하지 말고 '추가 확인이 필요합니다'라고 쓰세요.\n" +
                            "- 마크다운 제목이나 목록은 쓰지 마세요.\n" +
                            "- 마지막 문장은 이 사건을 현재 장소 탐방과 연결해 마무리하세요.",
                    mission.getTitle(), answerKeyword
            );
            report = callGeminiStandard(url, prompt);
        }

        if (report == null || report.isBlank()) {
            report = "임무는 완료되었습니다. 이 장소와 핵심 키워드의 상세 역사 해설은 공공데이터 기반 기록 보강 단계에서 제공될 예정입니다.";
        }

        return Map.of(
                "missionId", mission.getId(),
                "title", mission.getTitle(),
                "answerKeyword", answerKeyword == null ? "" : answerKeyword,
                "report", report
        );
    }

    /**
     * 챗봇 답변 유사도 검증
     */
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if (answerKeyword == null) return true;

        String normalizedAnswer = normalizeAnswer(userAnswer);
        String normalizedKeyword = normalizeAnswer(answerKeyword);
        if (normalizedAnswer.equals(normalizedKeyword) || normalizedAnswer.contains(normalizedKeyword)) {
            return true;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey.trim();
        String prompt = String.format(
                "정답 키워드: '%s'\n요원 답변: '%s'\n" +
                        "판정 기준: 답변이 정답 키워드 자체이거나, 같은 역사적 사건/일화의 공식 명칭을 명확히 말한 경우만 TRUE입니다. " +
                        "관련 인물, 장소, 시대 배경만 말한 것은 FALSE입니다. 예: 정답이 '아관파천'이면 '고종', '러시아 공사관', '덕수궁'은 FALSE입니다. " +
                        "오직 TRUE 또는 FALSE만 반환하세요.",
                answerKeyword, userAnswer
        );

        String result = callGeminiStandard(url, prompt);
        return result != null && "TRUE".equalsIgnoreCase(result.trim());
    }

    private String normalizeAnswer(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
    }

    private ResponseBodyEmitter streamPrompt(String url, String prompt) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(120000L);

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
