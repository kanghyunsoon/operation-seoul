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

    //test
//    public String extractTextFromImage(MultipartFile image) {
//        // 실제 구글 서버 호출 코드를 잠시 주석 처리하고, 테스트용 텍스트를 바로 리턴합니다.
//        System.out.println("⚠️ 결제 이슈로 인해 가짜 인증 텍스트를 반환합니다.");
//        return "이곳은 덕수궁 중명전 입니다."; // DB의 visionKeyword에 맞춰서 작성
//    }
    // 1. 찐(Real) Google Vision API 통신
    public String extractTextFromImage(MultipartFile image) {
        try {
            // 1. 이미지를 Base64 문자열로 변환
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            // 2. 구글 Vision API URL 세팅
            String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;

            // 3. 구글이 요구하는 형태의 JSON 바디 만들기 (Map 사용)
            Map<String, Object> imageMap = Map.of("content", base64Image);
            Map<String, Object> featureMap = Map.of("type", "TEXT_DETECTION"); // 글자 인식 모드
            Map<String, Object> requestMap = Map.of(
                    "image", imageMap,
                    "features", List.of(featureMap)
            );
            Map<String, Object> requestBody = Map.of("requests", List.of(requestMap));

            // 4. HTTP 헤더 및 엔티티(요청 객체) 생성! 여기서 httpEntity가 확실하게 만들어집니다.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            // 5. 구글 비전 서버로 POST 요청 (JsonNode 대신 Map 사용)
            ResponseEntity<Map> response = restTemplate.postForEntity(url, httpEntity, Map.class);

            // 6. 구글의 응답에서 텍스트 정보만 안전하게 빼오기
            List<Map<String, Object>> responses = (List<Map<String, Object>>) response.getBody().get("responses");

            // 사진에 글자가 아예 없는 경우 방어 로직
            if (responses == null || responses.isEmpty() || !responses.get(0).containsKey("textAnnotations")) {
                System.out.println("⚠️ 사진에서 글자를 찾지 못했습니다.");
                return "";
            }

            List<Map<String, Object>> textAnnotations = (List<Map<String, Object>>) responses.get(0).get("textAnnotations");
            String extractedText = (String) textAnnotations.get(0).get("description");

            System.out.println("📸 AI가 인식한 텍스트: \n" + extractedText);
            return extractedText;

        } catch (Exception e) {
            System.err.println("Vision API 통신 에러: " + e.getMessage());
            throw new RuntimeException("AI 이미지 분석에 실패했습니다.");
        }
    }
    // 2. 키워드 검증기 (기존과 동일)
    public boolean validateKeyword(Long missionId, String extractedText) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("미션 오류!"));

        String targetKeyword = mission.getVisionKeyword();
        if(targetKeyword == null || targetKeyword.isEmpty()) return true;

        // ✨ 수정 포인트: 사진에서 읽은 글자와 정답 키워드 모두 공백/줄바꿈을 제거하고 비교합니다.
        String cleanExtractedText = extractedText.replace(" ", "").replace("\n", "").toLowerCase();
        String cleanTargetKeyword = targetKeyword.replace(" ", "").toLowerCase();

        // 로그를 찍어서 실제로 AI가 무엇을 읽었는지 서버 콘솔에서 확인하세요.
        System.out.println("🧐 AI가 읽은 텍스트(공백제거): " + cleanExtractedText);
        System.out.println("🎯 목표 키워드(공백제거): " + cleanTargetKeyword);

        return cleanExtractedText.contains(cleanTargetKeyword);
    }
}