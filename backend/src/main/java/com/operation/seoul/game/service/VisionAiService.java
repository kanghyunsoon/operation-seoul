package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class VisionAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.vision.key}")
    private String visionApiKey;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public boolean validateKeyword(Long missionId, MultipartFile image) {
        try {
            Mission mission = missionRepository.findById(missionId)
                    .orElseThrow(() -> new IllegalArgumentException("미션 오류!"));

            String targetKeyword = mission.getVisionKeyword();
            if (targetKeyword == null || targetKeyword.isEmpty()) return true;

            // 1. Google Vision API로 사진에서 데이터(글자+사물) 추출
            String extractedData = getLabelsFromVision(image);
            System.out.println("🧐 본부 수신 데이터(Vision API): " + extractedData);

            // 2. Gemini AI에게 "추출된 데이터가 정답과 일치하는지" 판단 요청
            return judgeMatchWithGemini(extractedData, targetKeyword);

        } catch (Exception e) {
            System.err.println("🚨 분석 엔진 가동 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Google Vision API 호출 로직 (라벨 및 텍스트 동시 추출)
    private String getLabelsFromVision(MultipartFile image) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;

        Map<String, Object> request = Map.of("requests", List.of(Map.of(
                "image", Map.of("content", base64Image),
                "features", List.of(
                        Map.of("type", "TEXT_DETECTION"),
                        Map.of("type", "LABEL_DETECTION")
                )
        )));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode res = root.path("responses").get(0);

        StringBuilder sb = new StringBuilder();
        res.path("textAnnotations").forEach(n -> sb.append(n.path("description").asText()).append(" "));
        res.path("labelAnnotations").forEach(n -> sb.append(n.path("description").asText()).append(" "));

        return sb.toString().toLowerCase();
    }

    // Gemini AI를 이용한 의미론적(Semantic) 판독 로직
    private boolean judgeMatchWithGemini(String labels, String target) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + geminiApiKey;

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
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        String aiAnswer = objectMapper.readTree(response.getBody())
                .path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();

        System.out.println("🤖 Gemini의 최종 판독: " + aiAnswer);
        return aiAnswer.equalsIgnoreCase("TRUE");
    }
}