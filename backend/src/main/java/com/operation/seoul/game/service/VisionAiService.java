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
 * [Service: 사진 인식 및 현장 인증 검증 엔진]
 * - 역할: Google Vision API 연동 및 사진 속 키워드 일치 여부 판별
 */
@Service
@RequiredArgsConstructor
public class VisionAiService {

    private final MissionRepository missionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /** Google Cloud Platform에서 발급받은 API Key (application.yml 설정값) */
    @Value("${google.vision.key}")
    private String visionApiKey;

    /**[기능: 이미지 내 텍스트 추출]
     - 수행 내용: MultipartFile 형태의 이미지를 Google Vision API로 전송하여 OCR 결과 반환
     - 매개 변수: MultipartFile image (유저가 촬영하여 업로드한 사진)
     - 반환 값: String (추출된 전체 텍스트 데이터)  */
    public String extractTextFromImage(MultipartFile image) {
        try {
            // 1. 이미지를 Base64 문자열로 인코딩 (JSON 전송 규격)
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            // 2. Google Vision API 엔드포인트 설정 (API Key 포함)
            String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;

            // 3. API 요청 바디 구성 (Map을 이용한 JSON 구조화)
            Map<String, Object> imageMap = Map.of("content", base64Image);
            Map<String, Object> featureMap = Map.of("type", "TEXT_DETECTION"); // 텍스트 감지 모드 설정
            Map<String, Object> requestMap = Map.of(
                    "image", imageMap,
                    "features", List.of(featureMap)
            );
            Map<String, Object> requestBody = Map.of("requests", List.of(requestMap));

            // 4. HTTP 헤더 설정 및 엔티티 생성 (Content-Type: JSON)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            // 5. RestTemplate을 이용한 외부 API(Google) POST 요청 실행
            ResponseEntity<Map> response = restTemplate.postForEntity(url, httpEntity, Map.class);

            // 6. 응답 데이터 파싱 및 방어 로직 수행
            List<Map<String, Object>> responses = (List<Map<String, Object>>) response.getBody().get("responses");

            // 결과값이 없거나 텍스트 정보가 감지되지 않은 경우 빈 값 반환
            if (responses == null || responses.isEmpty() || !responses.get(0).containsKey("textAnnotations")) {
                System.out.println("⚠️ 사진에서 글자를 찾지 못했습니다.");
                return "";
            }

            // 전체 텍스트 덩어리 추출 (textAnnotations의 0번 인덱스가 전체 텍스트 요약본)
            List<Map<String, Object>> textAnnotations = (List<Map<String, Object>>) responses.get(0).get("textAnnotations");
            String extractedText = (String) textAnnotations.get(0).get("description");

            System.out.println("📸 AI가 인식한 텍스트: \n" + extractedText);
            return extractedText;

        } catch (Exception e) {
            System.err.println("Vision API 통신 에러: " + e.getMessage());
            throw new RuntimeException("AI 이미지 분석에 실패했습니다.");
        }
    }

    /**[기능: 추출 텍스트 기반 미션 장소 검증]
     - 수행 내용: 사진에서 읽은 텍스트에 미션별 목표 키워드가 포함되어 있는지 확인
     - 매개 변수: Long missionId (검증 대상 미션 번호), String extractedText (AI가 읽은 문자열)
     - 반환 값: boolean (검증 성공 시 true)  */
    public boolean validateKeyword(Long missionId, String extractedText) {
        // 1. 미션 정보 조회 및 예외 처리
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("미션 오류!"));

        // 2. 목표 키워드 획득 (키워드가 설정되지 않은 미션은 즉시 승인 처리)
        String targetKeyword = mission.getVisionKeyword();
        if(targetKeyword == null || targetKeyword.isEmpty()) return true;

        // 3. 데이터 전처리 (공백 및 줄바꿈 제거, 소문자 치환으로 비교 유연성 확보)
        String cleanExtractedText = extractedText.replace(" ", "").replace("\n", "").toLowerCase();
        String cleanTargetKeyword = targetKeyword.replace(" ", "").toLowerCase();

        // 4. 로깅 (디버깅용: AI 인식 텍스트와 정답 데이터 비교 출력)
        System.out.println("🧐 AI가 읽은 텍스트(공백제거): " + cleanExtractedText);
        System.out.println("🎯 목표 키워드(공백제거): " + cleanTargetKeyword);

        // 5. 포함 여부 검사 (부분 일치 허용)
        return cleanExtractedText.contains(cleanTargetKeyword);
    }
}