package com.operation.seoul.game.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/missions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AdminMissionController {

    private final TourApiService tourApiService;
    private final GeminiAiService geminiAiService;
    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [관리자 기능: AI 자동 미션 생성 및 저장]
     * @param regionId 미션을 추가할 지역 ID
     * @param lat 기준 위도
     * @param lng 기준 경도
     */
    @PostMapping("/generate")
    public ResponseEntity<String> generateAndSaveMission(
            @RequestParam Long regionId,
            @RequestParam double lat,
            @RequestParam double lng) {

        try {
            // 1. TourAPI를 통해 주변 명소 5개 확보 (반경 1km)
            List<Map<String, String>> spots = tourApiService.getNearbyTouristSpots(lng, lat, 1000);
            if (spots.isEmpty()) return ResponseEntity.badRequest().body("주변에 활용할 명소가 없습니다.");

            // 2. Gemini에게 명소 리스트를 던져서 스파이 미션 스크립트 생성 요청
            String aiJsonResponse = geminiAiService.generateDynamicMission(spots);

            // 3. AI가 준 JSON 문자열을 자바 객체로 파싱
            JsonNode missionNode = objectMapper.readTree(aiJsonResponse);
            String targetName = missionNode.path("targetName").asText();

            // 4. AI가 선택한 장소의 실제 좌표를 spots 리스트에서 찾기
            Map<String, String> selectedSpot = spots.stream()
                    .filter(s -> s.get("title").contains(targetName) || targetName.contains(s.get("title")))
                    .findFirst()
                    .orElse(spots.get(0)); // 못 찾으면 첫 번째 장소로 폴백

            // 5. Mission 엔티티 생성 및 DB 저장
            Mission newMission = new Mission();
            newMission.setRegionId(regionId);
            newMission.setTitle(missionNode.path("title").asText());
            newMission.setDescription(missionNode.path("description").asText());
            newMission.setVisionKeyword(missionNode.path("visionKeyword").asText());
            newMission.setAnswerKeyword(missionNode.path("answerKeyword").asText());
            newMission.setTargetLat(Double.parseDouble(selectedSpot.get("mapY")));
            newMission.setTargetLng(Double.parseDouble(selectedSpot.get("mapX")));
            newMission.setRadiusInMeters(50.0); // 기본 50m 반경
            newMission.setFinal(false); // 일단 일반 미션으로 생성

            missionRepository.save(newMission);

            return ResponseEntity.ok("성공적으로 ' " + newMission.getTitle() + " ' 작전이 수립되었습니다!");

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("작전 수립 실패: " + e.getMessage());
        }
    }
}