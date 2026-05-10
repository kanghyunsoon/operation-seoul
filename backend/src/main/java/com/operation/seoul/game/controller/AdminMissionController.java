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
@RequiredArgsConstructor
public class AdminMissionController {

    private static final double MIN_HINT_DISTANCE_METERS = 250.0;
    private static final double MAX_HINT_DISTANCE_METERS = 1500.0;
    private static final double MIN_HINT_SPACING_METERS = 180.0;
    private static final double HINT_DISTANCE_BUCKET_METERS = 300.0;
    private static final double MAX_AI_COORDINATE_SNAP_METERS = 120.0;
    private static final double MAX_HINT_WALK_DISTANCE_METERS = 2300.0;
    private static final double MAX_WALK_TO_STRAIGHT_RATIO = 2.2;
    private static final int MAX_ROUTE_CHECK_CANDIDATES = 40;
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

            String finalAnswerKeyword = extractFinalAnswerKeyword(root.path("missions"));

            Region newRegion = new Region();
            newRegion.setName(maskSecretKeyword(
                    root.path("regionName").asText("작전명: 봉인된 현장"),
                    finalAnswerKeyword,
                    "작전명: 봉인된 현장"
            ));
            newRegion.setDescription(maskSecretKeyword(
                    root.path("regionDescription").asText("본부의 브리핑을 대기 중입니다."),
                    finalAnswerKeyword,
                    "현장에는 아직 공개되지 않은 역사적 사건의 흔적이 남아 있습니다. 주변 단서를 수집해 최종 진실을 유추하십시오."
            ));
            Region savedRegion = regionRepository.save(newRegion);

            JsonNode missionsNode = root.path("missions");
            if (missionsNode.isArray()) {
                for (JsonNode mNode : missionsNode) {
                    Mission mission = new Mission();
                    mission.setRegionId(savedRegion.getId());

                    boolean isFinal = mNode.path("isFinal").asBoolean(false);
                    Map<String, String> sourceSpot = resolveSourceSpot(mNode, targetSpot, subSpots, isFinal);

                    mission.setTitle(resolveSafeTitle(mNode, sourceSpot, finalAnswerKeyword, isFinal));
                    mission.setTargetLat(resolveLatitude(mNode, sourceSpot));
                    mission.setTargetLng(resolveLongitude(mNode, sourceSpot));
                    mission.setVisionKeyword(mNode.path("visionKeyword").asText(""));
                    mission.setFinal(isFinal);
                    mission.setRadiusInMeters(isFinal ? 30.0 : 50.0);
                    // 서브 미션은 clue(단서)를, 최종 미션은 answerKeyword(진짜 정답)를 가집니다.
                    if (isFinal) {
                        mission.setAnswerKeyword(mNode.path("answerKeyword").asText("정답누락"));
                    } else {
                        mission.setClue(maskSecretKeyword(
                                mNode.path("clue").asText("단서누락"),
                                finalAnswerKeyword,
                                "이 장소의 단서는 최종 사건을 직접 말하지 않고, 당시의 긴장과 선택을 우회적으로 가리킵니다."
                        ));
                    }

                    if (isFinal) {
                        mission.setRealStory(mNode.path("realStory").asText(""));
                    }
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

    private String extractFinalAnswerKeyword(JsonNode missionsNode) {
        if (missionsNode == null || !missionsNode.isArray()) {
            return "";
        }

        for (JsonNode missionNode : missionsNode) {
            if (missionNode.path("isFinal").asBoolean(false)) {
                return missionNode.path("answerKeyword").asText("");
            }
        }
        return "";
    }

    private String maskSecretKeyword(String text, String secretKeyword, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        if (secretKeyword == null || secretKeyword.isBlank()) {
            return text;
        }

        String normalizedText = normalizeForSecretCheck(text);
        String normalizedKeyword = normalizeForSecretCheck(secretKeyword);
        if (normalizedKeyword.isBlank() || !normalizedText.contains(normalizedKeyword)) {
            return text;
        }
        return fallback;
    }

    private String normalizeForSecretCheck(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
    }

    private Map<String, String> resolveSourceSpot(
            JsonNode missionNode,
            Map<String, String> targetSpot,
            List<Map<String, String>> subSpots,
            boolean isFinal) {
        if (isFinal) {
            return targetSpot;
        }

        String missionTitle = normalizeSpotName(missionNode.path("title").asText(""));
        if (!missionTitle.isBlank()) {
            for (Map<String, String> spot : subSpots) {
                String sourceTitle = normalizeSpotName(spot.getOrDefault("title", ""));
                if (sourceTitle.equals(missionTitle)
                        || sourceTitle.contains(missionTitle)
                        || missionTitle.contains(sourceTitle)) {
                    return spot;
                }
            }
        }

        double aiLat = missionNode.path("lat").asDouble(Double.NaN);
        double aiLng = missionNode.path("lng").asDouble(Double.NaN);
        if (!Double.isNaN(aiLat) && !Double.isNaN(aiLng)) {
            Map<String, String> closestSpot = subSpots.stream()
                    .filter(spot -> spot.get("mapY") != null && spot.get("mapX") != null)
                    .min(java.util.Comparator.comparingDouble(spot -> distanceMeters(
                            aiLat,
                            aiLng,
                            Double.parseDouble(spot.get("mapY")),
                            Double.parseDouble(spot.get("mapX"))
                    )))
                    .orElse(null);
            if (closestSpot != null && distanceMeters(
                    aiLat,
                    aiLng,
                    Double.parseDouble(closestSpot.get("mapY")),
                    Double.parseDouble(closestSpot.get("mapX"))
            ) <= MAX_AI_COORDINATE_SNAP_METERS) {
                return closestSpot;
            }
        }

        return null;
    }

    private String resolveSafeTitle(JsonNode missionNode, Map<String, String> sourceSpot, String secretKeyword, boolean isFinal) {
        String title = resolveTitle(missionNode, sourceSpot);
        return maskSecretKeyword(title, secretKeyword, isFinal ? "최종 현장" : "단서 지점");
    }

    private String resolveTitle(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("title") != null && !sourceSpot.get("title").isBlank()) {
            return sourceSpot.get("title");
        }
        return missionNode.path("title").asText("목적지");
    }

    private double resolveLatitude(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("mapY") != null && !sourceSpot.get("mapY").isBlank()) {
            return Double.parseDouble(sourceSpot.get("mapY"));
        }
        return missionNode.path("lat").asDouble(0.0);
    }

