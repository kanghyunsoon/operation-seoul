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
import org.springframework.web.bind.annotation.PutMapping;
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

    // 힌트 후보는 최종 목적지에서 너무 가깝거나 멀지 않고, 후보끼리도 적당히 떨어지도록 제한합니다.
    private static final double MIN_HINT_DISTANCE_METERS = 350.0;
    private static final double MAX_HINT_DISTANCE_METERS = 1800.0;
    private static final double MIN_HINT_SPACING_METERS = 260.0;
    private static final double HINT_DISTANCE_BUCKET_METERS = 300.0;
    private static final double MAX_AI_COORDINATE_SNAP_METERS = 120.0;
    private static final double MAX_HINT_WALK_DISTANCE_METERS = 2600.0;
    private static final double MAX_WALK_TO_STRAIGHT_RATIO = 1.8;
    private static final int MAX_ROUTE_CHECK_CANDIDATES = 40;
    private static final int MAX_AI_SUB_SPOTS = 15;
    private static final int REGION_CANDIDATE_RADIUS_METERS = 18000;
    private static final int MAX_REGION_CANDIDATES = 60;
    private static final int MAX_EDIT_SPOT_CANDIDATES = 45;
    private static final String[] HINT_POI_KEYWORDS = {
            "카페", "시장", "공원", "서점", "문화", "기념", "박물관", "전시", "역사", "광장", "골목"
    };
    private static final Map<String, List<AreaSeed>> REGION_CANDIDATE_SEEDS = Map.of(
            "seoul", List.of(
                    new AreaSeed(37.5665, 126.9780),
                    new AreaSeed(37.5796, 126.9770),
                    new AreaSeed(37.5512, 126.9882)
            ),
            "gangwon", List.of(
                    new AreaSeed(37.8813, 127.7298),
                    new AreaSeed(37.7519, 128.8761),
                    new AreaSeed(38.2070, 128.5918),
                    new AreaSeed(37.3422, 127.9202)
            ),
            "chungbuk", List.of(
                    new AreaSeed(36.6424, 127.4890),
                    new AreaSeed(37.1326, 128.1910),
                    new AreaSeed(36.9910, 127.9259)
            ),
            "chungnam", List.of(
                    new AreaSeed(36.6588, 126.6728),
                    new AreaSeed(36.8151, 127.1139),
                    new AreaSeed(36.4465, 127.1190),
                    new AreaSeed(36.3326, 126.6129)
            ),
            "jeonbuk", List.of(
                    new AreaSeed(35.8242, 127.1480),
                    new AreaSeed(35.9677, 126.7366),
                    new AreaSeed(35.4164, 127.3904),
                    new AreaSeed(35.9483, 126.9576)
            ),
            "jeonnam", List.of(
                    new AreaSeed(34.8118, 126.3922),
                    new AreaSeed(34.7604, 127.6622),
                    new AreaSeed(35.0161, 126.7108),
                    new AreaSeed(34.9506, 127.4872)
            ),
            "gyeongbuk", List.of(
                    new AreaSeed(36.5684, 128.7294),
                    new AreaSeed(36.0190, 129.3435),
                    new AreaSeed(35.8562, 129.2247),
                    new AreaSeed(36.1195, 128.3446)
            ),
            "gyeongnam", List.of(
                    new AreaSeed(35.2285, 128.6811),
                    new AreaSeed(35.1796, 128.1076),
                    new AreaSeed(34.8544, 128.4332),
                    new AreaSeed(35.5038, 128.7466)
            ),
            "jeju", List.of(
                    new AreaSeed(33.4996, 126.5312),
                    new AreaSeed(33.2539, 126.5597),
                    new AreaSeed(33.4098, 126.2671),
                    new AreaSeed(33.4585, 126.9425)
            )
    );

    private record AreaSeed(double lat, double lng) {
    }

    private final TourApiService tourApiService;
    private final GeminiAiService geminiAiService;
    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final OperationAreaResolver operationAreaResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class MissionGenerateRequest {
        /** 관리자가 최종 목적지로 선택한 TourAPI 후보지입니다. */
        private Map<String, String> targetSpot;
        /** 같은 권역에서 함께 스캔된 후보지 목록입니다. 주변 POI가 부족할 때 fallback으로 씁니다. */
        private List<Map<String, String>> candidateSpots;
        /** 사용자가 홈 화면에서 선택한 권역 코드입니다. */
        private String areaCode;
    }

    @Data
    public static class MissionUpdateRequest {
        private String title;
        private String description;
        private Double targetLat;
        private Double targetLng;
        private Double radiusInMeters;
        private String visionKeyword;
        private String clue;
        private String answerKeyword;
        private Long chapterId;
        private Boolean finalMission;
        private String realStory;
    }

    @Data
    public static class MissionRecomposeRequest {
        private Map<String, String> selectedSpot;
    }

    @Data
    public static class RegionMetadataUpdateRequest {
        private String periodCode;
        private String themeCode;
    }

    public record AdminRegionMissionsResponse(
            Region region,
            List<AdminMissionResponse> missions
    ) {
    }

    public record AdminMissionGenerateResponse(
            String message,
            Region region,
            List<AdminMissionResponse> missions
    ) {
    }

    public record AdminMissionResponse(
            Long id,
            Long regionId,
            String title,
            String description,
            Double targetLat,
            Double targetLng,
            Double radiusInMeters,
            String visionKeyword,
            String clue,
            String answerKeyword,
            Long chapterId,
            boolean finalMission,
            String realStory
    ) {
        public static AdminMissionResponse from(Mission mission) {
            return new AdminMissionResponse(
                    mission.getId(),
                    mission.getRegionId(),
                    mission.getTitle(),
                    mission.getDescription(),
                    mission.getTargetLat(),
                    mission.getTargetLng(),
                    mission.getRadiusInMeters(),
                    mission.getVisionKeyword(),
                    mission.getClue(),
                    mission.getAnswerKeyword(),
                    mission.getChapterId(),
                    mission.isFinal(),
                    mission.getRealStory()
            );
        }
    }

    /**
     * 현재 좌표 주변의 역사 관광지 후보를 조회합니다.
     * 초기 개발용 endpoint이며, 권역 단위 스캔은 region-candidates를 주로 사용합니다.
     */
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

    /**
     * 권역별 대표 시드 좌표를 여러 개 훑어 TourAPI 후보지를 수집합니다.
     * 중복 제거와 권역 폴리곤 필터를 거쳐 홈 관리자 모달에 표시할 후보만 반환합니다.
     */
    @GetMapping("/region-candidates")
    public ResponseEntity<?> getRegionHistoricalCandidates(@RequestParam(defaultValue = "seoul") String areaCode) {
        String normalizedAreaCode = operationAreaResolver.normalizeAreaCode(areaCode);
        List<AreaSeed> seeds = REGION_CANDIDATE_SEEDS.getOrDefault(
                normalizedAreaCode,
                REGION_CANDIDATE_SEEDS.get(OperationAreaResolver.DEFAULT_AREA_CODE)
        );

        try {
            Map<String, Map<String, String>> uniqueSites = new LinkedHashMap<>();
            for (AreaSeed seed : seeds) {
                try {
                    List<Map<String, String>> historicalSites = tourApiService.fetchHistoricalPlaces(
                            seed.lat(),
                            seed.lng(),
                            REGION_CANDIDATE_RADIUS_METERS
                    );

                    if (historicalSites == null || historicalSites.isEmpty()) {
                        continue;
                    }

                    for (Map<String, String> site : historicalSites) {
                        if (!hasUsableCoordinates(site)) {
                            continue;
                        }

                        double siteLat = Double.parseDouble(site.get("mapY"));
                        double siteLng = Double.parseDouble(site.get("mapX"));
                        if (!operationAreaResolver.isInsideAreaCode(normalizedAreaCode, siteLat, siteLng)) {
                            continue;
                        }

                        Map<String, String> copied = new HashMap<>(site);
                        copied.put("areaCode", normalizedAreaCode);
                        copied.put("seedLat", String.valueOf(seed.lat()));
                        copied.put("seedLng", String.valueOf(seed.lng()));
                        copied.put("seedDistanceMeters", String.valueOf(Math.round(distanceMeters(
                                seed.lat(),
                                seed.lng(),
                                siteLat,
                                siteLng
                        ))));

                        uniqueSites.putIfAbsent(spotIdentity(copied), copied);
                    }
                } catch (Exception seedError) {
                    log.warn(
                            "Region candidate seed scan skipped. areaCode={}, lat={}, lng={}",
                            normalizedAreaCode,
                            seed.lat(),
                            seed.lng(),
                            seedError
                    );
                }
            }

            List<Map<String, String>> candidates = uniqueSites.values().stream()
                    .sorted(java.util.Comparator.comparing(spot -> spot.getOrDefault("title", "")))
                    .limit(MAX_REGION_CANDIDATES)
                    .toList();

            if (candidates.isEmpty()) {
                return ResponseEntity.badRequest().body("선택 지역에서 TourAPI 후보지를 찾지 못했습니다.");
            }

            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            log.error("Region candidate search failed. areaCode={}", normalizedAreaCode, e);
            return ResponseEntity.internalServerError().body("지역 후보지 검색 실패: " + e.getMessage());
        }
    }

    /** 생성된 작전 카드 안의 미션 원본 목록을 관리자 편집 화면에 제공합니다. */
    @GetMapping("/regions/{regionId}")
    public ResponseEntity<?> getRegionMissionsForAdmin(@PathVariable Long regionId) {
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            return ResponseEntity.status(404).body("존재하지 않는 작전입니다.");
        }

        List<AdminMissionResponse> missions = missionRepository.findByRegionId(regionId).stream()
                .map(AdminMissionResponse::from)
                .toList();

        return ResponseEntity.ok(new AdminRegionMissionsResponse(region, missions));
    }

    /** 관리자가 힌트 미션 위치를 고를 수 있도록 최종 지점 주변 후보 스팟을 제공합니다. */
    @GetMapping("/regions/{regionId}/spot-candidates")
    public ResponseEntity<?> getMissionSpotCandidates(@PathVariable Long regionId) {
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            return ResponseEntity.status(404).body("존재하지 않는 작전입니다.");
        }

        List<Mission> missions = missionRepository.findByRegionId(regionId);
        Mission anchorMission = resolveAnchorMission(missions);
        if (anchorMission == null || !isValidLatitude(anchorMission.getTargetLat()) || !isValidLongitude(anchorMission.getTargetLng())) {
            return ResponseEntity.badRequest().body("후보 스팟을 찾을 기준 미션 좌표가 없습니다.");
        }

        return ResponseEntity.ok(buildEditableSpotCandidates(missions, anchorMission));
    }

    /** 작전 카드의 대표 시대/테마 메타데이터를 수정합니다. */
    @PutMapping("/regions/{regionId}/metadata")
    @Transactional
    public ResponseEntity<?> updateRegionMetadata(@PathVariable Long regionId,
                                                  @RequestBody RegionMetadataUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("수정할 메타데이터가 필요합니다.");
        }
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            return ResponseEntity.status(404).body("존재하지 않는 작전입니다.");
        }

        region.setPeriodCode(normalizeMetadataCode(request.getPeriodCode(), "mixed"));
        region.setThemeCode(normalizeMetadataCode(request.getThemeCode(), "mystery"));
        regionRepository.update(region);

        List<AdminMissionResponse> missions = missionRepository.findByRegionId(regionId).stream()
                .map(AdminMissionResponse::from)
                .toList();
        return ResponseEntity.ok(new AdminRegionMissionsResponse(region, missions));
    }

    /** AI가 생성한 개별 미션을 삭제 없이 즉시 수정합니다. */
    @PutMapping("/{missionId}")
    @Transactional
    public ResponseEntity<?> updateMission(@PathVariable Long missionId,
                                           @RequestBody MissionUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("수정할 미션 정보가 필요합니다.");
        }

        String validationError = validateMissionUpdate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        Mission mission = missionRepository.findById(missionId).orElse(null);
        if (mission == null) {
            return ResponseEntity.status(404).body("존재하지 않는 미션입니다.");
        }

        mission.setTitle(normalizeEditableText(request.getTitle()));
        mission.setDescription(normalizeEditableText(request.getDescription()));
        mission.setTargetLat(request.getTargetLat());
        mission.setTargetLng(request.getTargetLng());
        mission.setRadiusInMeters(request.getRadiusInMeters());
        mission.setVisionKeyword(normalizeEditableText(request.getVisionKeyword()));
        mission.setClue(normalizeEditableText(request.getClue()));
        mission.setAnswerKeyword(normalizeEditableText(request.getAnswerKeyword()));
        mission.setChapterId(request.getChapterId());
        if (request.getFinalMission() != null) {
            mission.setFinal(request.getFinalMission());
        }
        mission.setRealStory(normalizeEditableText(request.getRealStory()));

        missionRepository.update(mission);
        return ResponseEntity.ok(AdminMissionResponse.from(mission));
    }

    /** 선택한 스팟 좌표를 적용하고, 해당 힌트 미션의 설명/단서만 AI로 다시 씁니다. */
    @PostMapping("/{missionId}/recompose")
    @Transactional
    public ResponseEntity<?> recomposeMissionWithSpot(@PathVariable Long missionId,
                                                      @RequestBody MissionRecomposeRequest request) {
        if (request == null || !hasUsableCoordinates(request.getSelectedSpot())) {
            return ResponseEntity.badRequest().body("재구성할 스팟 좌표가 필요합니다.");
        }

        Mission mission = missionRepository.findById(missionId).orElse(null);
        if (mission == null) {
            return ResponseEntity.status(404).body("존재하지 않는 미션입니다.");
        }
        if (mission.isFinal()) {
            return ResponseEntity.badRequest().body("최종 미션은 힌트 미션 재구성 대상이 아닙니다.");
        }

        Region region = regionRepository.findById(mission.getRegionId()).orElse(null);
        if (region == null) {
            return ResponseEntity.status(404).body("미션이 속한 작전을 찾을 수 없습니다.");
        }

        List<Mission> regionMissions = missionRepository.findByRegionId(region.getId());
        Mission finalMission = regionMissions.stream()
                .filter(Mission::isFinal)
                .findFirst()
                .orElse(null);
        String finalMissionTitle = finalMission == null ? "" : finalMission.getTitle();
        Map<String, String> selectedSpot = request.getSelectedSpot();
        Map<String, String> aiPatch = geminiAiService.generateHintMissionPatch(
                mission,
                region,
                selectedSpot,
                finalMissionTitle
        );
        if (aiPatch == null) {
            return ResponseEntity.internalServerError().body("AI 미션 재구성에 실패했습니다.");
        }

        String finalAnswerKeyword = finalMission == null ? "" : finalMission.getAnswerKeyword();
        mission.setTitle(normalizeEditableText(selectedSpot.getOrDefault("title", mission.getTitle())));
        mission.setTargetLat(Double.parseDouble(selectedSpot.get("mapY")));
        mission.setTargetLng(Double.parseDouble(selectedSpot.get("mapX")));
        mission.setRadiusInMeters(45.0);
        mission.setVisionKeyword(sanitizeRecomposedText(
                normalizeEditableText(aiPatch.get("visionKeyword")),
                finalAnswerKeyword,
                finalMissionTitle,
                mission.getVisionKeyword()
        ));
        mission.setDescription(sanitizeRecomposedText(
                normalizeEditableText(aiPatch.get("description")),
                finalAnswerKeyword,
                finalMissionTitle,
                mission.getDescription()
        ));
        mission.setClue(sanitizeRecomposedText(
                normalizeEditableText(aiPatch.get("clue")),
                finalAnswerKeyword,
                finalMissionTitle,
                mission.getClue()
        ));

        missionRepository.update(mission);
        return ResponseEntity.ok(AdminMissionResponse.from(mission));
    }

    /**
     * 관리자가 선택한 최종 목적지를 기준으로 주변 힌트 후보를 보강하고 Gemini 작전을 생성합니다.
     * AI 응답은 정답 노출 검사를 통과한 뒤 Region/Mission 엔티티로 저장됩니다.
     */
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

            List<Map<String, String>> localSpots = new ArrayList<>();
            for (String keyword : HINT_POI_KEYWORDS) {
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
            newRegion.setPeriodCode(resolveDefaultPeriodCode(targetSpot, root));
            newRegion.setThemeCode(resolveDefaultThemeCode(targetSpot, root));
            newRegion.setName(maskSecretKeyword(
                    root.path("regionName").asText("작전명 봉인된 현장"),
                    finalAnswerKeyword,
                    "작전명 봉인된 현장"
            ));
            newRegion.setDescription(maskSecretKeyword(
                    root.path("regionDescription").asText("오래된 기록을 정리하던 조사원이 같은 날짜가 서로 다른 이름으로 남아 있다는 사실을 발견했다. 한 기록은 사건이 끝났다고 말했고, 다른 기록은 아직 시작되지 않았다고 적혀 있었다.\n\n조사원은 두 기록 사이에 빠진 하루를 찾기 위해 현장으로 향했지만, 그날 밤 이후 연락이 끊겼다. 책상 위에는 찢긴 메모와 순서가 뒤바뀐 사진 몇 장만 남아 있었다.\n\n본편은 그가 찾지 못한 하루에서 시작된다. 흩어진 장면을 제자리에 놓고, 왜 누군가 이 이야기의 시작을 바꾸려 했는지 밝혀내라."),
                    finalAnswerKeyword,
                    "오래된 기록을 정리하던 조사원이 같은 날짜가 서로 다른 이름으로 남아 있다는 사실을 발견했다. 한 기록은 사건이 끝났다고 말했고, 다른 기록은 아직 시작되지 않았다고 적혀 있었다.\n\n조사원은 두 기록 사이에 빠진 하루를 찾기 위해 현장으로 향했지만, 그날 밤 이후 연락이 끊겼다. 책상 위에는 찢긴 메모와 순서가 뒤바뀐 사진 몇 장만 남아 있었다.\n\n본편은 그가 찾지 못한 하루에서 시작된다. 흩어진 장면을 제자리에 놓고, 왜 누군가 이 이야기의 시작을 바꾸려 했는지 밝혀내라."
            ));
            Region savedRegion = regionRepository.save(newRegion);
            List<AdminMissionResponse> savedMissions = new ArrayList<>();

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
                    mission.setDescription(maskSecretKeyword(
                            resolveMissionDescription(missionNode, isFinal),
                            finalAnswerKeyword,
                            isFinal
                                    ? "마지막 파일은 아직 완전히 열리지 않았다. 오래된 선택의 흔적이 결론 앞에서 신호를 낮춘다."
                                    : "본부가 끊어진 현장 신호를 포착했다. 작은 흔적 하나가 더 큰 사건의 윤곽을 밀어 올린다."
                    ));
                    mission.setFinal(isFinal);
                    mission.setRadiusInMeters(isFinal ? 30.0 : 45.0);

                    if (isFinal) {
                        mission.setAnswerKeyword(finalAnswerKeyword);
                        mission.setClue(maskSecretKeyword(
                                missionNode.path("clue").asText("마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 문 너머에서 오래된 결정이 아직 흔들린다."),
                                finalAnswerKeyword,
                                "마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 문 너머에서 오래된 결정이 아직 흔들린다."
                        ));
                        mission.setRealStory(missionNode.path("realStory").asText(""));
                    } else {
                        mission.setClue(maskSecretKeyword(
                                missionNode.path("clue").asText("낡은 표식은 다른 장소의 그림자를 먼저 비춘다. 흔들린 시대의 결재선이 아직 한쪽으로 기울어 있다."),
                                finalAnswerKeyword,
                                "낡은 표식은 다른 장소의 그림자를 먼저 비춘다. 흔들린 시대의 결재선이 아직 한쪽으로 기울어 있다."
                        ));
                    }

                    missionRepository.save(mission);
                    savedMissions.add(AdminMissionResponse.from(mission));
                }
            }
            String message = "AI 작전 생성 완료 [" + areaCode + "]: " + savedRegion.getName();
            return ResponseEntity.ok(new AdminMissionGenerateResponse(message, savedRegion, savedMissions));
        } catch (Exception e) {
            log.error("Mission generation failed", e);
            return ResponseEntity.internalServerError().body("작전 생성 실패: " + e.getMessage());
        }
    }

    /** 관리자 화면에서 작전 카드를 삭제할 때 Region과 하위 Mission을 함께 제거합니다. */
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

    private Mission resolveAnchorMission(List<Mission> missions) {
        if (missions == null || missions.isEmpty()) {
            return null;
        }
        return missions.stream()
                .filter(Mission::isFinal)
                .filter(mission -> isValidLatitude(mission.getTargetLat()) && isValidLongitude(mission.getTargetLng()))
                .findFirst()
                .orElseGet(() -> missions.stream()
                        .filter(mission -> isValidLatitude(mission.getTargetLat()) && isValidLongitude(mission.getTargetLng()))
                        .findFirst()
                        .orElse(null));
    }

    private List<Map<String, String>> buildEditableSpotCandidates(List<Mission> missions, Mission anchorMission) {
        double anchorLat = anchorMission.getTargetLat();
        double anchorLng = anchorMission.getTargetLng();
        List<Map<String, String>> spots = new ArrayList<>();

        for (String keyword : HINT_POI_KEYWORDS) {
            List<Map<String, String>> found = tourApiService.fetchNearbyLocalPOIs(anchorLat, anchorLng, 2400, keyword);
            if (found != null && !found.isEmpty()) {
                spots.addAll(found);
            }
        }

        for (Mission mission : missions) {
            if (!mission.isFinal() && isValidLatitude(mission.getTargetLat()) && isValidLongitude(mission.getTargetLng())) {
                spots.add(buildExistingMissionSpot(mission));
            }
        }

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
                .map(spot -> withDistance(spot, anchorLat, anchorLng))
                .filter(spot -> Double.parseDouble(spot.get("distanceMeters")) > 50.0)
                .sorted(java.util.Comparator.comparingDouble(spot -> Double.parseDouble(spot.get("distanceMeters"))))
                .limit(MAX_EDIT_SPOT_CANDIDATES)
                .toList();
    }

    private Map<String, String> buildExistingMissionSpot(Mission mission) {
        Map<String, String> spot = new HashMap<>();
        spot.put("title", normalizeEditableText(mission.getTitle()));
        spot.put("address", "현재 힌트 미션 위치");
        spot.put("mapY", String.valueOf(mission.getTargetLat()));
        spot.put("mapX", String.valueOf(mission.getTargetLng()));
        spot.put("source", "CurrentMission");
        return spot;
    }

    private String sanitizeRecomposedText(String text, String finalAnswerKeyword, String finalMissionTitle, String fallback) {
        String withoutAnswer = maskSecretKeyword(text, finalAnswerKeyword, fallback);
        return maskSecretKeyword(withoutAnswer, finalMissionTitle, fallback);
    }

    private String normalizeMetadataCode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "prehistoric" -> "ancient";
            case "japanese", "colonial", "empire", "opening", "opening_japanese", "modern_occupation" -> "empire_japanese";
            case "war", "security" -> "war_security";
            case "life", "daily", "daily_history" -> "daily_life";
            default -> normalized;
        };
    }

    private String resolveDefaultPeriodCode(Map<String, String> targetSpot, JsonNode root) {
        String aiPeriodCode = normalizeMetadataCode(root == null ? null : root.path("periodCode").asText(null), "");
        if (isSupportedPeriodCode(aiPeriodCode)) {
            return aiPeriodCode;
        }

        String context = buildClassificationContext(targetSpot, root);

        // 선사/고대는 화면 분류상 모두 고대로 묶습니다.
        if (containsAny(context,
                "구석기", "신석기", "청동기", "철기", "선사", "고대", "원삼국")) {
            return "ancient";
        }

        // 삼국/통일신라/발해
        if (containsAny(context,
                "고조선", "삼국", "백제", "신라", "고구려",
                "가야", "통일신라", "발해")) {
            return "three_kingdoms";
        }

        // 고려
        if (containsAny(context,
                "고려", "팔만대장경", "고려청자")) {
            return "goryeo";
        }

        // 조선
        if (containsAny(context,
                "조선", "한양", "궁궐", "왕실",
                "세종", "정조", "고종",
                "경복궁", "창덕궁", "덕수궁")) {
            return "joseon";
        }

        // 개항기/대한제국/일제강점기
        if (containsAny(context,
                "대한제국", "개항", "개화기",
                "일제", "강점", "독립운동",
                "의병", "3.1", "삼일",
                "1910", "1945")) {
            return "empire_japanese";
        }

        // 현대
        if (containsAny(context,
                "광복", "해방", "한국전쟁",
                "6.25", "민주화", "산업화",
                "올림픽", "월드컵", "디지털", "축제")) {
            return "contemporary";
        }

        // 근현대 일반
        if (containsAny(context,
                "근대", "근현대", "근대문화", "철도", "기차", "열차", "역사", "탄광", "광산",
                "항구", "항만", "등대", "시장 형성", "상권 형성")) {
            return "modern";
        }

        return "mixed";
    }

    private String resolveDefaultThemeCode(Map<String, String> targetSpot, JsonNode root) {
        String aiThemeCode = normalizeMetadataCode(root == null ? null : root.path("themeCode").asText(null), "");
        if (isSupportedThemeCode(aiThemeCode)) {
            return aiThemeCode;
        }

        String context = buildClassificationContext(targetSpot, root);
        if (containsAny(context, "궁", "궁궐", "왕실", "경복궁", "창덕궁", "덕수궁")) {
            return "royal";
        }
        if (containsAny(context, "독립운동", "의병", "만세", "대한제국", "일제강점기")) {
            return "independence";
        }
        if (containsAny(context, "전쟁", "안보", "전투", "피난", "군사", "6.25", "한국전쟁")) {
            return "war_security";
        }
        if (containsAny(context, "철도", "철길", "열차", "기차", "역", "레일", "탄광", "광산", "석탄", "산업")) {
            return "daily_life";
        }
        if (containsAny(context, "바다", "해안", "항구", "항만", "등대", "섬", "포구", "어촌", "선착장")) {
            return "nature";
        }
        if (containsAny(context, "관광", "축제", "테마", "콘텐츠", "마을 재생", "문화자원", "지역 산업")) {
            return "culture";
        }
        if (containsAny(context, "시장", "전통시장", "재래시장", "상점가", "거리", "골목", "상점")) {
            return "market";
        }
        if (containsAny(context, "미술관", "전시관", "공연", "예술", "전시", "박물관", "미술")) {
            return "culture";
        }
        if (containsAny(context, "한옥", "건축", "도시", "철도", "다리", "건물", "성곽")) {
            return "architecture";
        }
        if (containsAny(context, "공원", "숲", "산림", "등산", "국립공원", "호수", "강", "정원", "생태")) {
            return "nature";
        }
        if (containsAny(context, "생활", "마을", "주거", "음식", "골목")) {
            return "daily_life";
        }
        return "mystery";
    }

    private boolean isSupportedPeriodCode(String value) {
        return Set.of(
                "ancient",
                "three_kingdoms",
                "goryeo",
                "joseon",
                "empire_japanese",
                "modern",
                "contemporary",
                "mixed"
        ).contains(value);
    }

    private boolean isSupportedThemeCode(String value) {
        return Set.of(
                "royal",
                "independence",
                "war_security",
                "market",
                "culture",
                "architecture",
                "nature",
                "daily_life",
                "mystery"
        ).contains(value);
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(normalizeForSecretCheck(needle))) {
                return true;
            }
        }
        return false;
    }

    private String buildClassificationContext(Map<String, String> targetSpot, JsonNode root) {
        StringBuilder builder = new StringBuilder();
        if (targetSpot != null) {
            builder.append(targetSpot.getOrDefault("title", "")).append(' ')
                    .append(targetSpot.getOrDefault("category", "")).append(' ')
                    .append(targetSpot.getOrDefault("address", "")).append(' ')
                    .append(targetSpot.getOrDefault("overview", ""));
        }
        if (root != null) {
            builder.append(' ')
                    .append(root.path("regionName").asText("")).append(' ')
                    .append(root.path("regionDescription").asText(""));
            JsonNode missionsNode = root.path("missions");
            if (missionsNode.isArray()) {
                for (JsonNode missionNode : missionsNode) {
                    builder.append(' ')
                            .append(missionNode.path("title").asText("")).append(' ')
                            .append(missionNode.path("description").asText("")).append(' ')
                            .append(missionNode.path("clue").asText("")).append(' ')
                            .append(missionNode.path("realStory").asText(""));
                }
            }
        }
        return normalizeForSecretCheck(builder.toString());
    }

    private String validateMissionUpdate(MissionUpdateRequest request) {
        if (normalizeEditableText(request.getTitle()).isBlank()) {
            return "미션 제목은 필수입니다.";
        }
        if (!isValidLatitude(request.getTargetLat())) {
            return "목표 위도는 -90부터 90 사이의 숫자여야 합니다.";
        }
        if (!isValidLongitude(request.getTargetLng())) {
            return "목표 경도는 -180부터 180 사이의 숫자여야 합니다.";
        }
        if (request.getRadiusInMeters() == null
                || request.getRadiusInMeters().isNaN()
                || request.getRadiusInMeters().isInfinite()
                || request.getRadiusInMeters() <= 0) {
            return "인증 반경은 0보다 큰 숫자여야 합니다.";
        }
        return null;
    }

    private boolean isValidLatitude(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() && value >= -90.0 && value <= 90.0;
    }

    private boolean isValidLongitude(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() && value >= -180.0 && value <= 180.0;
    }

    private String normalizeEditableText(String value) {
        return value == null ? "" : value.trim();
    }

    /** AI 응답의 missions 배열에서 최종 미션 정답 키워드를 꺼냅니다. */
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

    /** 장소명, 공백, 유명하지만 무관한 사건명처럼 부적합한 최종 정답을 걸러냅니다. */
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
        if (isLikelyUnrelatedFamousEvent(normalizedAnswer, targetSpot)) {
            return true;
        }
        return isCommonPlaceOrPersonAnswer(normalizedAnswer);
    }

    /** 유명 사건이 특정 장소와 연결될 만한 anchor 없이 붙은 경우를 차단합니다. */
    private boolean isLikelyUnrelatedFamousEvent(String normalizedAnswer, Map<String, String> targetSpot) {
        String targetContext = normalizeForSecretCheck(String.join(" ",
                targetSpot.getOrDefault("title", ""),
                targetSpot.getOrDefault("addr1", ""),
                targetSpot.getOrDefault("addr2", ""),
                targetSpot.getOrDefault("address", ""),
                targetSpot.getOrDefault("overview", "")
        ));

        Map<String, Set<String>> eventAnchors = Map.of(
                normalizeForSecretCheck("을사늑약"), Set.of(
                        normalizeForSecretCheck("중명전"),
                        normalizeForSecretCheck("덕수궁"),
                        normalizeForSecretCheck("정동"),
                        normalizeForSecretCheck("대한제국"),
                        normalizeForSecretCheck("을사")
                ),
                normalizeForSecretCheck("아관파천"), Set.of(
                        normalizeForSecretCheck("러시아공사관"),
                        normalizeForSecretCheck("정동"),
                        normalizeForSecretCheck("덕수궁"),
                        normalizeForSecretCheck("고종")
                ),
                normalizeForSecretCheck("대한제국선포"), Set.of(
                        normalizeForSecretCheck("환구단"),
                        normalizeForSecretCheck("황궁우"),
                        normalizeForSecretCheck("덕수궁"),
                        normalizeForSecretCheck("대한제국")
                ),
                normalizeForSecretCheck("임오군란"), Set.of(
                        normalizeForSecretCheck("구식군대"),
                        normalizeForSecretCheck("별기군"),
                        normalizeForSecretCheck("운현궁"),
                        normalizeForSecretCheck("임오")
                )
        );

        Set<String> requiredAnchors = eventAnchors.get(normalizedAnswer);
        if (requiredAnchors == null) {
            return false;
        }
        return requiredAnchors.stream().noneMatch(targetContext::contains);
    }

    /** 장소명/인물명처럼 너무 직접적이거나 흔한 정답 후보를 차단합니다. */
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

    /** 최종 정답이 카드명/설명/힌트에 직접 노출되면 안전한 대체 문구로 바꿉니다. */
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

    /** 비밀 키워드 비교용으로 공백, 구두점, 기호를 제거합니다. */
    private String normalizeForSecretCheck(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
    }

    /** AI가 돌려준 미션을 실제 후보 좌표와 매칭해 좌표 환각을 줄입니다. */
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

    /** 정답 키워드가 제목에 들어가면 최종 현장/단서 지점 같은 안전한 제목으로 대체합니다. */
    private String resolveSafeTitle(JsonNode missionNode, Map<String, String> sourceSpot, String secretKeyword, boolean isFinal) {
        String title = resolveTitle(missionNode, sourceSpot);
        return maskSecretKeyword(title, secretKeyword, isFinal ? "최종 현장" : "단서 지점");
    }

    /** 실제 후보 장소명이 있으면 우선 사용하고, 없으면 AI 제목을 fallback으로 사용합니다. */
    private String resolveTitle(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("title") != null && !sourceSpot.get("title").isBlank()) {
            return sourceSpot.get("title");
        }
        return missionNode.path("title").asText("목적지");
    }

    /** AI 응답의 description/storyBeat 중 사용 가능한 미션 설명을 선택합니다. */
    private String resolveMissionDescription(JsonNode missionNode, boolean isFinal) {
        String description = missionNode.path("description").asText("");
        if (description.isBlank()) {
            description = missionNode.path("storyBeat").asText("");
        }
        if (!description.isBlank()) {
            return description;
        }
        return isFinal
                ? "마지막 파일은 아직 완전히 열리지 않았다. 오래된 선택의 흔적이 결론 앞에서 신호를 낮춘다."
                : "본부가 끊어진 현장 신호를 포착했다. 작은 흔적 하나가 더 큰 사건의 윤곽을 밀어 올린다.";
    }

    /** 실제 후보 좌표가 있으면 후보 위도를, 없으면 AI 응답 위도를 사용합니다. */
    private double resolveLatitude(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("mapY") != null && !sourceSpot.get("mapY").isBlank()) {
            return Double.parseDouble(sourceSpot.get("mapY"));
        }
        return missionNode.path("lat").asDouble(0.0);
    }

    /** 실제 후보 좌표가 있으면 후보 경도를, 없으면 AI 응답 경도를 사용합니다. */
    private double resolveLongitude(JsonNode missionNode, Map<String, String> sourceSpot) {
        if (sourceSpot != null && sourceSpot.get("mapX") != null && !sourceSpot.get("mapX").isBlank()) {
            return Double.parseDouble(sourceSpot.get("mapX"));
        }
        return missionNode.path("lng").asDouble(0.0);
    }

    /** 장소명 비교를 위해 공백/기호를 제거하고 소문자로 정규화합니다. */
    private String normalizeSpotName(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
    }

    /** 거리, 중복, 도보 접근성, 후보 간 간격 기준으로 AI에 넘길 힌트 후보를 선별합니다. */
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

    /** Kakao 주변 POI가 부족할 때 같은 TourAPI 후보 목록에서 최종 목적지가 아닌 지점을 보강합니다. */
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

    /** 엄격한 거리 조건을 만족하는 후보가 부족할 때 가장 가까운 유효 후보를 fallback으로 고릅니다. */
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

    /** TourAPI/Kakao 응답에 파싱 가능한 위도/경도가 모두 있는지 확인합니다. */
    private boolean hasUsableCoordinates(Map<String, String> spot) {
        return spot != null
                && parseCoordinate(spot.get("mapY")) != null
                && parseCoordinate(spot.get("mapX")) != null;
    }

    /** 문자열 좌표를 안전하게 Double로 변환합니다. 실패하면 null을 반환합니다. */
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

    /** 후보가 최종 목적지와 같은 장소인지 제목/주소 또는 근접 거리로 판단합니다. */
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

    /** Tmap 도보 거리를 확인해 실제로 걷기 어려운 후보를 제거합니다. */
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

    /** 후보 목록에서 선택된 지점끼리 최소 간격을 유지하며 순서대로 고릅니다. */
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

    /** 거리 구간별로 후보를 나눠 너무 한 방향/거리대에 몰리지 않게 고릅니다. */
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

    /** round-robin 분배 중 아직 처리할 bucket item이 남았는지 확인합니다. */
    private boolean hasRemainingBucketItems(Map<Integer, List<Map<String, String>>> buckets, int round) {
        for (List<Map<String, String>> bucket : buckets.values()) {
            if (round < bucket.size()) {
                return true;
            }
        }
        return false;
    }

    /** 최종 목적지와의 거리를 일정 간격 bucket으로 나눕니다. */
    private int distanceBucket(Map<String, String> spot) {
        double distance = Double.parseDouble(spot.get("distanceMeters"));
        return (int) Math.floor((distance - MIN_HINT_DISTANCE_METERS) / HINT_DISTANCE_BUCKET_METERS);
    }

    /** 새 후보가 이미 선택된 후보들과 충분히 떨어져 있는지 확인합니다. */
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

    /** 후보 Map에 최종 목적지와의 직선거리 값을 추가합니다. */
    private Map<String, String> withDistance(Map<String, String> spot, double targetLat, double targetLng) {
        Map<String, String> copied = new HashMap<>(spot);
        double lat = Double.parseDouble(copied.get("mapY"));
        double lng = Double.parseDouble(copied.get("mapX"));
        copied.put("distanceMeters", String.valueOf(Math.round(distanceMeters(targetLat, targetLng, lat, lng))));
        return copied;
    }

    /** 후보 중복 제거용 key입니다. 제목/주소가 없으면 좌표를 사용합니다. */
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

    /** 하버사인 공식으로 두 좌표 사이 직선거리를 미터 단위로 계산합니다. */
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
