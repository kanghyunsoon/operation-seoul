package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * [Service: 사진 인식 및 검증 엔진]
 */
@Service
@RequiredArgsConstructor
public class VisionAiService {

    private final MissionRepository missionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.vision.key}")
    private String visionApiKey;

    // 1. 찐(Real) Google Vision API 통신
    public String extractTextFromImage(MultipartFile image) {
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;

        try {
            // 이미지를 구글이 읽을 수 있는 Base64 문자열로 변환
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            // JSON 바디 만들기 (TEXT_DETECTION = 사진 속 글씨를 읽어라)
            Map<String, Object> imageMap = Map.of("content", base64Image);
            Map<String, Object> feature = Map.of("type", "TEXT_DETECTION");
            Map<String, Object> request = Map.of("image", imageMap, "features", List.of(feature));
            Map<String, Object> requestBody = Map.of("requests", List.of(request));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            // 구글 서버로 이미지 전송!
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, httpEntity, JsonNode.class);

            // 인식된 전체 텍스트 추출 (글씨를 못 찾았으면 빈 문자열 반환)
            JsonNode textAnnotations = response.getBody().path("responses").get(0).path("textAnnotations");
            if (textAnnotations.isMissingNode() || textAnnotations.isEmpty()) {
                return "";
            }

            String extractedText = textAnnotations.get(0).path("description").asText();
            System.out.println("📸 AI가 인식한 실제 글씨: \n" + extractedText);
            return extractedText;

        } catch (Exception e) {
            System.err.println("Vision API 에러: " + e.getMessage());
            throw new RuntimeException("AI 이미지 분석에 실패했습니다.");
        }
    }

    // 2. 키워드 검증기 (기존과 동일)
    public boolean validateKeyword(Long missionId, String extractedText) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("미션 오류!"));

        String targetKeyword = mission.getVisionKeyword();
        if(targetKeyword == null || targetKeyword.isEmpty()) return true;

        // 구글이 읽어온 텍스트(extractedText) 안에 우리가 원하는 현판 글씨가 있는지 체크
        return extractedText.contains(targetKeyword);
    }
}