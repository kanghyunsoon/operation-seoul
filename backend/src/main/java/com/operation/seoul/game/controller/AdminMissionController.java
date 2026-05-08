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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AdminMissionController {

    private static final double MIN_HINT_DISTANCE_METERS = 250.0;
    private static final double MAX_HINT_DISTANCE_METERS = 1500.0;
    private static final double MIN_HINT_SPACING_METERS = 180.0;
    private static final int MAX_AI_SUB_SPOTS = 15;

    private final TourApiService tourApiService;
    private final GeminiAiService geminiAiService;
    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class MissionGenerateRequest {
        private Map<String, String> targetSpot;
        private List<Map<String, String>> candidateSpots; // 프론트에서 넘어오지 않아도 구조 유지를 위해 둡니다.
    }

    /**
     * 1. 후보지 조회: TourAPI를 이용해 "역사적 장소(메인 목적지)" 후보만 검색하여 반환합니다.
     */
    @GetMapping("/candidates")
    public ResponseEntity<?> getHistoricalCandidates(@RequestParam double lat,
                                                     @RequestParam double lng,
                                                     @RequestParam(defaultValue = "2000") int radius) {
        log.info("📍 역사적 메인 목적지 후보 검색 요청: lat={}, lng={}", lat, lng);
        try {
            List<Map<String, String>> historicalSites = tourApiService.fetchHistoricalPlaces(lat, lng, 2000);
            if (historicalSites == null || historicalSites.isEmpty()) {
                return ResponseEntity.badRequest().body("주변 반경에 역사적 장소(관광지) 데이터가 없습니다.");
            }
            return ResponseEntity.ok(historicalSites);
        } catch (Exception e) {
            log.error("🚨 후보지 검색 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body("후보지 검색 실패: " + e.getMessage());
        }
    }

    /**
     * 2. AI 작전 수립: 선택된 역사적 장소를 기반으로, 주변 골목 상권을 백엔드에서 자동 수집하여 AI에게 기획을 맡깁니다.
     */
    @PostMapping("/generate-selected")
    public ResponseEntity<?> generateMissionByAi(@RequestBody MissionGenerateRequest request) {
        log.info("🤖 AI 작전 수립 파이프라인 가동 시작...");
        try {
            Map<String, String> targetSpot = request.getTargetSpot();
            if (targetSpot == null || !targetSpot.containsKey("mapY") || !targetSpot.containsKey("mapX")) {
                return ResponseEntity.badRequest().body("목적지(Target Spot) 데이터가 없거나 좌표가 누락되었습니다.");
            }

            double tLat = Double.parseDouble(targetSpot.get("mapY"));
            double tLng = Double.parseDouble(targetSpot.get("mapX"));

            String[] keywords = {"카페", "시장", "공원", "서점", "문화", "기념", "박물관", "전시", "산책"};
            List<Map<String, String>> localSpots = new ArrayList<>();
            for (String kw : keywords) {
                List<Map<String, String>> spots = tourApiService.fetchNearbyLocalPOIs(tLat, tLng, 1600, kw);
                if (spots != null && !spots.isEmpty()) {
                    localSpots.addAll(spots);
                }
            }

            List<Map<String, String>> subSpots = selectHintCandidates(localSpots, tLat, tLng);

            // 🚨 [타입 변환 로직 추가] GeminiAiService가 Map<String, Object>를 요구하므로 맞춰서 변환합니다.
            Map<String, Object> targetSpotObj = new HashMap<>(targetSpot);
            List<Map<String, Object>> subSpotsObj = subSpots.stream()
                    .map(spot -> new HashMap<String, Object>(spot))
                    .collect(Collectors.toList());

            // AI에게 최종 목적지(역사적 장소)와 경유지 후보군(골목 상권)을 함께 전달하여 스토리를 짜도록 지시합니다.
            String aiRawResponse = geminiAiService.generateCourseWithTarget(targetSpotObj, subSpotsObj);
            if (aiRawResponse == null || aiRawResponse.isBlank()) return ResponseEntity.internalServerError().body("AI 응답 없음");

            // JSON 파싱 및 DB 저장
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
                    // 서브 미션은 clue(단서)를, 최종 미션은 answerKeyword(진짜 정답)를 가집니다.
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

    @DeleteMapping("/regions/{regionId}")
    @Transactional
    public ResponseEntity<?> deleteRegion(@PathVariable Long regionId) {
        log.info("🗑️ 작전 파기 요청 수신. Region ID: {}", regionId);
        try {
            List<Mission> missions = missionRepository.findByRegionId(regionId);
            if (!missions.isEmpty()) {
                missionRepository.deleteAll(missions);
                log.info("   - 하위 미션 데이터 {}개 삭제 완료", missions.size());
            }

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

    private List<Map<String, String>> selectHintCandidates(List<Map<String, String>> spots, double targetLat, double targetLng) {
        List<Map<String, String>> deduped = spots.stream()
                .filter(spot -> spot.get("mapY") != null && spot.get("mapX") != null)
                .collect(Collectors.toMap(
                        this::spotIdentity,
                        spot -> spot,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(spot -> withDistance(spot, targetLat, targetLng))
                .filter(spot -> {
                    double distance = Double.parseDouble(spot.get("distanceMeters"));
                    return distance >= MIN_HINT_DISTANCE_METERS && distance <= MAX_HINT_DISTANCE_METERS;
                })
                .sorted(java.util.Comparator.comparingDouble(spot -> Double.parseDouble(spot.get("distanceMeters"))))
                .toList();

        List<Map<String, String>> selected = pickSpacedSpots(deduped, MIN_HINT_SPACING_METERS);
        if (selected.size() < 6) {
            selected = pickSpacedSpots(deduped, MIN_HINT_SPACING_METERS / 2);
        }
        return selected.stream().limit(MAX_AI_SUB_SPOTS).toList();
    }

    private List<Map<String, String>> pickSpacedSpots(List<Map<String, String>> spots, double minSpacingMeters) {
        List<Map<String, String>> selected = new ArrayList<>();
        for (Map<String, String> spot : spots) {
            boolean farEnoughFromOthers = selected.stream().allMatch(existing ->
                    distanceMeters(
                            Double.parseDouble(spot.get("mapY")),
                            Double.parseDouble(spot.get("mapX")),
                            Double.parseDouble(existing.get("mapY")),
                            Double.parseDouble(existing.get("mapX"))
                    ) >= minSpacingMeters
            );
            if (farEnoughFromOthers) {
                selected.add(spot);
            }
            if (selected.size() >= MAX_AI_SUB_SPOTS) {
                break;
            }
        }
        return selected;
    }

    private Map<String, String> withDistance(Map<String, String> spot, double targetLat, double targetLng) {
        Map<String, String> copied = new HashMap<>(spot);
        double lat = Double.parseDouble(copied.get("mapY"));
        double lng = Double.parseDouble(copied.get("mapX"));
        copied.put("distanceMeters", String.valueOf(Math.round(distanceMeters(targetLat, targetLng, lat, lng))));
        return copied;
    }

    private String spotIdentity(Map<String, String> spot) {
        return (spot.getOrDefault("title", "") + "|" + spot.getOrDefault("address", "")).trim();
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusMeters = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
