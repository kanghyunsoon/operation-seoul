package com.operation.seoul.game.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // 이거 임포트 확인
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AdminMissionController {

    private final TourApiService tourApiService;
    private final GeminiAiService geminiAiService;
    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;

    // 💡 변경됨: 스프링에게 의존성 주입을 맡기지 않고 직접 객체를 생성해서 에러 원천 차단
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/generate")
    public ResponseEntity<String> generateMissionByAi(
            @RequestParam(required = false) Long regionId,
            @RequestParam double lat,
            @RequestParam double lng) {

        log.info("🤖 AI 작전 수립 파이프라인 가동 시작... 기준 좌표: lat={}, lng={}", lat, lng);

        try {
            // 1. 장소 데이터 수집 (요원님의 실제 메서드 사용)
            List<Map<String, String>> spots = tourApiService.getNearbyTouristSpots(lng, lat, 2000);

            if (spots == null || spots.isEmpty()) {
                return ResponseEntity.badRequest().body("주변 반경에 관광지 데이터가 없습니다. 좌표를 변경해주세요.");
            }

            // 2. Gemini AI에게 스토리 및 JSON 생성 요청
            String jsonResponse = geminiAiService.generateDynamicMissions(spots);
            log.info("📩 AI가 작성한 작전 기획안: {}", jsonResponse);

            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return ResponseEntity.internalServerError().body("AI 응답을 받지 못했습니다.");
            }

            // 3. JSON 파싱
            JsonNode root = objectMapper.readTree(jsonResponse);

            // 4. Region 테이블에 새 작전 구역(카드) 저장
            Region newRegion = new Region();
            newRegion.setName(root.path("regionName").asText("알 수 없는 작전"));
            newRegion.setDescription(root.path("regionDescription").asText("스토리 브리핑 대기 중..."));
            Region savedRegion = regionRepository.save(newRegion);

            // 5. Mission 테이블에 마커 저장 및 Region 연동
            JsonNode missionsNode = root.path("missions");
            if (missionsNode.isArray()) {
                for (JsonNode mNode : missionsNode) {
                    Mission mission = new Mission();
                    mission.setRegionId(savedRegion.getId());
                    mission.setTitle(mNode.path("title").asText());
                    mission.setTargetLat(mNode.path("lat").asDouble());
                    mission.setTargetLng(mNode.path("lng").asDouble());
                    mission.setVisionKeyword(mNode.path("visionKeyword").asText());
                    mission.setFinal(mNode.path("isFinal").asBoolean(false));
                    mission.setRadiusInMeters(50.0);

                    missionRepository.save(mission);
                }
            }

            log.info("✅ 데이터베이스 적재 완료. 작전명: {}", savedRegion.getName());
            return ResponseEntity.ok("AI 작전 생성 완료! [" + savedRegion.getName() + "] 카드가 DB에 등록되었습니다.");

        } catch (Exception e) {
            log.error("🚨 AI 미션 생성 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body("작전 수립 실패: " + e.getMessage());
        }
    }
}