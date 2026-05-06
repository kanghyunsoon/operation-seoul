package com.operation.seoul.game.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional; // 추가됨
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class MissionGenerateRequest {
        private Map<String, String> targetSpot;
        private List<Map<String, String>> candidateSpots;
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> getCandidates(@RequestParam double lat, @RequestParam double lng) {
        log.info("📍 관광지 후보 검색 요청: lat={}, lng={}", lat, lng);
        try {
            List<Map<String, String>> spots = tourApiService.getNearbyTouristSpots(lng, lat, 2000);
            if (spots == null || spots.isEmpty()) {
                return ResponseEntity.badRequest().body("주변 반경에 관광지 데이터가 없습니다.");
            }
            return ResponseEntity.ok(spots);
        } catch (Exception e) {
            log.error("🚨 후보지 검색 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body("후보지 검색 실패: " + e.getMessage());
        }
    }

    @PostMapping("/generate-selected")
    public ResponseEntity<?> generateMissionByAi(@RequestBody MissionGenerateRequest request) {
        log.info("🤖 AI 작전 수립 파이프라인 가동 시작...");
        try {
            List<Map<String, String>> spotsForAi = new ArrayList<>();
            if (request.getTargetSpot() != null) spotsForAi.add(request.getTargetSpot());
            if (request.getCandidateSpots() != null && !request.getCandidateSpots().isEmpty()) {
                spotsForAi.addAll(request.getCandidateSpots());
            }

            if (spotsForAi.isEmpty()) return ResponseEntity.badRequest().body("장소 데이터가 없습니다.");

            String aiRawResponse = geminiAiService.generateDynamicMissions(spotsForAi);
            if (aiRawResponse == null || aiRawResponse.isBlank()) return ResponseEntity.internalServerError().body("AI 응답 없음");

            int startIndex = aiRawResponse.indexOf('{');
            int endIndex = aiRawResponse.lastIndexOf('}');
            if (startIndex == -1 || endIndex == -1) return ResponseEntity.internalServerError().body("JSON 포맷 오류");

            String pureJson = aiRawResponse.substring(startIndex, endIndex + 1);
            JsonNode root = objectMapper.readTree(pureJson);

            Region newRegion = new Region();
            newRegion.setName(root.path("regionName").asText("알 수 없는 작전"));
            newRegion.setDescription(root.path("regionDescription").asText("스토리 브리핑 대기 중..."));
            Region savedRegion = regionRepository.save(newRegion);

            JsonNode missionsNode = root.path("missions");
            if (missionsNode.isArray()) {
                for (JsonNode mNode : missionsNode) {
                    Mission mission = new Mission();
                    mission.setRegionId(savedRegion.getId());
                    mission.setTitle(mNode.path("title").asText("목적지"));
                    mission.setTargetLat(mNode.path("lat").asDouble(0.0));
                    mission.setTargetLng(mNode.path("lng").asDouble(0.0));
                    mission.setVisionKeyword(mNode.path("visionKeyword").asText(""));

                    boolean isFinal = mNode.path("isFinal").asBoolean(false);
                    mission.setFinal(isFinal);
                    mission.setRadiusInMeters(50.0);
                    mission.setAnswerKeyword(isFinal ? mNode.path("answerKeyword").asText("정답누락") : mNode.path("clue").asText("단서누락"));

                    missionRepository.save(mission);
                }
            }
            return ResponseEntity.ok("AI 작전 생성 완료! [" + savedRegion.getName() + "] 카드가 등록되었습니다.");
        } catch (Exception e) {
            log.error("🚨 AI 미션 생성 중 오류: ", e);
            return ResponseEntity.internalServerError().body("작전 수립 실패: " + e.getMessage());
        }
    }

    // 💡 뚫려있지 않던 삭제 엔드포인트 긴급 개통!
    @DeleteMapping("/regions/{regionId}")
    @Transactional // 카드와 마커를 동시에 지우기 위해 트랜잭션 적용
    public ResponseEntity<?> deleteRegion(@PathVariable Long regionId) {
        log.info("🗑️ 작전 파기 요청 수신. Region ID: {}", regionId);
        try {
            // 1. 해당 지역에 속한 모든 미션(마커)들을 먼저 파기
            List<Mission> missions = missionRepository.findByRegionId(regionId);
            if (!missions.isEmpty()) {
                missionRepository.deleteAll(missions);
                log.info("   - 하위 미션 데이터 {}개 삭제 완료", missions.size());
            }

            // 2. 작전 구역(Region) 데이터 파기
            if (regionRepository.existsById(regionId)) {
                regionRepository.deleteById(regionId);
                log.info("   - Region ID: {} 최종 파기 성공", regionId);
                return ResponseEntity.ok("성공적으로 해당 작전 데이터가 영구 파기되었습니다.");
            } else {
                return ResponseEntity.status(404).body("이미 존재하지 않거나 파기된 작전입니다.");
            }
        } catch (Exception e) {
            log.error("🚨 작전 파기 실패: ", e);
            return ResponseEntity.internalServerError().body("작전 파기 중 장애 발생: " + e.getMessage());
        }
    }
}