package com.operation.seoul.game.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.service.GeminiAiService;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import com.operation.seoul.location.service.OperationAreaResolver;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/missions")
@RequiredArgsConstructor
public class AdminMissionController {

    private static final double MIN_HINT_DISTANCE_METERS = 350.0;
    private static final double MAX_HINT_DISTANCE_METERS = 1800.0;
    private static final double MIN_HINT_SPACING_METERS = 260.0;
    private static final double HINT_DISTANCE_BUCKET_METERS = 300.0;
    private static final double MAX_AI_COORDINATE_SNAP_METERS = 120.0;
    private static final double MAX_HINT_WALK_DISTANCE_METERS = 2600.0;
    private static final double MAX_WALK_TO_STRAIGHT_RATIO = 1.8;
    private static final int MAX_ROUTE_CHECK_CANDIDATES = 40;
    private static final int MAX_AI_SUB_SPOTS = 15;

    private final TourApiService tourApiService;
    private final GeminiAiService geminiAiService;
    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final OperationAreaResolver operationAreaResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class MissionGenerateRequest {
        private Map<String, String> targetSpot;
        private List<Map<String, String>> candidateSpots;
        private String areaCode;
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> getHistoricalCandidates(@RequestParam double lat,
                                                     @RequestParam double lng,
                                                     @RequestParam(defaultValue = "2000") int radius) {
        try {
            List<Map<String, String>> historicalSites = tourApiService.fetchHistoricalPlaces(lat, lng, radius);
            if (historicalSites == null || historicalSites.isEmpty()) {
                return ResponseEntity.badRequest().body("주변 반경에 역사 관광지 데이터가 없습니다.");
            }
            return ResponseEntity.ok(historicalSites);
        } catch (Exception e) {
            log.error("Candidate search failed", e);
            return ResponseEntity.internalServerError().body("후보지 검색 실패: " + e.getMessage());
        }
    }

    @PostMapping("/generate-selected")
    public ResponseEntity<?> generateMissionByAi(@RequestBody MissionGenerateRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body("요청 본문이 필요합니다.");
            }

            Map<String, String> targetSpot = request.getTargetSpot();
            if (!hasUsableCoordinates(targetSpot)) {
                return ResponseEntity.badRequest().body("목적지 좌표가 필요합니다.");
            }

            double targetLat = Double.parseDouble(targetSpot.get("mapY"));
            double targetLng = Double.parseDouble(targetSpot.get("mapX"));
            String areaCode = operationAreaResolver.resolveAreaCode(targetLat, targetLng, request.getAreaCode());

            String[] keywords = {"카페", "시장", "공원", "서점", "문화", "기념", "박물관", "전시", "역사", "광장", "골목"};
            List<Map<String, String>> localSpots = new ArrayList<>();
            for (String keyword : keywords) {
                List<Map<String, String>> spots = tourApiService.fetchNearbyLocalPOIs(targetLat, targetLng, 1900, keyword);
                if (spots != null && !spots.isEmpty()) {
                    localSpots.addAll(spots);
                }
            }

            List<Map<String, String>> hintPool = new ArrayList<>(localSpots);
            List<Map<String, String>> subSpots = selectHintCandidates(hintPool, targetLat, targetLng);
            if (subSpots.size() < 3) {
                List<Map<String, String>> fallbackSpots = buildCandidateFallbackSpots(
                        request.getCandidateSpots(),
                        targetSpot,
                        targetLat,
                        targetLng
                );
                hintPool.addAll(fallbackSpots);
                subSpots = selectHintCandidates(hintPool, targetLat, targetLng);
                if (subSpots.size() < 3) {
                    subSpots = selectClosestHintCandidates(hintPool, targetLat, targetLng);
                }
            }
            if (subSpots.size() < 3) {
                return ResponseEntity.badRequest().body("최종 목적지 주변에 사용할 수 있는 힌트 지점이 3개 미만입니다. 기준 좌표를 조금 옮기거나 다른 장소를 선택해 주세요.");
            }

            Map<String, Object> targetSpotObj = new HashMap<>(targetSpot);
            List<Map<String, Object>> subSpotsObj = subSpots.stream()
                    .map(spot -> new HashMap<String, Object>(spot))
                    .collect(Collectors.toList());

            String aiRawResponse = geminiAiService.generateCourseWithTarget(targetSpotObj, subSpotsObj);
            if (aiRawResponse == null || aiRawResponse.isBlank()) {
                return ResponseEntity.internalServerError().body("AI 응답이 비어 있습니다.");
            }

            int startIndex = aiRawResponse.indexOf('{');
            int endIndex = aiRawResponse.lastIndexOf('}');
            if (startIndex == -1 || endIndex == -1) {
                return ResponseEntity.internalServerError().body("AI 응답에서 JSON을 찾지 못했습니다.");
            }

            JsonNode root = objectMapper.readTree(aiRawResponse.substring(startIndex, endIndex + 1));
            String finalAnswerKeyword = extractFinalAnswerKeyword(root.path("missions"));
            if (isInvalidFinalAnswerKeyword(finalAnswerKeyword, targetSpot)) {
                return ResponseEntity.badRequest().body("AI가 장소명에 가까운 최종 정답을 생성했습니다. 다시 생성해 주세요. answerKeyword=" + finalAnswerKeyword);
            }

            Region newRegion = new Region();
            newRegion.setAreaCode(areaCode);
            newRegion.setName(maskSecretKeyword(
                    root.path("regionName").asText("작전명 봉인된 현장"),
                    finalAnswerKeyword,
                    "작전명 봉인된 현장"
            ));
            newRegion.setDescription(maskSecretKeyword(
                    root.path("regionDescription").asText("현장에는 아직 공개되지 않은 사건의 흔적이 남아 있습니다. 주변 단서를 수집해 최종 진실을 추론하십시오."),
                    finalAnswerKeyword,
                    "현장에는 아직 공개되지 않은 사건의 흔적이 남아 있습니다. 주변 단서를 수집해 최종 진실을 추론하십시오."
            ));
            Region savedRegion = regionRepository.save(newRegion);

            JsonNode missionsNode = root.path("missions");
            if (missionsNode.isArray()) {
                for (JsonNode missionNode : missionsNode) {
                    boolean isFinal = missionNode.path("isFinal").asBoolean(false);
                    Map<String, String> sourceSpot = resolveSourceSpot(missionNode, targetSpot, subSpots, isFinal);

                    Mission mission = new Mission();
                    mission.setRegionId(savedRegion.getId());
                    mission.setTitle(resolveSafeTitle(missionNode, sourceSpot, finalAnswerKeyword, isFinal));
                    mission.setTargetLat(resolveLatitude(missionNode, sourceSpot));
                    mission.setTargetLng(resolveLongitude(missionNode, sourceSpot));
                    mission.setVisionKeyword(missionNode.path("visionKeyword").asText(""));
                    mission.setFinal(isFinal);
                    mission.setRadiusInMeters(isFinal ? 30.0 : 45.0);

                    if (isFinal) {
                        mission.setAnswerKeyword(finalAnswerKeyword);
                        mission.setRealStory(missionNode.path("realStory").asText(""));
                    } else {
                        mission.setClue(maskSecretKeyword(
                                missionNode.path("clue").asText("현장 단서는 최종 사건을 직접 말하지 않고 주변 정황만 남깁니다."),
                                finalAnswerKeyword,
                                "이 단서는 최종 사건을 직접 말하지 않고, 당시의 긴장과 선택의 흔적만 남깁니다."
                        ));
                    }

                    missionRepository.save(mission);
                }
            }
            return ResponseEntity.ok("AI 작전 생성 완료 [" + areaCode + "]: " + savedRegion.getName());
        } catch (Exception e) {
            log.error("Mission generation failed", e);
            return ResponseEntity.internalServerError().body("작전 생성 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/regions/{regionId}")
    @Transactional
    public ResponseEntity<?> deleteRegion(@PathVariable Long regionId) {
        try {
            List<Mission> missions = missionRepository.findByRegionId(regionId);
            if (!missions.isEmpty()) {
                missionRepository.deleteAll(missions);
            }

            if (regionRepository.existsById(regionId)) {
                regionRepository.deleteById(regionId);
                return ResponseEntity.ok("작전 데이터가 삭제되었습니다.");
            }
            return ResponseEntity.status(404).body("존재하지 않는 작전입니다.");
        } catch (Exception e) {
            log.error("Region delete failed", e);
            return ResponseEntity.internalServerError().body("작전 삭제 실패: " + e.getMessage());
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

    private boolean isInvalidFinalAnswerKeyword(String answerKeyword, Map<String, String> targetSpot) {
        String normalizedAnswer = normalizeForSecretCheck(answerKeyword);
        if (normalizedAnswer.isBlank() || normalizedAnswer.equals(normalizeForSecretCheck("정답누락"))) {
            return true;
        }

        String targetTitle = normalizeForSecretCheck(targetSpot.getOrDefault("title", ""));
        if (!targetTitle.isBlank()
                && (normalizedAnswer.equals(targetTitle)
                || targetTitle.contains(normalizedAnswer)
                || normalizedAnswer.contains(targetTitle))) {
            return true;
        }
        return isCommonPlaceOrPersonAnswer(normalizedAnswer);
    }

    private boolean isCommonPlaceOrPersonAnswer(String normalizedAnswer) {
        Set<String> blockedAnswers = Set.of(
                normalizeForSecretCheck("고종"),
                normalizeForSecretCheck("명성황후"),
                normalizeForSecretCheck("덕수궁"),
                normalizeForSecretCheck("경복궁"),
                normalizeForSecretCheck("경희궁"),
                normalizeForSecretCheck("광화문"),
                normalizeForSecretCheck("숭례문"),
                normalizeForSecretCheck("흥인지문"),
                normalizeForSecretCheck("서울"),
                normalizeForSecretCheck("정동"),
                normalizeForSecretCheck("권력"),
                normalizeForSecretCheck("러시아공사관"),
                normalizeForSecretCheck("공사관"),
                normalizeForSecretCheck("황제"),
                normalizeForSecretCheck("왕")
        );
        return blockedAnswers.contains(normalizedAnswer);
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
                .filter(this::hasUsableCoordinates)
                .collect(Collectors.toMap(
                        this::spotIdentity,
                        spot -> spot,
                        (first, ignored) -> first,
                        LinkedHashMap::new
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

    private List<Map<String, String>> buildCandidateFallbackSpots(
            List<Map<String, String>> candidateSpots,
            Map<String, String> targetSpot,
            double targetLat,
            double targetLng) {
        if (candidateSpots == null || candidateSpots.isEmpty()) {
            return List.of();
        }

        return candidateSpots.stream()
                .filter(this::hasUsableCoordinates)
                .filter(spot -> !isSameSpot(spot, targetSpot, targetLat, targetLng))
                .map(spot -> {
                    Map<String, String> copied = new HashMap<>(spot);
                    copied.putIfAbsent("source", "CandidateFallback");
                    return copied;
                })
                .toList();
    }

    private List<Map<String, String>> selectClosestHintCandidates(List<Map<String, String>> spots, double targetLat, double targetLng) {
        return spots.stream()
                .filter(this::hasUsableCoordinates)
                .collect(Collectors.toMap(
                        this::spotIdentity,
                        spot -> spot,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(spot -> withDistance(spot, targetLat, targetLng))
                .filter(spot -> Double.parseDouble(spot.get("distanceMeters")) > 50.0)
                .sorted(java.util.Comparator.comparingDouble(spot -> Double.parseDouble(spot.get("distanceMeters"))))
                .limit(MAX_AI_SUB_SPOTS)
                .toList();
    }

    private boolean hasUsableCoordinates(Map<String, String> spot) {
        return spot != null
                && parseCoordinate(spot.get("mapY")) != null
                && parseCoordinate(spot.get("mapX")) != null;
    }

    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isSameSpot(Map<String, String> spot, Map<String, String> targetSpot, double targetLat, double targetLng) {
        String spotIdentity = spotIdentity(spot);
        String targetIdentity = spotIdentity(targetSpot);
        if (!spotIdentity.isBlank() && spotIdentity.equals(targetIdentity)) {
            return true;
        }

        double spotLat = Double.parseDouble(spot.get("mapY"));
        double spotLng = Double.parseDouble(spot.get("mapX"));
        return distanceMeters(spotLat, spotLng, targetLat, targetLng) <= 20.0;
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
                        LinkedHashMap::new,
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
        if (spot == null) {
            return "";
        }

        String identity = (spot.getOrDefault("title", "") + "|" + spot.getOrDefault("address", "")).trim();
        if (!identity.isBlank()) {
            return identity;
        }
        return spot.getOrDefault("mapY", "") + "|" + spot.getOrDefault("mapX", "");
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
