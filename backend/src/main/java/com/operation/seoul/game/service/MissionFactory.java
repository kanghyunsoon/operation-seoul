package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.dto.AiCourseResponseDto;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import com.operation.seoul.location.service.OperationAreaResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionFactory {

    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final OperationAreaResolver operationAreaResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 백엔드 표준 키(kakao.rest.api.key)를 우선 사용하되, 기존 로컬 설정(VITE_KAKAO_REST_KEY)도 호환합니다.
    @Value("${kakao.rest.api.key:${VITE_KAKAO_REST_KEY:}}")
    private String kakaoApiKey;

    /**
     * 단일 최종 장소 데이터를 받아 주변 후보를 보강하고 Gemini 시나리오를 생성해 DB에 저장합니다.
     * 현재 관리자 화면은 AdminMissionController의 generate-selected 흐름을 주로 사용합니다.
     */
    @Transactional
    public Map<String, Object> createAiMission(Map<String, Object> spotData) {
        String finalSpotName = (String) spotData.get("title");
        double finalLat = Double.parseDouble(spotData.get("lat").toString());
        double finalLng = Double.parseDouble(spotData.get("lng").toString());

        log.info("🕵️‍♂️ 작전 수립 개시. 타겟: {}", finalSpotName);

        // 1. 카카오맵 API로 주변 동선 3곳 추출
        List<Map<String, Object>> subMissions = getNearbyKakaoSpots(finalLat, finalLng);

        // 2. Gemini를 통한 스토리텔링 및 챕터별 목표 생성
        AiCourseResponseDto aiResult = generateStoryFromGemini(finalSpotName, finalLat, finalLng, subMissions);

        // 3. Region(지역/작전구역) 테이블 저장
        Region region = new Region();
        region.setAreaCode(operationAreaResolver.resolveAreaCode(finalLat, finalLng, null));
        region.setName(aiResult.getRegionName());
        region.setDescription(aiResult.getRegionDescription());
        Region savedRegion = regionRepository.save(region);

        // 4. Mission(세부 동선 및 힌트) 테이블 저장
        for (AiCourseResponseDto.AiMissionDto dto : aiResult.getMissions()) {
            Mission mission = new Mission();
            mission.setRegionId(savedRegion.getId());
            mission.setTitle(dto.getTitle());
            mission.setTargetLat(dto.getLat());
            mission.setTargetLng(dto.getLng());
            mission.setVisionKeyword(dto.getVisionKeyword());
            mission.setClue(dto.getClue());
            mission.setAnswerKeyword(dto.getAnswerKeyword());
            mission.setFinal(dto.isFinal());
            mission.setRadiusInMeters(50.0);

            missionRepository.save(mission);
        }

        log.info("✅ 데이터베이스 작전 적재 완료: {}", savedRegion.getName());
        return Map.of("message", "Success", "regionName", savedRegion.getName());
    }

    private List<Map<String, Object>> getNearbyKakaoSpots(double lat, double lng) {
        List<Map<String, Object>> spots = new ArrayList<>();

        if (kakaoApiKey == null || kakaoApiKey.isEmpty()) {
            log.warn("⚠️ 카카오 API 키가 없습니다. 긴급 더미 데이터를 주입합니다.");
            return createDummySpots(lat, lng);
        }

        try {
            // 카페 카테고리(CE7)를 우선 사용해 최종 목적지 주변 경유지를 확보합니다.
            String url = "https://dapi.kakao.com/v2/local/search/category.json?category_group_code=CE7&y=" + lat + "&x=" + lng + "&radius=1500&size=3";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode docs = objectMapper.readTree(response.getBody()).path("documents");

            for (JsonNode doc : docs) {
                Map<String, Object> map = new HashMap<>();
                map.put("title", doc.path("place_name").asText());
                map.put("lat", doc.path("y").asDouble());
                map.put("lng", doc.path("x").asDouble());
                spots.add(map);
            }
        } catch (Exception e) {
            log.error("🚨 카카오맵 로컬 API 호출 실패. REST API 키를 사용했는지 확인하세요.", e);
        }

        while (spots.size() < 3) {
            spots.addAll(createDummySpots(lat, lng));
        }
        return spots.subList(0, 3);
    }

    private List<Map<String, Object>> createDummySpots(double lat, double lng) {
        return List.of(
                Map.of("title", "오래된 뒷골목 다방", "lat", lat + 0.002, "lng", lng + 0.002),
                Map.of("title", "버려진 공원 벤치", "lat", lat - 0.001, "lng", lng + 0.003),
                Map.of("title", "비밀스러운 헌책방", "lat", lat, "lng", lng - 0.002)
        );
    }

    private AiCourseResponseDto generateStoryFromGemini(String finalSpot, double finalLat, double finalLng, List<Map<String, Object>> subs) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + geminiApiKey;

            String promptText = String.format(
                    "당신은 '오퍼레이션 코리아' 작전 지휘관입니다. 다음 4개의 장소를 순서대로 방문하여 힌트를 얻는 '독립운동 혹은 첩보 밀서 전달' 컨셉의 방탈출 시나리오를 만드세요.\n" +
                            "1. %s (서브 힌트 1, 위도 %.4f, 경도 %.4f)\n" +
                            "2. %s (서브 힌트 2, 위도 %.4f, 경도 %.4f)\n" +
                            "3. %s (서브 힌트 3, 위도 %.4f, 경도 %.4f)\n" +
                            "4. %s (최종 목적지, 위도 %.4f, 경도 %.4f)\n\n" +
                            "조건: 각 장소마다 주변 사물을 찍어 인증할 'visionKeyword'(예: 간판, 돌담, 시계 등)를 넣고, 최종 목적지에는 최종 퀴즈 정답인 'answerKeyword'를 만드세요.\n" +
                            "반드시 아래 JSON 형식으로만 대답하세요 (응답 내용에 코드 블록 기호나 마크다운은 절대 사용하지 마세요):\n" +
                            "{\n" +
                            "  \"regionName\": \"작전명: ...\",\n" +
                            "  \"regionDescription\": \"요원, 당신의 임무는...\",\n" +
                            "  \"missions\": [\n" +
                            "    {\"title\": \"장소1\", \"lat\": 위도, \"lng\": 경도, \"visionKeyword\": \"...\", \"answerKeyword\": null, \"isFinal\": false},\n" +
                            "    {\"title\": \"최종장소\", \"lat\": 위도, \"lng\": 경도, \"visionKeyword\": \"...\", \"answerKeyword\": \"최종정답\", \"isFinal\": true}\n" +
                            "  ]\n" +
                            "}",
                    subs.get(0).get("title"), (double) subs.get(0).get("lat"), (double) subs.get(0).get("lng"),
                    subs.get(1).get("title"), (double) subs.get(1).get("lat"), (double) subs.get(1).get("lng"),
                    subs.get(2).get("title"), (double) subs.get(2).get("lat"), (double) subs.get(2).get("lng"),
                    finalSpot, finalLat, finalLng
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", promptText)))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String aiJsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            // Gemini가 실수로 코드블록이나 설명을 붙여도 JSON 본문만 파싱합니다.
            int startIndex = aiJsonText.indexOf("{");
            int endIndex = aiJsonText.lastIndexOf("}");

            if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                aiJsonText = aiJsonText.substring(startIndex, endIndex + 1);
            } else {
                throw new RuntimeException("응답에서 JSON 형식을 찾을 수 없습니다. 원본: " + aiJsonText);
            }

            return objectMapper.readValue(aiJsonText, AiCourseResponseDto.class);

        } catch (Exception e) {
            log.error("🚨 Gemini 작전 생성 실패 상세 원인: ", e);
            throw new RuntimeException("AI 시나리오 파싱 오류: " + e.getMessage());
        }
    }
}
