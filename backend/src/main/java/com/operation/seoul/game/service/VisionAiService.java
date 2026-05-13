package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisionAiService {

    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.vision.key}")
    private String visionApiKey;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * 업로드 이미지가 미션의 목표 단서를 충분히 담았는지 확인하고 성공 시 세션을 클리어합니다.
     * 관리자는 현장 테스트 편의를 위해 Vision 판정 없이 통과할 수 있습니다.
     */
    public Map<String, Object> verifyAndRecordMission(Long missionId, MultipartFile image, Long userId, boolean isAdmin) {
        boolean isSuccess = isAdmin || validateKeyword(missionId, image);

        if (!isSuccess) {
            return Map.of(
                    "success", false,
                    "message", "목표 단서를 식별할 수 없습니다. 프레임에 정확히 담아주십시오."
            );
        }

        GameSession session = gameSessionRepository.findByUserIdAndMissionId(userId, missionId)
                .orElseGet(() -> {
                    GameSession newSession = new GameSession();
                    newSession.setUserId(userId);
                    newSession.setMissionId(missionId);
                    newSession.setStartedAt(LocalDateTime.now());
                    return newSession;
                });

        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setStatus("CLEARED");
        session.setClearedAt(LocalDateTime.now());
        gameSessionRepository.save(session);

        String keywordMsg = isAdmin ? "판독 성공 (관리자 프리패스)" : "판독 성공";
        return Map.of(
                "success", true,
                "keyword", keywordMsg,
                "message", "인증 성공! 단서가 해금되었습니다."
        );
    }

    /**
     * Google Vision 라벨 결과와 미션의 visionKeyword를 Gemini로 의미 비교합니다.
     * 현재는 사물/장면 라벨 중심이며, 간판 문구 인증이 필요하면 TEXT_DETECTION을 추가해야 합니다.
     */
    public boolean validateKeyword(Long missionId, MultipartFile image) {
        try {
            Mission mission = missionRepository.findById(missionId)
                    .orElseThrow(() -> new IllegalArgumentException("미션 오류!"));

            String targetKeyword = mission.getVisionKeyword();
            if (targetKeyword == null || targetKeyword.isEmpty()) return true;

            // 1. Google Vision API로 사진에서 사물/장면 라벨 추출
            String extractedData = getLabelsFromVision(image);
            System.out.println("🧐 Vision API 추출 라벨: " + extractedData);

            // 2. Gemini를 이용한 의미론적 비교
            return judgeMatchWithGemini(extractedData, targetKeyword);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Google Vision LABEL_DETECTION 결과를 쉼표로 이어 붙인 소문자 문자열로 반환합니다. */
    private String getLabelsFromVision(MultipartFile image) throws Exception {
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

        Map<String, Object> imageReq = Map.of("content", base64Image);
        Map<String, Object> feature = Map.of("type", "LABEL_DETECTION", "maxResults", 10);
        Map<String, Object> request = Map.of("image", imageReq, "features", List.of(feature));
        Map<String, Object> body = Map.of("requests", List.of(request));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode labelsNode = root.path("responses").get(0).path("labelAnnotations");

        StringBuilder sb = new StringBuilder();
        if (labelsNode.isArray()) {
            for (JsonNode label : labelsNode) {
                sb.append(label.path("description").asText()).append(", ");
            }
        }
        return sb.toString().toLowerCase();
    }

    /** Vision 라벨들이 목표 단서를 설명하는지 Gemini에게 TRUE/FALSE로만 판정하게 합니다. */
    private boolean judgeMatchWithGemini(String labels, String target) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

        String prompt = String.format(
                "당신은 '오퍼레이션 서울'의 작전 통제 AI입니다. 요원이 현장에서 찍은 사진의 분석 키워드들을 보고, 목표 사물과 일치하는지 판단하세요.\n\n" +
                        "목표: %s\n" +
                        "현장 분석 키워드: %s\n\n" +
                        "만약 현장 키워드들이 목표를 충분히 설명한다면(예: 목표가 '붉은색 문'이고 키워드에 'red', 'door'가 포함됨) 오직 'TRUE'라고만 답하고, 전혀 상관없다면 오직 'FALSE'라고만 답하세요. 다른 설명은 절대 하지 마세요.",
                target, labels
        );

        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey.trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String aiAnswer = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();

        return aiAnswer.equalsIgnoreCase("TRUE");
    }
}