    private double resolveLongitude(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("mapX") != null && !sourceSpot.get("mapX").isBlank()) {
            return Double.parseDouble(sourceSpot.get("mapX"));
        }
        return missionNode.path("lng").asDouble(0.0);
    }

    private String normalizeSpotName(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
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

        List<Map<String, String>> walkableCandidates = filterWalkableCandidates(deduped, targetLat, targetLng);

        List<Map<String, String>> selected = pickDistributedSpots(walkableCandidates, MIN_HINT_SPACING_METERS);
        if (selected.size() < 6) {
            selected = pickDistributedSpots(walkableCandidates, MIN_HINT_SPACING_METERS / 2);
        }
        return selected.stream().limit(MAX_AI_SUB_SPOTS).toList();
    }

    private List<Map<String, String>> filterWalkableCandidates(List<Map<String, String>> spots, double targetLat, double targetLng) {
        List<Map<String, String>> checked = new ArrayList<>();
        int routeCheckedCount = 0;

        for (Map<String, String> spot : spots) {
            if (routeCheckedCount >= MAX_ROUTE_CHECK_CANDIDATES) {
                break;
            }
            routeCheckedCount++;

            double straightDistance = Double.parseDouble(spot.get("distanceMeters"));
            double spotLat = Double.parseDouble(spot.get("mapY"));
            double spotLng = Double.parseDouble(spot.get("mapX"));
            Double walkingDistance = tourApiService.fetchPedestrianDistanceMeters(targetLat, targetLng, spotLat, spotLng);

            if (walkingDistance == null) {
                continue;
            }

            if (walkingDistance <= MAX_HINT_WALK_DISTANCE_METERS
                    && walkingDistance / Math.max(straightDistance, 1.0) <= MAX_WALK_TO_STRAIGHT_RATIO) {
                Map<String, String> copied = new HashMap<>(spot);
                copied.put("walkingDistanceMeters", String.valueOf(Math.round(walkingDistance)));
                checked.add(copied);
            }
        }

        return checked.size() >= 6 ? checked : spots;
    }

    private List<Map<String, String>> pickSpacedSpots(List<Map<String, String>> spots, double minSpacingMeters) {
        List<Map<String, String>> selected = new ArrayList<>();
        for (Map<String, String> spot : spots) {
            if (isFarEnoughFromSelected(spot, selected, minSpacingMeters)) {
                selected.add(spot);
            }
            if (selected.size() >= MAX_AI_SUB_SPOTS) {
                break;
            }
        }
        return selected;
    }

    private List<Map<String, String>> pickDistributedSpots(List<Map<String, String>> spots, double minSpacingMeters) {
        Map<Integer, List<Map<String, String>>> buckets = spots.stream()
                .collect(Collectors.groupingBy(
                        this::distanceBucket,
                        java.util.LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));

        buckets.values().forEach(bucket ->
                bucket.sort(java.util.Comparator.comparingDouble(spot -> Double.parseDouble(spot.get("distanceMeters"))))
        );

        List<Map<String, String>> selected = new ArrayList<>();
        boolean added;
        int round = 0;
        do {
            added = false;
            for (List<Map<String, String>> bucket : buckets.values()) {
                if (round >= bucket.size()) {
                    continue;
                }

                Map<String, String> spot = bucket.get(round);
                if (isFarEnoughFromSelected(spot, selected, minSpacingMeters)) {
                    selected.add(spot);
                    added = true;
                }
                if (selected.size() >= MAX_AI_SUB_SPOTS) {
                    return selected;
                }
            }
            round++;
        } while (added || hasRemainingBucketItems(buckets, round));

        return selected.isEmpty() ? pickSpacedSpots(spots, minSpacingMeters) : selected;
    }

    private boolean hasRemainingBucketItems(Map<Integer, List<Map<String, String>>> buckets, int round) {
        for (List<Map<String, String>> bucket : buckets.values()) {
            if (round < bucket.size()) {
                return true;
            }
        }
        return false;
    }

    private int distanceBucket(Map<String, String> spot) {
        double distance = Double.parseDouble(spot.get("distanceMeters"));
        return (int) Math.floor((distance - MIN_HINT_DISTANCE_METERS) / HINT_DISTANCE_BUCKET_METERS);
    }

    private boolean isFarEnoughFromSelected(Map<String, String> spot, List<Map<String, String>> selected, double minSpacingMeters) {
        return selected.stream().allMatch(existing ->
                distanceMeters(
                        Double.parseDouble(spot.get("mapY")),
                        Double.parseDouble(spot.get("mapX")),
                        Double.parseDouble(existing.get("mapY")),
                        Double.parseDouble(existing.get("mapX"))
                ) >= minSpacingMeters
        );
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
