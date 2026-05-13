package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public String generateCourseWithTarget(Map<String, Object> targetSpot, List<Map<String, Object>> subSpots) {
        String url = geminiUrl();
        String prompt = """
                당신은 Operation KOREA의 현장형 역사 추리 게임 작전 기획자입니다.

                [입력 데이터]
                - 최종 목적지: %s
                - 힌트 후보 POI: %s

                [생성 규칙]
                1. 최종 목적지와 강하게 연결되는 실제 역사 사건, 공식 사건명, 유명한 일화 중 하나를 정답으로 정하세요.
                   answerKeyword는 장소명, 인물명, 건물명이 아니라 사건/일화/역사적 행위의 압축 키워드여야 합니다.
                   좋은 예: "아관파천", "을사늑약", "대한제국 선포", "임오군란"
                   나쁜 예: "덕수궁", "고종", "광화문", "러시아공사관", "숭례문"
                   answerKeyword는 최종 채팅에서 맞혀야 하는 비밀 정답입니다. regionName, regionDescription, clue에는 절대 직접 쓰지 마세요.

                2. 힌트 미션 3개와 최종 미션 1개, 총 4개만 만드세요.
                   힌트 미션 title/lat/lng는 제공된 힌트 후보 POI의 공식 명칭과 좌표를 사용하세요.
                   최종 미션은 반드시 제공된 최종 목적지의 공식 명칭과 좌표를 사용하세요.

                3. 전체 작전은 방탈출 narrative scenario 형식으로 설계하세요.
                   먼저 세팅, 주인공, 목표, 플롯을 내부적으로 정리한 뒤 결과에는 자연스러운 스토리만 쓰세요.
                   예: 닫힌 교실, 잠긴 노트북, 스케줄러의 날짜, 프로젝터에 겹쳐지는 기호처럼 장소와 사물이 사건을 밀어내는 구조입니다.
                   Operation KOREA의 테마는 역사 추리, 현장 잠입, 봉인된 기록 복원입니다.
                   재미 요소는 Discovery/Exploration, Challenge, Thrill/Sensation을 섞고, 기능 설명이나 플레이 방법 설명은 쓰지 마세요.

                4. regionDescription은 브리핑 화면에 그대로 표시할 "전체 스토리"입니다.
                   지역명, 마커, 좌표, TourAPI, 사진 촬영, AI 채팅, 이동 방법 같은 시스템 설명은 쓰지 마세요.
                   하라체로 5~7문장 작성하세요. 문장은 지시하듯 단호해야 하지만 내용은 스토리라인이어야 합니다.
                   정답 단어와 최종 목적지명은 직접 쓰지 마세요.

                5. 각 미션의 description은 해당 장소에 붙는 짧은 narrative scenario 조각입니다.
                   "무엇을 하라"는 사용법이 아니라, 그 장소에 남은 장면/사물/소리/기록이 다음 의문으로 이어지는 식으로 2~3문장 작성하세요.
                   하라체를 사용하고, 번호 목록이나 단계 설명은 쓰지 마세요.

                6. 각 힌트 clue는 예전 추리형 단서와 현재 방탈출 장면 단서를 섞어 작성하세요.
                   1~2문장, 90자 내외로 작성하고 하라체를 사용하세요.
                   첫 축은 해당 힌트 장소에서 보일 법한 사물, 표식, 소리, 문장, 색, 연도 같은 방탈출식 장면 단서입니다.
                   두 번째 축은 최종 목적지의 분위기와 answerKeyword 사건의 정황(권력 이동, 외세 압박, 봉인된 선언, 사라진 이름 등)을 아주 모호하게 암시하는 역사 추리 단서입니다.
                   정답 단어, 최종 목적지명, 직접 이동/촬영/확인 지시는 쓰지 마세요.
                   너무 추상적인 침묵만 반복하지 말고, 장소성 단서와 사건성 단서를 한 문장 안에서 연결하세요.

                7. final mission의 visionKeyword는 사진 인증용이 아니라 현장에서 찾아볼 만한 단서 대상입니다.
                   예: "현판", "비석", "문양", "동상", "기둥", "안내판", "문"
                   final mission의 clue에는 최종 사건을 열기 직전의 마지막 혼합형 힌트만 남기세요.
                   visionKeyword 대상의 물리적 흔적과 사건의 정황을 함께 숨기고, answerKeyword를 직접 쓰지 마세요.

                8. realStory는 최종 클리어 후 보여줄 역사 해설입니다.
                   사건의 실제 배경, 최종 장소와의 관련성, 플레이어가 모은 힌트의 의미를 8~12문장으로 쓰세요.

                9. JSON만 출력하세요. 마크다운 코드블록이나 추가 설명은 쓰지 마세요.

                {
                  "regionName": "작전명 [비유적인 작전 이름]",
                  "regionDescription": "[정답과 최종 장소명을 숨긴 방탈출식 전체 스토리]",
                  "missions": [
                    {
                      "title": "[힌트 후보 공식명]",
                      "lat": 37.0,
                      "lng": 127.0,
                      "visionKeyword": "[현장 관찰 키워드]",
                      "description": "[해당 장소의 방탈출식 스토리 조각]",
                      "clue": "[방탈출 장면 단서와 역사적 정황을 함께 숨긴 혼합형 힌트]",
                      "isFinal": false
                    },
                    {
                      "title": "[최종 목적지 공식명]",
                      "lat": 37.0,
                      "lng": 127.0,
                      "visionKeyword": "[최종 현장 단서 대상]",
                      "description": "[마지막 장면의 방탈출식 스토리 조각]",
                      "clue": "[마지막 봉인을 여는 혼합형 사건 힌트. 정답 직접 노출 금지]",
                      "answerKeyword": "[장소명이 아닌 사건/일화 키워드]",
                      "realStory": "[역사 해설]",
                      "isFinal": true
                    }
                  ]
                }
                """.formatted(targetSpot, subSpots);

        return callGeminiStandard(url, prompt);
    }

    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String fieldClue = getFinalFieldClue(mission);
        String prompt = String.format("""
                당신은 Operation KOREA의 본부 오퍼레이터입니다.
                미션: %s
                현장 관찰 단서: "%s"
                대원 입력: "%s"
                정답 여부: %s

                [응답 규칙]
                - 한국어로 2문장 이하, 120자 이하.
                - 정답이면 성공을 짧게 알리고 자세한 해설은 종료 기록에서 제공된다고 안내하세요.
                - 오답이면 단정적인 정답 공개 없이, 수집한 단서와 현장 관찰 단서를 다시 연결해 보라고 안내하세요.
                - 마크다운 제목이나 목록은 쓰지 마세요.
                """, mission.getTitle(), fieldClue, userAnswer, isCorrect);

        return streamPrompt(geminiUrl(), prompt);
    }

    public ResponseBodyEmitter streamHintAnswer(Long missionId, Long userId, String userQuestion) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String clueContext = buildCollectedClueContext(getClearedClues(mission, userId));
        String fieldClue = getFinalFieldClue(mission);
        String prompt = String.format("""
                당신은 역사 추리 게임의 최종 채팅 진행자입니다.
                정답 키워드: "%s"
                최종 장소: "%s"
                현장 관찰 단서: "%s"
                실제 역사 기록 요약: "%s"
                플레이어가 수집한 단서: %s
                플레이어 질문: "%s"

                [응답 규칙]
                - 한국어로 2문장 이하, 170자 이하.
                - 질문 내용이 정답과 관련 있는지 "관련 있음", "부분적으로 관련 있음", "거리가 있음" 중 하나의 표현으로 알려주세요.
                - 관련이 있으면 어떤 역사적 축과 가까운지 한 문장으로 설명하세요.
                - 정답 키워드 자체는 절대 말하지 마세요.
                - 장소명, 인물명, 시대 배경은 힌트로 언급할 수 있지만 결론을 직접 확정하지 마세요.
                """,
                mission.getAnswerKeyword(),
                mission.getTitle(),
                fieldClue,
                summarizeForPrompt(mission.getRealStory(), 500),
                clueContext,
                userQuestion
        );

        return streamPrompt(geminiUrl(), prompt);
    }

    public boolean isHintQuestion(String userAnswer) {
        if (userAnswer == null) {
            return false;
        }
        String trimmed = userAnswer.trim();
        return trimmed.endsWith("?")
                || trimmed.endsWith("？")
                || trimmed.contains("관련")
                || trimmed.contains("맞아")
                || trimmed.contains("인가")
                || trimmed.contains("이야")
                || trimmed.contains("일까")
                || trimmed.contains("뭐")
                || trimmed.contains("무엇")
                || trimmed.contains("왜")
                || trimmed.contains("언제")
                || trimmed.contains("어디")
                || trimmed.contains("누구")
                || trimmed.contains("어떻게")
                || trimmed.contains("힌트");
    }

    public Map<String, Object> generateClearReport(Long missionId, Long userId) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String realStory = mission.getRealStory();
        List<Map<String, String>> clearedClues = getClearedClues(mission, userId);
        GameSession finalSession = gameSessionRepository.findByUserIdAndMissionId(userId, missionId).orElse(null);

        String report = realStory;
        Map<String, List<String>> clueExplanations = new HashMap<>();
        Map<String, Object> generatedReport = generatePlayerClearReport(mission, answerKeyword, realStory, clearedClues);
        if (generatedReport != null) {
            report = (String) generatedReport.getOrDefault("report", report);
            Object explanations = generatedReport.get("clueExplanations");
            if (explanations instanceof Map<?, ?> explanationMap) {
                for (Map.Entry<?, ?> entry : explanationMap.entrySet()) {
                    clueExplanations.put(String.valueOf(entry.getKey()), normalizeParagraphList(entry.getValue()));
                }
            }
        }

        if (report == null || report.isBlank()) {
            report = "작전이 완료되었습니다. 수집한 단서는 최종 장소와 연결된 실제 역사 사건을 추론하도록 설계되었습니다.";
        }
        if (clueExplanations.isEmpty()) {
            clueExplanations = buildFallbackClueExplanations(clearedClues, answerKeyword, mission.getTitle());
        }

        return Map.of(
                "missionId", mission.getId(),
                "title", mission.getTitle(),
                "answerKeyword", answerKeyword == null ? "" : answerKeyword,
                "report", report,
                "clueExplanations", clueExplanations,
                "score", finalSession != null && finalSession.getScore() != null ? finalSession.getScore() : 0,
                "elapsedSeconds", finalSession != null && finalSession.getElapsedSeconds() != null ? finalSession.getElapsedSeconds() : 0L,
                "routeDistanceMeters", finalSession != null && finalSession.getRouteDistanceMeters() != null ? finalSession.getRouteDistanceMeters() : 0.0
        );
    }

    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if (answerKeyword == null || answerKeyword.isBlank()) {
            return true;
        }

        String normalizedAnswer = normalizeAnswer(userAnswer);
        String normalizedKeyword = normalizeAnswer(answerKeyword);
        if (normalizedAnswer.equals(normalizedKeyword) || normalizedAnswer.contains(normalizedKeyword)) {
            return true;
        }

        String prompt = String.format("""
                정답 키워드: "%s"
                대원 답변: "%s"
                판정 기준: 답변이 정답 키워드 자체이거나 같은 역사 사건/일화의 공식 명칭을 명확히 말한 경우만 TRUE입니다.
                관련 인물, 장소, 시대 배경만 말한 경우는 FALSE입니다.
                TRUE 또는 FALSE만 출력하세요.
                """, answerKeyword, userAnswer);

        String result = callGeminiStandard(geminiUrl(), prompt);
        return result != null && "TRUE".equalsIgnoreCase(result.trim());
    }

    private List<Map<String, String>> getClearedClues(Mission finalMission, Long userId) {
        List<Map<String, String>> clues = new ArrayList<>();
        if (finalMission.getRegionId() == null) {
            return clues;
        }

        for (Mission mission : missionRepository.findByRegionId(finalMission.getRegionId())) {
            if (mission.isFinal()) {
                continue;
            }
            boolean cleared = gameSessionRepository.findByUserIdAndMissionId(userId, mission.getId())
                    .map(session -> "CLEARED".equals(session.getStatus()))
                    .orElse(false);
            if (!cleared) {
                continue;
            }
            clues.add(Map.of(
                    "id", String.valueOf(mission.getId()),
                    "title", mission.getTitle() == null ? "" : mission.getTitle(),
                    "clue", getMissionClueText(mission)
            ));
        }
        return clues;
    }

    private String getMissionClueText(Mission mission) {
        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return mission.getClue();
        }
        return mission.getAnswerKeyword() == null ? "" : mission.getAnswerKeyword();
    }

    private String buildCollectedClueContext(List<Map<String, String>> clearedClues) {
        if (clearedClues == null || clearedClues.isEmpty()) {
            return "아직 수집한 단서 없음";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, String> clue : clearedClues) {
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(clue.getOrDefault("title", "단서"))
                    .append(": ")
                    .append(summarizeForPrompt(clue.getOrDefault("clue", ""), 120));
        }
        return builder.toString();
    }

    private String getFinalFieldClue(Mission mission) {
        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return mission.getClue();
        }
        if (mission.getVisionKeyword() != null && !mission.getVisionKeyword().isBlank()) {
            return "'" + mission.getVisionKeyword() + "'에 남은 흠집과 연도가 마지막 봉인의 결을 비춘다. 권력의 문이 흔들리던 밤을 기억하라.";
        }
        return "마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 사건의 방향을 의심하라.";
    }

    private String summarizeForPrompt(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private Map<String, Object> generatePlayerClearReport(
            Mission mission,
            String answerKeyword,
            String realStory,
            List<Map<String, String>> clearedClues) {
        String prompt = String.format("""
                당신은 역사 추리 관광 게임의 클리어 리포트 작성자입니다.

                [최종 장소] %s
                [정답 사건] %s
                [실제 역사 기록] %s
                [플레이어가 수집한 힌트] %s

                JSON만 반환하세요.
                report는 실제 역사 사실과 게임 단서의 의미를 8~12문장으로 설명하세요.
                clueExplanations는 각 힌트 id를 key로 쓰고, 값은 2~3개 문단 배열로 작성하세요.

                {
                  "report": "문장 단위의 역사 해설",
                  "clueExplanations": {
                    "힌트id": ["문단1", "문단2"]
                  }
                }
                """, mission.getTitle(), answerKeyword, realStory == null ? "" : realStory, clearedClues);

        String raw = callGeminiStandard(geminiUrl(), prompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            int startIndex = raw.indexOf('{');
            int endIndex = raw.lastIndexOf('}');
            if (startIndex == -1 || endIndex == -1) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw.substring(startIndex, endIndex + 1));
            Map<String, Object> parsed = new HashMap<>();
            parsed.put("report", root.path("report").asText(""));

            Map<String, List<String>> explanations = new HashMap<>();
            JsonNode explanationNode = root.path("clueExplanations");
            explanationNode.properties().forEach(entry -> explanations.put(entry.getKey(), normalizeParagraphList(entry.getValue())));
            parsed.put("clueExplanations", explanations);
            return parsed;
        } catch (Exception e) {
            log.warn("Clear report JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private List<String> normalizeParagraphList(Object value) {
        List<String> paragraphs = new ArrayList<>();
        if (value instanceof JsonNode node && node.isArray()) {
            node.forEach(item -> {
                if (!item.asText("").isBlank()) {
                    paragraphs.add(item.asText());
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(item -> {
                if (item != null && !String.valueOf(item).isBlank()) {
                    paragraphs.add(String.valueOf(item));
                }
            });
        } else if (value != null && !String.valueOf(value).isBlank()) {
            paragraphs.add(String.valueOf(value));
        }
        return paragraphs;
    }

    private Map<String, List<String>> buildFallbackClueExplanations(
            List<Map<String, String>> clues,
            String answerKeyword,
            String finalTitle) {
        Map<String, List<String>> explanations = new HashMap<>();
        for (Map<String, String> clue : clues) {
            String id = clue.getOrDefault("id", "");
            String title = clue.getOrDefault("title", "수집한 단서");
            String clueText = clue.getOrDefault("clue", "현장 단서");
            explanations.put(id, List.of(
                    title + "에서 얻은 단서는 \"" + clueText + "\"입니다.",
                    "이 단서는 " + finalTitle + "와 연결된 사건을 직접 말하지 않고, 플레이어가 정황을 조합하도록 만든 힌트입니다.",
                    "최종 정답은 " + answerKeyword + "이며, 각 힌트는 그 사건의 분위기와 역사적 배경을 우회적으로 가리킵니다."
            ));
        }
        return explanations;
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
            log.error("Gemini request failed: {}", e.getMessage());
            return null;
        }
    }

    private String geminiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + geminiApiKey.trim();
    }
}
