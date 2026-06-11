package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.domain.AdminEpisodeProgressStats;
import com.operation.seoul.admin.episode.dto.AdminEpisodeDetailResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodeListResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodeUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminEvidenceUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminPartnerRewardUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminPlaceCandidateResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodePublishReadinessResponse;
import com.operation.seoul.admin.episode.dto.AdminPuzzleUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminRewardPayloadValidationRequest;
import com.operation.seoul.admin.episode.dto.AdminRewardPayloadValidationResponse;
import com.operation.seoul.admin.episode.dto.AdminSpotUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminSuspectUpdateRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftSaveRequest;
import com.operation.seoul.admin.episode.repository.AdminEpisodeRepository;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.domain.EpisodePartnerReward;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.MissionSpot;
import com.operation.seoul.episode.domain.Puzzle;
import com.operation.seoul.episode.domain.PuzzleHint;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.location.service.OperationAreaResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("SpellCheckingInspection")
@Service
@RequiredArgsConstructor
public class AdminEpisodeService {
    private static final Set<String> EPISODE_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> MARKER_TYPES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT", "FINAL");
    private static final Set<String> PUBLIC_MARKER_TYPES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT");
    private static final Set<String> CLUE_ROLES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT", "FINAL_PLACE");
    private static final Set<String> PUZZLE_TYPES = Set.of("OBSERVATION", "NUMBER_LOCK", "INITIAL_SOUND", "PATTERN", "STORY_COMBINATION");
    private static final Set<String> ANSWER_FORMATS = Set.of("TEXT", "NUMBER", "CHOICE", "CODE");
    private static final Set<String> REWARD_TYPES = Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE", "MEMO_UNLOCK", "EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "SUSPECT_UNLOCK", "SUSPECT_UPDATE");
    private static final Set<String> EVIDENCE_TYPES = Set.of("PHOTO", "MEMO", "NOTE", "DOCUMENT", "EVIDENCE", "SUSPECT_CLUE", "POST_IT", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE");
    private static final Set<String> PARTNER_REWARD_TYPES = Set.of("COUPON", "GIFT_CARD", "LOCAL_CURRENCY", "CAFE_DISCOUNT", "STAMP");
    private static final Set<String> PARTNER_REWARD_STATUSES = Set.of("DISABLED", "PLANNED", "ACTIVE", "ENDED");
    private static final int CANDIDATE_RADIUS_METERS = 18_000;
    private static final int MAX_CANDIDATES = 60;
    private static final int MIN_EPISODE_SPOTS = 3;
    private static final int MAX_EPISODE_SPOTS = 30;

    private static final Map<String, List<AreaSeed>> CANDIDATE_SEEDS = Map.of(
            "seoul", List.of(new AreaSeed(37.5665, 126.9780), new AreaSeed(37.5796, 126.9770), new AreaSeed(37.5512, 126.9882)),
            "capital_area", List.of(new AreaSeed(37.4563, 126.7052), new AreaSeed(37.2636, 127.0286), new AreaSeed(37.3943, 127.1112)),
            "gangwon", List.of(new AreaSeed(37.8813, 127.7298), new AreaSeed(37.7519, 128.8761), new AreaSeed(38.2070, 128.5918)),
            "chungbuk", List.of(new AreaSeed(36.6424, 127.4890), new AreaSeed(37.1326, 128.1910), new AreaSeed(36.9910, 127.9259)),
            "chungnam", List.of(new AreaSeed(36.6588, 126.6728), new AreaSeed(36.8151, 127.1139), new AreaSeed(36.4465, 127.1190)),
            "jeonbuk", List.of(new AreaSeed(35.8242, 127.1480), new AreaSeed(35.9677, 126.7366), new AreaSeed(35.4164, 127.3904)),
            "jeonnam", List.of(new AreaSeed(34.8118, 126.3922), new AreaSeed(34.7604, 127.6622), new AreaSeed(35.0161, 126.7108)),
            "gyeongbuk", List.of(new AreaSeed(36.5684, 128.7294), new AreaSeed(36.0190, 129.3435), new AreaSeed(35.8562, 129.2247)),
            "gyeongnam", List.of(new AreaSeed(35.2285, 128.6811), new AreaSeed(35.1796, 128.1076), new AreaSeed(34.8544, 128.4332)),
            "jeju", List.of(new AreaSeed(33.4996, 126.5312), new AreaSeed(33.2539, 126.5597), new AreaSeed(33.4098, 126.2671))
    );

    private final AdminEpisodeRepository adminEpisodeRepository;
    private final ObjectMapper objectMapper;
    private final TourApiService tourApiService;
    private final OperationAreaResolver operationAreaResolver;
    private final KakaoLocalCandidateService kakaoLocalCandidateService;

    public List<AdminEpisodeListResponse> getEpisodes() {
        return adminEpisodeRepository.findAllEpisodes().stream()
                .map(episode -> {
                    AdminEpisodeProgressStats stats = safeStats(episode.getId());
                    return AdminEpisodeListResponse.builder()
                            .id(episode.getId())
                            .title(episode.getTitle())
                            .subtitle(episode.getSubtitle())
                            .genre(episode.getGenre())
                            .era(episode.getEra())
                            .difficulty(episode.getDifficulty())
                            .status(episode.getStatus())
                            .finalAnswerType(episode.getFinalAnswerType())
                            .spotCount(adminEpisodeRepository.countSpots(episode.getId()))
                            .puzzleCount(adminEpisodeRepository.countPuzzles(episode.getId()))
                            .suspectCount(adminEpisodeRepository.countSuspects(episode.getId()))
                            .evidenceCount(adminEpisodeRepository.countEvidences(episode.getId()))
                            .partnerRewardCount(adminEpisodeRepository.countPartnerRewards(episode.getId()))
                            .totalPlayers(value(stats.getTotalPlayers()))
                            .clearedPlayers(value(stats.getClearedPlayers()))
                            .build();
                })
                .toList();
    }

    public List<AdminPlaceCandidateResponse> getPlaceCandidates(String areaCode) {
        String normalizedAreaCode = operationAreaResolver.normalizeAreaCode(areaCode);
        List<AreaSeed> seeds = CANDIDATE_SEEDS.getOrDefault(normalizedAreaCode, CANDIDATE_SEEDS.get(OperationAreaResolver.DEFAULT_AREA_CODE));
        Map<String, AdminPlaceCandidateResponse> unique = new LinkedHashMap<>();
        for (AreaSeed seed : seeds) {
            List<Map<String, String>> places = tourApiService.fetchHistoricalPlaces(seed.lat(), seed.lng(), CANDIDATE_RADIUS_METERS);
            if (places == null) {
                continue;
            }
            for (Map<String, String> place : places) {
                Double lat = parseDouble(place.get("mapY"));
                Double lng = parseDouble(place.get("mapX"));
                if (lat == null || lng == null || !operationAreaResolver.isInsideAreaCode(normalizedAreaCode, lat, lng)) {
                    continue;
                }
                String title = blank(place.get("title"), "이름 미정 장소");
                String address = place.get("address");
                String key = (title + "|" + address + "|" + lat + "|" + lng).toLowerCase(Locale.ROOT);
                unique.putIfAbsent(key, AdminPlaceCandidateResponse.builder()
                        .title(title)
                        .address(address)
                        .latitude(lat)
                        .longitude(lng)
                        .areaCode(normalizedAreaCode)
                        .source(place.getOrDefault("source", "TourAPI"))
                        .description(place.getOrDefault("overview", "공식 설명이 없어 관리자 확인이 필요한 장소입니다."))
                        .contentId(place.get("contentId"))
                        .build());
            }
        }
        return unique.values().stream()
                .sorted(java.util.Comparator.comparing(AdminPlaceCandidateResponse::getTitle))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    public AiEpisodeDraftRequest enrichSiteData(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SITE_ENRICHMENT_INPUT",
                    "사이트 보강을 위해서는 최소 1개 이상의 장소 입력이 필요합니다."
            );
        }

        AiEpisodeDraftRequest enriched = new AiEpisodeDraftRequest();

        enriched.setArea(request.getArea());
        enriched.setEra(request.getEra());
        enriched.setTheme(request.getTheme());
        enriched.setTargetAudience(request.getTargetAudience());
        enriched.setPlayTime(request.getPlayTime());

        enriched.setSelectedGenreId(request.getSelectedGenreId());
        enriched.setSelectedGenreName(request.getSelectedGenreName());

        enriched.setFinalAnswerKeywords(request.getFinalAnswerKeywords());
        enriched.setFinalAnswerKeywordItems(request.getFinalAnswerKeywordItems());

        enriched.setGenreCatalog(request.getGenreCatalog());
        enriched.setMissionPolicy(request.getMissionPolicy());
        enriched.setPuzzlePolicy(request.getPuzzlePolicy());

        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            places.add(enrichPlace(place));
        }

        enriched.setPlaces(places);

        return enriched;
    }

    private String selectedGenreName(AiEpisodeDraftRequest request) {
        if (request == null) {
            return "야외 스토리 미션";
        }

        if (!missing(request.getSelectedGenreName())) {
            return request.getSelectedGenreName().trim();
        }

        if (!missing(request.getSelectedGenreId())) {
            return request.getSelectedGenreId().trim();
        }

        if (!missing(request.getTheme())) {
            return request.getTheme().trim();
        }

        return "야외 스토리 미션";
    }

    private List<String> approvedFinalKeywords(AiEpisodeDraftRequest request) {
        if (request == null) {
            return List.of();
        }

        if (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty()) {
            return request.getFinalAnswerKeywordItems().stream()
                    .map(AiEpisodeDraftRequest.AnswerKeywordInput::getKeyword)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }

        if (request.getFinalAnswerKeywords() != null && !request.getFinalAnswerKeywords().isEmpty()) {
            return request.getFinalAnswerKeywords().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }

        return List.of();
    }

    public AdminEpisodeDetailResponse getEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);

        AdminEpisodeProgressStats stats = safeStats(episodeId);
        return AdminEpisodeDetailResponse.builder()
                .id(episode.getId())
                .title(episode.getTitle())
                .subtitle(episode.getSubtitle())
                .era(episode.getEra())
                .genre(episode.getGenre())
                .difficulty(episode.getDifficulty())
                .estimatedTime(episode.getEstimatedTime())
                .estimatedDistance(episode.getEstimatedDistance())
                .fictionSynopsis(episode.getFictionSynopsis())
                .finalAnswerType(episode.getFinalAnswerType())
                .finalAnswer(episode.getFinalAnswer())
                .finalAnswerAliases(episode.getFinalAnswerAliases())
                .finalQuestion(episode.getFinalQuestion())
                .finalTruthSummary(episode.getFinalTruthSummary())
                .actualHistorySummary(episode.getActualHistorySummary())
                .deductionSecretFacts(episode.getDeductionSecretFacts())
                .deductionForbiddenReveals(episode.getDeductionForbiddenReveals())
                .maxDeductionQuestions(episode.getMaxDeductionQuestions())
                .recommendedPlayers(episode.getRecommendedPlayers())
                .teamRoleGuide(episode.getTeamRoleGuide())
                .noticeText(episode.getNoticeText())
                .status(episode.getStatus())
                .progressStats(AdminEpisodeDetailResponse.ProgressStats.builder()
                        .totalPlayers(value(stats.getTotalPlayers()))
                        .inProgressPlayers(value(stats.getInProgressPlayers()))
                        .clearedPlayers(value(stats.getClearedPlayers()))
                        .build())
                .spots(adminEpisodeRepository.findSpots(episodeId).stream().map(this::toSpot).toList())
                .suspects(adminEpisodeRepository.findSuspects(episodeId).stream().map(this::toSuspect).toList())
                .evidences(adminEpisodeRepository.findEvidences(episodeId).stream().map(this::toEvidence).toList())
                .partnerRewards(adminEpisodeRepository.findPartnerRewards(episodeId).stream().map(this::toReward).toList())
                .build();
    }

    public AdminEpisodePublishReadinessResponse getPublishReadiness(Long episodeId) {
        Episode episode = requireEpisode(episodeId);
        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episodeId);
        AdminEpisodePublishReadinessResponse.Summary summary = AdminEpisodePublishReadinessResponse.Summary.builder()
                .spotCount(spots.size())
                .startCount(spots.stream().filter(spot -> "START".equals(spot.getMarkerType())).count())
                .answerHintCount(spots.stream().filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType())).count())
                .destinationHintCount(spots.stream().filter(spot -> "DESTINATION_HINT".equals(spot.getMarkerType())).count())
                .finalPlaceCount(spots.stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count())
                .finalCandidateCount(spots.stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count())
                .puzzleCount(adminEpisodeRepository.countPuzzles(episodeId))
                .suspectCount(adminEpisodeRepository.countSuspects(episodeId))
                .evidenceCount(adminEpisodeRepository.countEvidences(episodeId))
                .build();
        try {
            validatePublishReadiness(episode);
            return AdminEpisodePublishReadinessResponse.builder()
                    .ready(true)
                    .status(episode.getStatus())
                    .message("공개 가능한 상태입니다. AI 초안, 현장 데이터, 정답 노출 검사를 통과했습니다.")
                    .summary(summary)
                    .blockingIssues(List.of())
                    .checklist(publishChecklist())
                    .build();
        } catch (ApiException e) {
            if (!"EPISODE_PUBLISH_NOT_READY".equals(e.getCode())) {
                throw e;
            }
            return AdminEpisodePublishReadinessResponse.builder()
                    .ready(false)
                    .status(episode.getStatus())
                    .message("공개 전 차단 이슈를 먼저 수정해야 합니다.")
                    .summary(summary)
                    .blockingIssues(extractBlockingIssues(e.getMessage()))
                    .checklist(publishChecklist())
                    .build();
        }
    }
    public AdminEpisodeDetailResponse updateEpisode(Long episodeId, AdminEpisodeUpdateRequest request) {
        Episode episode = requireEpisode(episodeId);
        episode.setTitle(text(request.getTitle(), episode.getTitle()));
        episode.setSubtitle(text(request.getSubtitle(), episode.getSubtitle()));
        episode.setEra(text(request.getEra(), episode.getEra()));
        episode.setGenre(text(request.getGenre(), episode.getGenre()));
        episode.setDifficulty(text(request.getDifficulty(), episode.getDifficulty()));
        episode.setEstimatedTime(text(request.getEstimatedTime(), episode.getEstimatedTime()));
        episode.setEstimatedDistance(text(request.getEstimatedDistance(), episode.getEstimatedDistance()));
        episode.setFictionSynopsis(text(request.getFictionSynopsis(), episode.getFictionSynopsis()));
        episode.setFinalAnswerType(text(request.getFinalAnswerType(), episode.getFinalAnswerType()));
        episode.setFinalAnswer(text(request.getFinalAnswer(), episode.getFinalAnswer()));
        episode.setFinalAnswerAliases(text(request.getFinalAnswerAliases(), episode.getFinalAnswerAliases()));
        episode.setFinalQuestion(text(request.getFinalQuestion(), episode.getFinalQuestion()));
        episode.setFinalTruthSummary(text(request.getFinalTruthSummary(), episode.getFinalTruthSummary()));
        episode.setActualHistorySummary(text(request.getActualHistorySummary(), episode.getActualHistorySummary()));
        episode.setDeductionSecretFacts(text(request.getDeductionSecretFacts(), episode.getDeductionSecretFacts()));
        episode.setDeductionForbiddenReveals(text(request.getDeductionForbiddenReveals(), episode.getDeductionForbiddenReveals()));
        episode.setMaxDeductionQuestions(request.getMaxDeductionQuestions() == null ? episode.getMaxDeductionQuestions() : Math.max(1, request.getMaxDeductionQuestions()));
        episode.setRecommendedPlayers(text(request.getRecommendedPlayers(), episode.getRecommendedPlayers()));
        episode.setTeamRoleGuide(text(request.getTeamRoleGuide(), episode.getTeamRoleGuide()));
        episode.setNoticeText(text(request.getNoticeText(), episode.getNoticeText()));
        episode.setStatus(validateValue(
                text(request.getStatus(), episode.getStatus()),
                EPISODE_STATUSES,
                "INVALID_EPISODE_STATUS",
                "status는 DRAFT, PUBLISHED, ARCHIVED 중 하나여야 합니다."
        ));
        if ("PUBLISHED".equals(episode.getStatus())) {
            validatePublishReadiness(episode);
        }
        adminEpisodeRepository.updateEpisode(episode);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updateSpot(Long episodeId, Long spotId, AdminSpotUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminSpotUpdateRequest safeRequest = request == null ? new AdminSpotUpdateRequest() : request;

        MissionSpot spot = adminEpisodeRepository.findSpots(episodeId).stream()
                .filter(item -> spotId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SPOT_NOT_FOUND",
                        "조사 지점을 찾을 수 없습니다."
                ));

        spot.setPlaceName(text(safeRequest.getPlaceName(), spot.getPlaceName()));
        spot.setAddress(text(safeRequest.getAddress(), spot.getAddress()));
        spot.setLatitude(safeRequest.getLatitude() == null ? spot.getLatitude() : safeRequest.getLatitude());
        spot.setLongitude(safeRequest.getLongitude() == null ? spot.getLongitude() : safeRequest.getLongitude());

        spot.setMarkerType(validateValue(
                text(safeRequest.getMarkerType(), spot.getMarkerType()),
                MARKER_TYPES,
                "INVALID_MARKER_TYPE",
                "지원하지 않는 markerType입니다."
        ));

        spot.setClueRole(validateValue(
                text(safeRequest.getClueRole(), spot.getClueRole()),
                CLUE_ROLES,
                "INVALID_CLUE_ROLE",
                "지원하지 않는 clueRole입니다."
        ));

        spot.setPublicMarkerType(validateValue(
                text(safeRequest.getPublicMarkerType(), spot.getPublicMarkerType()),
                PUBLIC_MARKER_TYPES,
                "INVALID_PUBLIC_MARKER_TYPE",
                "publicMarkerType으로 FINAL을 노출할 수 없습니다."
        ));

        spot.setStoryText(text(safeRequest.getStoryText(), spot.getStoryText()));
        spot.setArrivalRadius(safeRequest.getArrivalRadius() == null
                ? spot.getArrivalRadius()
                : Math.max(10.0, safeRequest.getArrivalRadius()));
        spot.setFieldVerified(safeRequest.getFieldVerified() == null ? spot.getFieldVerified() : safeRequest.getFieldVerified());
        spot.setFieldVerificationNote(text(safeRequest.getFieldVerificationNote(), spot.getFieldVerificationNote()));
        spot.setFinalPlace(safeRequest.getFinalPlace() == null ? spot.getFinalPlace() : safeRequest.getFinalPlace());

        if (Boolean.TRUE.equals(spot.getFinalPlace())) {
            spot.setMarkerType("FINAL");
            spot.setClueRole("FINAL_PLACE");
            spot.setPublicMarkerType("DESTINATION_HINT");
        }

        adminEpisodeRepository.updateSpot(spot);
        return getEpisode(episodeId);
    }

    @Transactional
    public AdminEpisodeDetailResponse createSpot(Long episodeId, AdminSpotUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminSpotUpdateRequest safeRequest = request == null ? new AdminSpotUpdateRequest() : request;

        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episodeId);

        if (spots.size() >= MAX_EPISODE_SPOTS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_SPOTS",
                    "장소는 최대 " + MAX_EPISODE_SPOTS + "개까지 추가할 수 있습니다."
            );
        }

        MissionSpot spot = new MissionSpot();
        spot.setEpisodeId(episodeId);
        spot.setPlaceName(text(safeRequest.getPlaceName(), "추가 조사 지점 " + (spots.size() + 1)));
        spot.setAddress(text(safeRequest.getAddress(), ""));
        spot.setLatitude(safeRequest.getLatitude() == null ? 37.5665 : safeRequest.getLatitude());
        spot.setLongitude(safeRequest.getLongitude() == null ? 126.9780 : safeRequest.getLongitude());

        spot.setMarkerType(validateValue(
                text(safeRequest.getMarkerType(), "ANSWER_HINT"),
                MARKER_TYPES,
                "INVALID_MARKER_TYPE",
                "지원하지 않는 markerType입니다."
        ));

        spot.setClueRole(validateValue(
                text(safeRequest.getClueRole(), toClueRole(spot.getMarkerType())),
                CLUE_ROLES,
                "INVALID_CLUE_ROLE",
                "지원하지 않는 clueRole입니다."
        ));

        spot.setPublicMarkerType(validateValue(
                text(safeRequest.getPublicMarkerType(), spot.getMarkerType()),
                PUBLIC_MARKER_TYPES,
                "INVALID_PUBLIC_MARKER_TYPE",
                "publicMarkerType으로 FINAL을 노출할 수 없습니다."
        ));

        spot.setStoryText(text(
                safeRequest.getStoryText(),
                "관리자 검수 후 이 지점의 현장 근거와 미션 파일을 연결하는 스토리 문구를 작성하세요."
        ));

        spot.setArrivalRadius(safeRequest.getArrivalRadius() == null
                ? 50.0
                : Math.max(10.0, safeRequest.getArrivalRadius()));

        spot.setFieldVerified(Boolean.TRUE.equals(safeRequest.getFieldVerified()));
        spot.setFieldVerificationNote(text(safeRequest.getFieldVerificationNote(), null));
        spot.setFinalPlace(Boolean.TRUE.equals(safeRequest.getFinalPlace()));

        if (Boolean.TRUE.equals(spot.getFinalPlace())) {
            spot.setMarkerType("FINAL");
            spot.setClueRole("FINAL_PLACE");
            spot.setPublicMarkerType("DESTINATION_HINT");
        }

        adminEpisodeRepository.insertSpot(spot);

        Puzzle puzzle = new Puzzle();
        puzzle.setMissionSpotId(spot.getId());
        puzzle.setPuzzleType("OBSERVATION");
        puzzle.setQuestionText("관리자 검수 후 이 지점의 실제 현장 근거를 기준으로 퍼즐 질문을 작성하세요.");
        puzzle.setAnswer("검수필요");
        puzzle.setAnswerFormat("TEXT");
        puzzle.setRewardClue("story-clue-" + (spots.size() + 1));
        puzzle.setRewardPayload(null);
        puzzle.setDifficulty("NORMAL");

        adminEpisodeRepository.insertPuzzle(puzzle);

        adminEpisodeRepository.insertHint(puzzle.getId(), 1, "현장에서 실제 확인 가능한 표지, 숫자, 조형물, 문구를 먼저 기록하세요.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 2, "장소명 자체를 정답으로 쓰지 말고, 확인 가능한 짧은 근거어를 사용하세요.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 3, "공개 전 reward_payload와 해금 단서가 최종 정답을 노출하지 않는지 확인하세요.");

        return getEpisode(episodeId);
    }

    @Transactional
    public AdminEpisodeDetailResponse deleteSpot(Long episodeId, Long spotId) {
        requireEditableEpisode(episodeId);

        boolean exists = adminEpisodeRepository.findSpots(episodeId).stream()
                .anyMatch(spot -> spotId.equals(spot.getId()));

        if (!exists) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SPOT_NOT_FOUND",
                    "삭제하려는 조사 지점을 찾을 수 없습니다."
            );
        }

        adminEpisodeRepository.detachEvidencesBySpotId(spotId);
        adminEpisodeRepository.deleteHintsBySpotId(spotId);
        adminEpisodeRepository.deletePuzzlesBySpotId(spotId);
        adminEpisodeRepository.deleteSpot(spotId);

        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updatePuzzle(Long episodeId, Long puzzleId, AdminPuzzleUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminPuzzleUpdateRequest safeRequest = request == null ? new AdminPuzzleUpdateRequest() : request;

        Puzzle puzzle = adminEpisodeRepository.findPuzzle(puzzleId);

        if (puzzle == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PUZZLE_NOT_FOUND",
                    "퍼즐을 찾을 수 없습니다."
            );
        }

        boolean belongsToEpisode = adminEpisodeRepository.findSpots(episodeId).stream()
                .anyMatch(spot -> spot.getId().equals(puzzle.getMissionSpotId()));

        if (!belongsToEpisode) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PUZZLE_NOT_FOUND",
                    "해당 에피소드에 속한 퍼즐이 아닙니다."
            );
        }

        puzzle.setPuzzleType(validateValue(
                text(safeRequest.getPuzzleType(), puzzle.getPuzzleType()),
                PUZZLE_TYPES,
                "INVALID_PUZZLE_TYPE",
                "지원하지 않는 puzzleType입니다."
        ));

        puzzle.setQuestionText(text(safeRequest.getQuestionText(), puzzle.getQuestionText()));
        puzzle.setAnswer(text(safeRequest.getAnswer(), puzzle.getAnswer()));

        puzzle.setAnswerFormat(validateValue(
                text(safeRequest.getAnswerFormat(), puzzle.getAnswerFormat()),
                ANSWER_FORMATS,
                "INVALID_ANSWER_FORMAT",
                "지원하지 않는 answerFormat입니다."
        ));

        puzzle.setRewardClue(text(safeRequest.getRewardClue(), puzzle.getRewardClue()));
        puzzle.setRewardPayload(text(safeRequest.getRewardPayload(), puzzle.getRewardPayload()));

        AdminRewardPayloadValidationResponse validation =
                validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));

        if (!validation.isValid()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REWARD_PAYLOAD",
                    String.join(" / ", validation.getErrors())
            );
        }

        puzzle.setDifficulty(text(safeRequest.getDifficulty(), puzzle.getDifficulty()));
        adminEpisodeRepository.updatePuzzle(puzzle);

        if (safeRequest.getHints() != null) {
            adminEpisodeRepository.deleteHints(puzzleId);

            int level = 1;
            for (String hint : safeRequest.getHints()) {
                if (hint != null && !hint.isBlank()) {
                    adminEpisodeRepository.insertHint(puzzleId, level++, hint.trim());
                }

                if (level > 3) {
                    break;
                }
            }
        }

        return getEpisode(episodeId);
    }

    public AdminRewardPayloadValidationResponse validateRewardPayload(Long episodeId, AdminRewardPayloadValidationRequest request) {
        requireEpisode(episodeId);
        return validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(request == null ? null : request.getRewardPayload()));
    }

    public AdminEpisodeDetailResponse updateSuspect(Long episodeId, Long suspectId, AdminSuspectUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminSuspectUpdateRequest safeRequest = request == null ? new AdminSuspectUpdateRequest() : request;

        CaseSuspect suspect = adminEpisodeRepository.findSuspects(episodeId).stream()
                .filter(item -> suspectId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SUSPECT_NOT_FOUND",
                        "관계자 카드를 찾을 수 없습니다."
                ));

        suspect.setDisplayName(text(safeRequest.getDisplayName(), suspect.getDisplayName()));
        suspect.setAlias(text(safeRequest.getAlias(), suspect.getAlias()));
        suspect.setShortDescription(text(safeRequest.getShortDescription(), suspect.getShortDescription()));
        suspect.setPortraitImageUrl(text(safeRequest.getPortraitImageUrl(), suspect.getPortraitImageUrl()));
        suspect.setImagePrompt(ensureKoreanPersonPrompt(text(safeRequest.getImagePrompt(), suspect.getImagePrompt())));
        suspect.setRelationToVictim(text(safeRequest.getRelationToVictim(), suspect.getRelationToVictim()));
        suspect.setSuspiciousPoint(text(safeRequest.getSuspiciousPoint(), suspect.getSuspiciousPoint()));
        suspect.setAlibiSummary(text(safeRequest.getAlibiSummary(), suspect.getAlibiSummary()));
        suspect.setUnlockedByDefault(safeRequest.getUnlockedByDefault() == null ? suspect.getUnlockedByDefault() : safeRequest.getUnlockedByDefault());
        suspect.setDisplayOrder(safeRequest.getDisplayOrder() == null ? suspect.getDisplayOrder() : safeRequest.getDisplayOrder());

        adminEpisodeRepository.updateSuspect(suspect);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse createSuspect(Long episodeId, AdminSuspectUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminSuspectUpdateRequest safeRequest = request == null ? new AdminSuspectUpdateRequest() : request;

        int nextOrder = adminEpisodeRepository.countSuspects(episodeId) + 1;
        String alias = blank(safeRequest.getAlias(), "관계자 " + relationLabel(nextOrder));
        String displayName = blank(safeRequest.getDisplayName(), "추가 확인이 필요한 관계자");
        String shortDescription = blank(
                safeRequest.getShortDescription(),
                "관리자가 수동으로 추가한 관계자 카드입니다. 공개 전 역할과 단서 연결 방식을 보강하세요."
        );
        String relation = blank(
                safeRequest.getRelationToVictim(),
                "미션 파일 관계자"
        );
        String suspiciousPoint = blank(
                safeRequest.getSuspiciousPoint(),
                "동선, 기록, 단서 흐름 중 추가 확인이 필요한 지점이 있습니다."
        );
        String alibiSummary = blank(
                safeRequest.getAlibiSummary(),
                "해금된 미션 파일과 대조해 확인해야 합니다."
        );

        CaseSuspect suspect = new CaseSuspect();
        suspect.setEpisodeId(episodeId);
        suspect.setAlias(alias);
        suspect.setDisplayName(displayName);
        suspect.setShortDescription(shortDescription);
        suspect.setPortraitImageUrl(text(safeRequest.getPortraitImageUrl(), null));
        suspect.setImagePrompt(ensureKoreanPersonPrompt(blank(
                safeRequest.getImagePrompt(),
                buildManualCharacterImagePrompt(displayName, relation, suspiciousPoint)
        )));
        suspect.setRelationToVictim(relation);
        suspect.setSuspiciousPoint(suspiciousPoint);
        suspect.setAlibiSummary(alibiSummary);
        suspect.setUnlockedByDefault(safeRequest.getUnlockedByDefault() != null && safeRequest.getUnlockedByDefault());
        suspect.setDisplayOrder(safeRequest.getDisplayOrder() == null ? nextOrder : safeRequest.getDisplayOrder());

        adminEpisodeRepository.insertSuspect(suspect);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse deleteSuspect(Long episodeId, Long suspectId) {
        requireEditableEpisode(episodeId);

        boolean exists = adminEpisodeRepository.findSuspects(episodeId).stream()
                .anyMatch(suspect -> suspectId.equals(suspect.getId()));

        if (!exists) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SUSPECT_NOT_FOUND",
                    "삭제하려는 관계자 카드를 찾을 수 없습니다."
            );
        }

        adminEpisodeRepository.detachEvidencesBySuspectId(suspectId);
        adminEpisodeRepository.deleteSuspect(suspectId);

        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updateEvidence(Long episodeId, Long evidenceId, AdminEvidenceUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminEvidenceUpdateRequest safeRequest = request == null ? new AdminEvidenceUpdateRequest() : request;

        CaseEvidence evidence = adminEpisodeRepository.findEvidences(episodeId).stream()
                .filter(item -> evidenceId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EVIDENCE_NOT_FOUND",
                        "해금 자료 카드를 찾을 수 없습니다."
                ));

        evidence.setTitle(text(safeRequest.getTitle(), evidence.getTitle()));

        evidence.setType(validateValue(
                text(safeRequest.getType(), evidence.getType()),
                EVIDENCE_TYPES,
                "INVALID_EVIDENCE_TYPE",
                "지원하지 않는 evidence type입니다."
        ));

        evidence.setImageUrl(text(safeRequest.getImageUrl(), evidence.getImageUrl()));
        evidence.setImagePrompt(ensureKoreanEvidencePrompt(text(safeRequest.getImagePrompt(), evidence.getImagePrompt())));
        evidence.setTextSummary(text(safeRequest.getTextSummary(), evidence.getTextSummary()));
        evidence.setSourceSpotId(validateOptionalSpot(episodeId, safeRequest.getSourceSpotId(), evidence.getSourceSpotId()));
        evidence.setRelatedSuspectId(validateOptionalSuspect(episodeId, safeRequest.getRelatedSuspectId(), evidence.getRelatedSuspectId()));
        evidence.setRelatedClueType(text(safeRequest.getRelatedClueType(), evidence.getRelatedClueType()));
        evidence.setUnlockedByDefault(safeRequest.getUnlockedByDefault() == null ? evidence.getUnlockedByDefault() : safeRequest.getUnlockedByDefault());
        evidence.setDisplayOrder(safeRequest.getDisplayOrder() == null ? evidence.getDisplayOrder() : safeRequest.getDisplayOrder());

        adminEpisodeRepository.updateEvidence(evidence);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse createEvidence(Long episodeId, AdminEvidenceUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminEvidenceUpdateRequest safeRequest = request == null ? new AdminEvidenceUpdateRequest() : request;

        int nextOrder = adminEpisodeRepository.countEvidences(episodeId) + 1;

        String type = validateValue(
                text(safeRequest.getType(), "NOTE"),
                EVIDENCE_TYPES,
                "INVALID_EVIDENCE_TYPE",
                "지원하지 않는 evidence type입니다."
        );

        String title = blank(safeRequest.getTitle(), "추가 해금 자료 " + nextOrder);
        String summary = blank(
                safeRequest.getTextSummary(),
                "관리자가 수동으로 추가한 해금 자료입니다. 공개 전 현장 근거, 연결 미션, 정답 노출 여부를 검수하세요."
        );

        CaseEvidence evidence = new CaseEvidence();
        evidence.setEpisodeId(episodeId);
        evidence.setTitle(title);
        evidence.setType(type);
        evidence.setImageUrl(text(safeRequest.getImageUrl(), null));
        evidence.setImagePrompt(ensureKoreanEvidencePrompt(blank(
                safeRequest.getImagePrompt(),
                buildManualEvidenceImagePrompt(title, summary)
        )));
        evidence.setTextSummary(summary);
        evidence.setSourceSpotId(validateOptionalSpot(episodeId, safeRequest.getSourceSpotId(), null));
        evidence.setRelatedSuspectId(validateOptionalSuspect(episodeId, safeRequest.getRelatedSuspectId(), null));
        evidence.setRelatedClueType(text(safeRequest.getRelatedClueType(), type));
        evidence.setUnlockedByDefault(safeRequest.getUnlockedByDefault() != null && safeRequest.getUnlockedByDefault());
        evidence.setDisplayOrder(safeRequest.getDisplayOrder() == null ? nextOrder : safeRequest.getDisplayOrder());

        adminEpisodeRepository.insertEvidence(evidence);
        return getEpisode(episodeId);
    }

    private String relationLabel(int order) {
        int index = Math.max(0, order - 1);
        char label = (char) ('A' + Math.floorMod(index, 26));

        if (index < 26) {
            return String.valueOf(label);
        }

        return label + String.valueOf((index / 26) + 1);
    }

    private String buildManualCharacterImagePrompt(String displayName, String relation, String note) {
        return "Create a high-quality character archive card illustration for a Korean outdoor story mission. "
                + "Subject: " + blank(displayName, "추가 확인이 필요한 관계자") + ". "
                + "Role in story: " + blank(relation, "미션 파일 관계자") + ". "
                + "Character note: " + blank(note, "동선과 기록 흐름에서 추가 확인이 필요한 지점이 있습니다.") + ". "
                + "Casting is mandatory: depict a fictional Korean person from Seoul, South Korea. "
                + "The subject must look unmistakably Korean; preserve the story's specified age, gender, occupation, and historical era. "
                + "Do not cast a Western or European-looking model, and do not change the character's Korean identity. "
                + "Composition: bust portrait or half-body portrait, 3/4 view, natural Korean styling and grooming appropriate to the story era, calm restrained expression, clean silhouette. "
                + missionArchiveIllustrationStylePrompt()
                + missionArchiveNegativeImagePrompt()
                + " No celebrity likeness.";
    }

    private String buildManualEvidenceImagePrompt(String title, String summary) {
        return "Create a high-quality mission archive card illustration for a Korean outdoor story mission. "
                + "Subject: " + blank(title, "추가 해금 자료") + ". "
                + "Story detail: " + blank(summary, "현장 근거와 미션 파일을 연결해 다음 판단을 돕는 자료입니다.") + ". "
                + missionArchiveIllustrationStylePrompt()
                + "If any person, hand, portrait, reflection, or human silhouette appears, it must belong to a fictional Korean person in Seoul and match the story era. "
                + missionArchiveNegativeImagePrompt();
    }

    public AdminEpisodeDetailResponse deleteEvidence(Long episodeId, Long evidenceId) {
        requireEditableEpisode(episodeId);

        boolean exists = adminEpisodeRepository.findEvidences(episodeId).stream()
                .anyMatch(evidence -> evidenceId.equals(evidence.getId()));

        if (!exists) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVIDENCE_NOT_FOUND",
                    "삭제하려는 해금 자료 카드를 찾을 수 없습니다."
            );
        }

        adminEpisodeRepository.deleteEvidence(evidenceId);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updatePartnerReward(Long episodeId, Long rewardId, AdminPartnerRewardUpdateRequest request) {
        requireEditableEpisode(episodeId);

        AdminPartnerRewardUpdateRequest safeRequest = request == null
                ? new AdminPartnerRewardUpdateRequest()
                : request;

        EpisodePartnerReward reward = adminEpisodeRepository.findPartnerRewards(episodeId).stream()
                .filter(item -> rewardId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PARTNER_REWARD_NOT_FOUND",
                        "제휴 리워드를 찾을 수 없습니다."
                ));

        reward.setTitle(text(safeRequest.getTitle(), reward.getTitle()));
        reward.setDescription(text(safeRequest.getDescription(), reward.getDescription()));

        reward.setRewardType(validateValue(
                text(safeRequest.getRewardType(), reward.getRewardType()),
                PARTNER_REWARD_TYPES,
                "INVALID_REWARD_TYPE",
                "지원하지 않는 제휴 리워드 유형입니다."
        ));

        reward.setPartnerName(text(safeRequest.getPartnerName(), reward.getPartnerName()));
        reward.setLocationName(text(safeRequest.getLocationName(), reward.getLocationName()));
        reward.setLatitude(safeRequest.getLatitude() == null ? reward.getLatitude() : safeRequest.getLatitude());
        reward.setLongitude(safeRequest.getLongitude() == null ? reward.getLongitude() : safeRequest.getLongitude());

        reward.setStatus(validateValue(
                text(safeRequest.getStatus(), reward.getStatus()),
                PARTNER_REWARD_STATUSES,
                "INVALID_REWARD_STATUS",
                "제휴 리워드 status는 DISABLED, PLANNED, ACTIVE, ENDED 중 하나여야 합니다."
        ));

        adminEpisodeRepository.updatePartnerReward(reward);
        return getEpisode(episodeId);
    }

    private AdminRewardPayloadValidationResponse validateRewardPayload(Long episodeId, AdminRewardPayloadValidationRequestWrapper request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<AdminRewardPayloadValidationResponse.RewardItem> rewardItems = new ArrayList<>();

        String payload = request == null ? null : request.rewardPayload();

        if (payload == null || payload.isBlank()) {
            warnings.add("reward_payload가 비어 있습니다. 이 경우 reward_clue만 사용됩니다.");
            return AdminRewardPayloadValidationResponse.builder()
                    .valid(true)
                    .errors(errors)
                    .warnings(warnings)
                    .rewards(rewardItems)
                    .build();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode rewards = root.path("rewards");

            if (!rewards.isArray()) {
                errors.add("reward_payload.rewards는 배열이어야 합니다.");
            } else if (rewards.isEmpty()) {
                warnings.add("reward_payload.rewards 배열이 비어 있습니다.");
            } else {
                for (int i = 0; i < rewards.size(); i++) {
                    JsonNode reward = rewards.get(i);

                    String type = reward.path("type").asText("");
                    String value = reward.path("value").asText("");
                    Long targetId = reward.hasNonNull("targetId")
                            ? reward.path("targetId").asLong()
                            : null;

                    if (!REWARD_TYPES.contains(type)) {
                        errors.add("rewards[" + i + "].type이 지원되지 않는 보상 유형입니다: " + type);
                    }

                    String targetLabel = null;

                    if (Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE").contains(type)
                            && value.isBlank()) {
                        errors.add("rewards[" + i + "] " + type + " 유형은 value가 필요합니다.");
                    }

                    if ("MEMO_UNLOCK".equals(type) && targetId == null && value.isBlank()) {
                        errors.add("rewards[" + i + "] MEMO_UNLOCK 유형은 targetId 또는 value가 필요합니다.");
                    }

                    if (Set.of("EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "MEMO_UNLOCK").contains(type)
                            && targetId != null) {
                        targetLabel = validateEvidenceTarget(episodeId, targetId, i, errors);
                    }

                    if (Set.of("SUSPECT_UNLOCK", "SUSPECT_UPDATE").contains(type)) {
                        targetLabel = validateSuspectTarget(episodeId, targetId, i, errors);
                    }

                    rewardItems.add(AdminRewardPayloadValidationResponse.RewardItem.builder()
                            .type(type)
                            .value(value)
                            .targetId(targetId)
                            .targetLabel(targetLabel)
                            .build());
                }
            }
        } catch (Exception e) {
            errors.add("reward_payload는 올바른 JSON 형식이어야 합니다.");
        }

        return AdminRewardPayloadValidationResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .rewards(rewardItems)
                .build();
    }


    public AiEpisodeDraftResponse createAiDraft(AiEpisodeDraftRequest request) {
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AI_DRAFT_REQUEST",
                    "AI 초안 생성을 위해 요청 본문이 필요합니다."
            );
        }

        List<AiEpisodeDraftRequest.PlaceInput> places = request.getPlaces() == null
                ? List.of()
                : request.getPlaces();

        int minMissionCount = request.getMissionPolicy() != null && request.getMissionPolicy().getMinMissionCount() != null
                ? request.getMissionPolicy().getMinMissionCount()
                : MIN_EPISODE_SPOTS;

        int maxMissionCount = request.getMissionPolicy() != null && request.getMissionPolicy().getMaxMissionCount() != null
                ? request.getMissionPolicy().getMaxMissionCount()
                : MAX_EPISODE_SPOTS;

        if (minMissionCount < 1 || maxMissionCount < minMissionCount) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MISSION_POLICY",
                    "미션 개수 정책이 올바르지 않습니다. 최소 개수는 1 이상이고, 최대 개수는 최소 개수보다 크거나 같아야 합니다."
            );
        }

        if (places.size() < minMissionCount) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NOT_ENOUGH_PLACES",
                    "현재 정책에서는 최소 " + minMissionCount + "개 이상의 장소가 필요합니다."
            );
        }

        if (places.size() > maxMissionCount) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_PLACES",
                    "현재 정책에서는 최대 " + maxMissionCount + "개 장소까지만 사용할 수 있습니다."
            );
        }
        List<String> warnings = new ArrayList<>();
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = places.get(i);
            String role = normalizeRole(place.getRole(), i, places.size());
            if (place.getVisibleElements() == null || place.getVisibleElements().isEmpty()) {
                warnings.add(blank(place.getName(), "조사 지점 " + (i + 1))
                        + ": 현장 관찰 요소(visibleElements)가 비어 있습니다. 관찰형 퍼즐은 공개 전 현장 확인 후 보강하세요.");
            }
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(blank(place.getName(), "조사 지점 " + (i + 1)))
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription(), i == 0
                            ? "미션 파일을 열고 단서 분류를 확인하세요."
                            : "현장 자료와 미션 메모를 비교하세요."))
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendPuzzleType(place))
                    .questionText(buildQuestion(place))
                    .answer(buildAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(buildRewardClue(role, i))
                    .hints(List.of(
                            "관리자가 제공한 현장 데이터만 기준으로 보세요.",
                            "이 단서가 정답 힌트인지 목적지 힌트인지 분류하세요.",
                            "공개 전 현장에서 단서 근거를 확인하세요."
                    ))
                    .groundRule("규칙 기반 관리자 초안입니다. 공개 전 현장 주장을 확인하세요.")
                    .build());
        }
        DraftObjective objective = draftObjective(request, places);
        String genreName = selectedGenreName(request);

        AiEpisodeDraftResponse.EpisodeDraft draft = AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("EP.NEW " + blank(request.getTheme(), "숨겨진 기록 미션"))
                .subtitle(draftSubtitle(request, places))
                .genre(genreName)
                .era(draftEra(request, places))
                .fictionSynopsis(objective.synopsis())
                .selectedGenre(objective.genre())
                .finalAnswerKeywords(objective.keywords())
                .finalAnswerType(objective.answerType())
                .finalAnswer(objective.finalAnswer())
                .finalAnswerAliases(withKeywordContract(objective.aliases(), objective.keywords()))
                .finalQuestion(objective.finalQuestion())
                .finalTruthSummary(String.join("\n",
                        "3. 픽션과 실제 배경의 연결 (디브리핑)",
                        "스토리 속 [" + objective.finalAnswer() + "] -> 실제 배경 속 [관리자 검수 필요 자료]: 선택한 장르의 해결 조건을 하나의 최종 결론으로 묶은 장치입니다.",
                        "스토리 속 [현장 지령] -> 실제 배경 속 [최종 목적지의 장소 맥락]: 장소에 남은 정보와 이동 흐름을 미션 구조로 바꾼 장치입니다.",
                        "스토리 속 [암호 카드] -> 실제 배경 속 [기록, 표지, 안내, 증언 자료]: 플레이어가 현장 정보를 대조하도록 만든 장치입니다.",
                        "스토리 속 [관계자 진술] -> 실제 배경 속 [관련 인물, 장소, 이해관계]: 실존 인물을 실제 책임자로 만들지 않고 역할과 갈등 구조만 차용했습니다."
                ))
                .actualHistorySummary("""
                        1. 모티브 공개
                        이 임무는 실제 [관리자 검수 필요 최종 목적지]의 역사·문화·장소적 배경을 모티브로 제작되었습니다.

                        2. 실제 배경 해설
                        이 초안은 규칙 기반 안전 fallback입니다. 공개 전 관리자는 TourAPI 설명, 현장 표지, 공식 해설 자료를 확인해 최종 목적지의 실제 배경, 장소의 의미, 픽션으로 재구성한 부분을 상세히 보강해야 합니다.
                        """.trim())
                .deductionSecretFacts(List.of(
                        "최종 정답은 시놉시스가 요구한 해결 조건을 모두 포함해야 한다.",
                        "일부 단서 물건이나 문서 위치만 맞히는 답은 최종 정답이 아니다.",
                        "정답은 실제 장소명이나 실존 인물명이 아니라 픽션 미션 안의 완결된 결론이다."))
                .deductionForbiddenReveals(List.of(objective.finalAnswer(), "actualFinalPlace", "realPersonAsFinalAnswer"))
                .maxDeductionQuestions(20)
                .missions(missions)
                .suspects(defaultDraftSuspects())
                .evidences(defaultDraftEvidences(missions))
                .build();
        warnings.add("규칙 기반 초안을 생성했습니다. 저장 전 퍼즐 정답, reward_payload, finalPlace를 확인하세요.");
        return AiEpisodeDraftResponse.builder()
                .generatorType("MVP_RULE_BASED_DRAFT")
                .message("규칙 기반 스토리 미션 초안을 생성했습니다. 아직 DB에 저장되지 않았습니다.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of(
                        "현장 관찰 근거 확인",
                        "최종 정답이 장소명이나 실존 인물명이 아닌지 확인",
                        "보상을 관계자 카드와 해금 자료 카드에 연결",
                        "검증 후 DRAFT로 저장"
                ))
                .build();
    }




    private String draftSubtitle(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String area = blank(request.getArea(), "선택 지역");

        String anchor = places.stream()
                .filter(place -> "FINAL".equals(normalizeRole(place.getRole(), places.indexOf(place), places.size())))
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> places.isEmpty()
                        ? "마지막 조사 지점"
                        : blank(places.get(places.size() - 1).getName(), "마지막 조사 지점"));

        return area + "의 단서가 " + anchor + "로 수렴됩니다";
    }

    private String draftEra(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (!missing(request.getEra())
                && !containsCompact(request.getEra(), "review")
                && !containsCompact(request.getEra(), "unknown")) {
            return request.getEra().trim();
        }

        String joined = routeText(request, places);

        if (containsCompact(joined, "1905")
                || containsCompact(joined, "1897")
                || containsCompact(joined, "empire")
                || containsCompact(joined, "대한제국")) {
            return "대한제국 후기";
        }

        if (containsCompact(joined, "palace")
                || containsCompact(joined, "royal")
                || containsCompact(joined, "궁")
                || containsCompact(joined, "왕실")) {
            return "왕실 기록의 시대";
        }

        if (containsCompact(joined, "independence")
                || containsCompact(joined, "colonial")
                || containsCompact(joined, "독립")
                || containsCompact(joined, "일제")) {
            return "독립운동기";
        }

        return "과거와 현재가 겹치는 시대";
    }




    private DraftObjective draftObjective(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        List<String> approvedKeywords = approvedFinalKeywords(request);
        String genre = selectedGenreName(request);

        if (!approvedKeywords.isEmpty()) {
            String finalAnswer = genre + "의 최종 결론은 " + String.join(", ", approvedKeywords) + "입니다";

            return new DraftObjective(
                    genre,
                    approvedKeywords.size() > 1 ? "HIDDEN_TRUTH" : "EVIDENCE",
                    finalAnswer,
                    approvedKeywords,
                    List.of(finalAnswer.replace(" ", "")),
                    genre + "의 최종 결론을 이루는 핵심 요소들을 종합하면 무엇인가?",
                    "선택한 장소의 역사·문화 단서는 " + genre + " 구조로 재배열됩니다. 플레이어는 미션 파일과 현장 단서를 대조해 가려진 핵심 요소들을 모두 추론해야 합니다."
            );
        }

        String joined = routeText(request, places);
        String area = blank(request.getArea(), "선택 지역");
        String first = places.isEmpty() ? "첫 조사 지점" : blank(places.get(0).getName(), "첫 조사 지점");
        String anchor = places.isEmpty() ? "마지막 조사 지점" : blank(places.get(places.size() - 1).getName(), "마지막 조사 지점");
        String routeSignal = routeSignal(places);

        if (requiresIdentityAndHideout(joined)) {
            String identity = containsCompact(joined, "royal")
                    || containsCompact(joined, "황실")
                    || containsCompact(joined, "대한제국")
                    ? "기록을 숨긴 연락책"
                    : "정체가 감춰진 내부 전달자";

            String hideout = containsCompact(joined, "archive")
                    || containsCompact(joined, "기록")
                    || containsCompact(joined, "문서")
                    ? "봉인된 기록고"
                    : "닫힌 골목 거점";

            String finalAnswer = "숨은 전달자는 " + identity + "이며 마지막 거점은 " + hideout + "이다";

            return new DraftObjective(
                    genre,
                    "HIDDEN_TRUTH",
                    finalAnswer,
                    List.of(identity, hideout),
                    List.of(finalAnswer.replace(" ", ""), identity + "와 " + hideout),
                    "숨은 전달자의 정체와 마지막 거점은 무엇인가?",
                    area + "의 " + first + "에서 미완성 기록이 발견됩니다. 설계도와 장부는 단서일 뿐이며, " + routeSignal + " 표식은 " + anchor + "로 이어집니다. 플레이어는 미션 자료를 대조해 숨은 전달자의 정체와 마지막 거점을 찾아야 합니다."
            );
        }

        String object = draftFinalObject(request, places);

        return new DraftObjective(
                genre,
                "EVIDENCE",
                object,
                List.of(object),
                List.of(object.replace(" ", ""), draftFinalAlias(request, places)),
                draftFinalQuestion(request, places),
                draftFictionSynopsis(request, places)
        );
    }

    private boolean requiresIdentityAndHideout(String text) {
        boolean identity = containsCompact(text, "정체")
                || containsCompact(text, "숨겨진역할")
                || containsCompact(text, "blackshadow")
                || containsCompact(text, "비밀조직")
                || containsCompact(text, "조직");
        boolean hideout = containsCompact(text, "은신처")
                || containsCompact(text, "숨어든")
                || containsCompact(text, "hideout")
                || containsCompact(text, "거점")
                || containsCompact(text, "아지트");
        boolean royalMacGuffin = containsCompact(text, "황실비밀자금")
                || containsCompact(text, "비밀자금")
                || containsCompact(text, "설계도");
        return (identity && hideout) || (royalMacGuffin && (identity || hideout));
    }

    private String draftFictionSynopsis(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String area = blank(request.getArea(), "선택 지역");
        String first = places.isEmpty() ? "첫 조사 지점" : blank(places.get(0).getName(), "첫 조사 지점");
        String anchor = places.isEmpty() ? "마지막 조사 지점" : blank(places.get(places.size() - 1).getName(), "마지막 조사 지점");
        String object = draftFinalObject(request, places);
        String routeSignal = routeSignal(places);

        return area + "의 " + first + "에서 조사가 시작됩니다. "
                + "단서는 " + anchor + "를 향하지만, " + routeSignal + " 기록이 서로 엇갈립니다. "
                + "플레이어는 현장 자료를 비교해 [" + object + "]의 의미를 밝혀야 합니다.";
    }




    private String draftFinalQuestion(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        return "모은 단서가 가리키는 [" + draftFinalObject(request, places) + "]의 의미는 무엇입니까?";
    }




    private String draftFinalObject(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String joined = routeText(request, places);

        if (containsCompact(joined, "coffee") || containsCompact(joined, "cafe") || containsCompact(joined, "tea")) {
            return "차가운 차 기록";
        }

        if (containsCompact(joined, "document") || containsCompact(joined, "seal") || containsCompact(joined, "signature")) {
            return "붉은 봉인 문서";
        }

        if (containsCompact(joined, "photo") || containsCompact(joined, "film") || containsCompact(joined, "lens")) {
            return "봉인된 필름 봉투";
        }

        if (containsCompact(joined, "market") || containsCompact(joined, "restaurant") || containsCompact(joined, "receipt")) {
            return "찢긴 영수증 조각";
        }

        if (containsCompact(joined, "palace") || containsCompact(joined, "archive")) {
            return "접힌 기록고 사본";
        }

        return "봉인된 기록 조각";
    }




    private String draftFinalAlias(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String object = draftFinalObject(request, places);
        String[] tokens = object.split("\\s+");
        return tokens.length == 0 ? object : tokens[tokens.length - 1];
    }

    private record DraftObjective(
            String genre,
            String answerType,
            String finalAnswer,
            List<String> keywords,
            List<String> aliases,
            String finalQuestion,
            String synopsis) {
    }

    private String routeSignal(List<AiEpisodeDraftRequest.PlaceInput> places) {
        return places.stream().flatMap(place -> place.getKeywords() == null ? java.util.stream.Stream.empty() : place.getKeywords().stream()).filter(value -> value != null && !value.isBlank()).findFirst().orElse("동선");
    }





    private List<AiEpisodeDraftResponse.SuspectDraft> defaultDraftSuspects() {
        return List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("관계자 A")
                        .displayName("첫 기록을 확인한 인물")
                        .shortDescription("첫 조사 지점의 기록과 현장 단서를 확인한 인물입니다.")
                        .relationToVictim("초기 단서 확인자")
                        .suspiciousPoint("첫 기록과 이후 동선 사이에 설명이 필요한 빈칸이 있습니다.")
                        .alibiSummary("초기 지점에 머물렀다고 주장하지만, 이후 단서 흐름과 대조가 필요합니다.")
                        .build(),

                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("관계자 B")
                        .displayName("동선 기록을 보관한 인물")
                        .shortDescription("중간 조사 지점의 동선 자료와 미션 파일을 관리한 인물입니다.")
                        .relationToVictim("동선 자료 보관자")
                        .suspiciousPoint("일부 동선 기록이 누락되었거나 순서가 맞지 않습니다.")
                        .alibiSummary("기록 보관 위치에 있었다고 말하지만, 해금된 자료와 비교해야 합니다.")
                        .build(),

                AiEpisodeDraftResponse.SuspectDraft.builder()
                        .alias("관계자 C")
                        .displayName("마지막 단서를 전달한 인물")
                        .shortDescription("최종 지점으로 이어지는 단서 흐름을 알고 있는 인물입니다.")
                        .relationToVictim("최종 단서 전달자")
                        .suspiciousPoint("최종 단서의 의미를 알고도 일부 정보를 늦게 공개했습니다.")
                        .alibiSummary("마지막 동선과 자신의 역할을 설명했지만, 미션 파일과 대조가 필요합니다.")
                        .build()
        );
    }


    private List<AiEpisodeDraftResponse.EvidenceDraft> defaultDraftEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (missions == null || missions.isEmpty()) {
            return List.of();
        }

        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = new ArrayList<>();
        int limit = Math.min(8, missions.size());

        for (int i = 0; i < limit; i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            String type = defaultEvidenceType(mission);
            String title = defaultEvidenceTitle(mission, order);
            String summary = defaultEvidenceSummary(mission, order);

            evidences.add(AiEpisodeDraftResponse.EvidenceDraft.builder()
                    .title(title)
                    .type(type)
                    .imageUrl("")
                    .imagePrompt(buildGenericEvidenceImagePrompt(title, summary))
                    .textSummary(summary)
                    .sourceMissionOrder(order)
                    .build());
        }

        return evidences;
    }

    private String defaultEvidenceType(AiEpisodeDraftResponse.MissionDraft mission) {
        String role = normalizeType(mission == null ? null : mission.getClueRole());

        if ("ANSWER_HINT".equals(role)) {
            return "ANSWER_CLUE";
        }

        if ("DESTINATION_HINT".equals(role) || "FINAL_PLACE".equals(role)) {
            return "DESTINATION_CLUE";
        }

        return "STORY_CLUE";
    }

    private String defaultEvidenceTitle(AiEpisodeDraftResponse.MissionDraft mission, int order) {
        String role = normalizeType(mission == null ? null : mission.getClueRole());

        if ("START".equals(role)) {
            return "미션 " + order + " 시작 기록";
        }

        if ("ANSWER_HINT".equals(role)) {
            return "미션 " + order + " 정답 후보 단서";
        }

        if ("DESTINATION_HINT".equals(role)) {
            return "미션 " + order + " 동선 단서";
        }

        if ("FINAL_PLACE".equals(role)) {
            return "미션 " + order + " 최종 대조 자료";
        }

        return "미션 " + order + " 해금 자료";
    }

    private String defaultEvidenceSummary(AiEpisodeDraftResponse.MissionDraft mission, int order) {
        String role = normalizeType(mission == null ? null : mission.getClueRole());

        if ("START".equals(role)) {
            return "작전 시작 시점에 해금되는 기본 미션 파일입니다. 이후 미션의 단서 흐름을 이해하는 데 사용됩니다.";
        }

        if ("ANSWER_HINT".equals(role)) {
            return "최종 정답의 일부 역할을 좁히는 보조 단서입니다. 정답 자체가 아니라 추론 근거로 사용됩니다.";
        }

        if ("DESTINATION_HINT".equals(role)) {
            return "다음 동선이나 위치 조건을 좁히는 단서입니다. 장소명을 직접 노출하지 않고 이동 판단에 사용됩니다.";
        }

        if ("FINAL_PLACE".equals(role)) {
            return "지금까지 해금한 단서들을 최종적으로 대조하기 위한 자료입니다. 최종 정답을 직접 노출하지 않습니다.";
        }

        return "미션 " + order + " 완료 후 해금되는 미션 파일 자료입니다.";
    }

    private String buildGenericEvidenceImagePrompt(String title, String summary) {
        return "Create a high-quality mission archive card illustration for a Korean outdoor story mission. "
                + "Subject: " + title + ". "
                + "Story detail: " + summary + ". "
                + missionArchiveIllustrationStylePrompt()
                + "If any person, hand, portrait, reflection, or human silhouette appears, it must belong to a fictional Korean person in Seoul and match the story era. "
                + missionArchiveNegativeImagePrompt();
    }


    private String routeText(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        return String.join(" ",
                blank(request.getArea(), ""),
                blank(request.getTheme(), ""),
                places.stream()
                        .map(place -> String.join(" ",
                                blank(place.getName(), ""),
                                blank(place.getDescription(), ""),
                                blank(place.getAdminMemo(), ""),
                                place.getKeywords() == null ? "" : String.join(" ", place.getKeywords())))
                        .collect(Collectors.joining(" ")));
    }

    public AdminEpisodeDetailResponse saveAiDraft(AiEpisodeDraftSaveRequest request) {
        AiEpisodeDraftResponse.EpisodeDraft draft = request == null ? null : request.getDraft();

        if (draft == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DRAFT",
                    "저장할 AI 초안 데이터가 없습니다."
            );
        }

        normalizeDraftBeforeSave(draft, request == null ? null : request.getSourceInput());

        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null
                ? List.of()
                : draft.getMissions();

        if (missions.size() < MIN_EPISODE_SPOTS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NOT_ENOUGH_MISSIONS",
                    "AI 초안 저장에는 최소 " + MIN_EPISODE_SPOTS + "개 이상의 미션이 필요합니다."
            );
        }

        if (missions.size() > MAX_EPISODE_SPOTS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_MISSIONS",
                    "AI 초안은 최대 " + MAX_EPISODE_SPOTS + "개 미션까지만 저장할 수 있습니다."
            );
        }

        long finalCount = missions.stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType()))
                .count();

        if (finalCount < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FINAL_PLACE_REQUIRED",
                    "AI 초안에는 내부 최종 장소가 최소 1개 필요합니다."
            );
        }

        if (finalCount > 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_FINAL_PLACES",
                    "AI 초안에는 내부 최종 장소가 정확히 1개만 있어야 합니다."
            );
        }
        String title = resolveDraftTitle(draft, missions);

        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(draft.getSubtitle(), "AI 생성 스토리 미션 초안"));
        episode.setEra(blank(draft.getEra(), "시대 검수 필요"));
        episode.setGenre(blank(draft.getGenre(), "야외 스토리 미션"));
        episode.setDifficulty("NORMAL");
        episode.setEstimatedTime("90~120분");
        episode.setEstimatedDistance(estimateDraftDistance(missions));

        episode.setFictionSynopsis(blank(
                draft.getFictionSynopsis(),
                "AI 초안 저장 시 시놉시스가 누락되었습니다. 공개 전 스토리 흐름, 장소 동선, 최종 목표를 보강하세요."
        ));

        episode.setFinalAnswerType(blank(draft.getFinalAnswerType(), "HIDDEN_TRUTH"));
        episode.setFinalAnswer(blank(draft.getFinalAnswer(), "검수필요"));
        episode.setFinalAnswerAliases(join(withKeywordContract(draft.getFinalAnswerAliases(), draft.getFinalAnswerKeywords())));

        episode.setFinalQuestion(blank(
                draft.getFinalQuestion(),
                "모든 단서를 종합했을 때 이 미션의 최종 결론은 무엇인가?"
        ));

        episode.setFinalTruthSummary(blank(
                draft.getFinalTruthSummary(),
                "관리자만 확인하는 최종 진실 요약입니다. 공개 전 정답 키워드, 단서 연결 방식, 최종 장소의 역할을 구체적으로 작성하세요."
        ));

        episode.setActualHistorySummary(blank(
                draft.getActualHistorySummary(),
                """
                1. 모티브 공개
                이 임무는 실제 장소의 역사·문화적 배경을 모티브로 제작된 스토리 미션입니다.
        
                2. 실제 배경 해설
                공개 전 관리자는 공식 해설 자료, 현장 표지, TourAPI 설명을 확인해 실제 배경과 픽션 요소의 차이를 보강해야 합니다.
                """.trim()
        ));

        episode.setDeductionSecretFacts(blank(
                joinLines(draft.getDeductionSecretFacts()),
                """
                최종 정답은 관리자가 확정한 핵심 키워드를 모두 포함해야 한다.
                일부 장소명이나 단서명만 맞히는 답은 최종 정답이 아니다.
                정답은 실제 장소명이나 실존 인물명이 아니라 픽션 미션 안의 결론이어야 한다.
                """.trim()
        ));

        episode.setDeductionForbiddenReveals(blank(
                joinLines(draft.getDeductionForbiddenReveals()),
                """
                검수필요
                실제 최종 장소명
                실존 인물 정답화
                """.trim()
        ));

        episode.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : Math.max(1, draft.getMaxDeductionQuestions()));
        episode.setRecommendedPlayers("2~4명");
        episode.setTeamRoleGuide("지도 담당, 미션 파일 담당, 퍼즐 담당, 기록 담당으로 역할을 나누어 진행하세요.");
        episode.setNoticeText("공개 전 현장 좌표, 도착 반경, 퍼즐 정답, 힌트, reward_payload를 반드시 검수하세요.");

        episode.setStatus(validateValue(
                text(request.getStatus(), "DRAFT"),
                EPISODE_STATUSES,
                "INVALID_EPISODE_STATUS",
                "status는 DRAFT, PUBLISHED, ARCHIVED 중 하나여야 합니다."
        ));

        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DRAFT_REVIEW_REQUIRED",
                    "AI 초안은 먼저 DRAFT로 저장한 뒤 관리자 검수 후 공개하세요."
            );
        }

        adminEpisodeRepository.insertEpisode(episode);

        Map<Integer, MissionSpot> spotByOrder = new HashMap<>();
        Map<Integer, Puzzle> puzzleByOrder = new HashMap<>();
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            MissionSpot spot = new MissionSpot();
            spot.setEpisodeId(episode.getId());
            spot.setPlaceName(blank(mission.getPlaceName(), "조사 지점 " + (i + 1)));
            spot.setAddress(mission.getAddress());
            spot.setLatitude(mission.getLatitude() == null ? 37.5665 + (i * 0.001) : mission.getLatitude());
            spot.setLongitude(mission.getLongitude() == null ? 126.9780 + (i * 0.001) : mission.getLongitude());
            boolean finalPlace = Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType());
            String markerType = finalPlace
                    ? "FINAL"
                    : validateValue(
                    blank(mission.getMarkerType(), normalizeRole(null, i, missions.size())),
                    MARKER_TYPES,
                    "INVALID_MARKER_TYPE",
                    "지원하지 않는 markerType입니다."
            );
            spot.setMarkerType(markerType);
            spot.setFinalPlace(finalPlace);
            spot.setClueRole(finalPlace
                    ? "FINAL_PLACE"
                    : validateValue(
                    blank(mission.getClueRole(), toClueRole(markerType)),
                    CLUE_ROLES,
                    "INVALID_CLUE_ROLE",
                    "지원하지 않는 clueRole입니다."
            ));
            spot.setPublicMarkerType(publicMarkerType(mission.getPublicMarkerType(), finalPlace, markerType));
            spot.setStoryText(blank(
                    sanitizeCategoryCodes(mission.getStoryText()),
                    "이 지점의 현장 근거와 미션 파일을 연결하는 스토리 문구를 공개 전 보강하세요."
            ));
            spot.setArrivalRadius(mission.getArrivalRadius() == null ? 50.0 : Math.max(10.0, mission.getArrivalRadius()));
            spot.setFieldVerified(true);
            spot.setFieldVerificationNote("AI/사이트 데이터 기반 검수 완료 초안입니다. 좌표, 도착 반경, 퍼즐 근거는 제공된 후보 데이터로 확인했으며 실제 GPS QA는 선택 사항입니다.");
            adminEpisodeRepository.insertSpot(spot);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            spotByOrder.put(order, spot);
            if (isMissionAnswerDisconnected(mission, draft, missions)) {
                normalizeMissionForReview(mission, draft, missions, i);
            }

            Puzzle puzzle = new Puzzle();
            puzzle.setMissionSpotId(spot.getId());
            puzzle.setPuzzleType(validateValue(
                    blank(mission.getPuzzleType(), "OBSERVATION"),
                    PUZZLE_TYPES,
                    "INVALID_PUZZLE_TYPE",
                    "지원하지 않는 puzzleType입니다."
            ));
            puzzle.setQuestionText(blank(
                    sanitizeCategoryCodes(mission.getQuestionText()),
                    "관리자 검수 후 이 지점의 실제 현장 근거를 기준으로 퍼즐 질문을 작성하세요."
            ));
            puzzle.setAnswer(blank(sanitizeCategoryCodes(mission.getAnswer()), "검수필요"));
            puzzle.setRewardClue(blank(sanitizeCategoryCodes(mission.getRewardClue()), saveSafeRewardClue(mission, i)));
            puzzle.setAnswerFormat(validateValue(
                    blank(mission.getAnswerFormat(), "TEXT"),
                    ANSWER_FORMATS,
                    "INVALID_ANSWER_FORMAT",
                    "지원하지 않는 answerFormat입니다."
            ));
            puzzle.setRewardPayload(null);
            puzzle.setDifficulty("NORMAL");
            adminEpisodeRepository.insertPuzzle(puzzle);
            puzzleByOrder.put(order, puzzle);
            List<String> hints = mission.getHints() == null ? List.of() : mission.getHints();
            for (int hintIndex = 0; hintIndex < Math.min(3, hints.size()); hintIndex++) {
                adminEpisodeRepository.insertHint(puzzle.getId(), hintIndex + 1, sanitizeHintText(sanitizeCategoryCodes(hints.get(hintIndex)), mission));
            }
        }

        List<CaseSuspect> suspects = saveDraftSuspects(episode.getId(), draft.getSuspects());
        Map<Integer, CaseEvidence> evidenceByMissionOrder = saveDraftEvidences(episode.getId(), draft.getEvidences(), spotByOrder, suspects);
        applyDraftRewardPayloads(episode.getId(), draft, missions, puzzleByOrder, evidenceByMissionOrder);
        saveDraftPartnerReward(episode.getId());
        return getEpisode(episode.getId());
    }

    private void normalizeDraftBeforeSave(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftRequest sourceInput
    ) {
        if (draft == null) {
            return;
        }

        List<String> keywords = approvedFinalKeywords(sourceInput);

        if (keywords.isEmpty() && draft.getFinalAnswerKeywords() != null) {
            keywords = draft.getFinalAnswerKeywords().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }

        if (!keywords.isEmpty()) {
            draft.setFinalAnswerKeywords(keywords);
            draft.setFinalAnswerAliases(withKeywordContract(draft.getFinalAnswerAliases(), keywords));

            String genre = missing(draft.getSelectedGenre())
                    ? selectedGenreName(sourceInput)
                    : draft.getSelectedGenre();

            boolean missingKeyword = keywords.stream()
                    .map(this::compact)
                    .anyMatch(keyword -> !compact(draft.getFinalAnswer()).contains(keyword));

            if (missingKeyword) {
                draft.setFinalAnswerType(keywords.size() > 1 ? "HIDDEN_TRUTH" : blank(draft.getFinalAnswerType(), "EVIDENCE"));
                draft.setFinalAnswer(naturalFinalAnswer(keywords));
            }

            if (missing(draft.getFinalQuestion()) || containsFinalKeywordText(draft.getFinalQuestion(), draft)) {
                draft.setFinalQuestion("흩어진 단서들이 가리키는 대상과 감춰진 행방을 밝혀내면, 이번 미션의 전말은 무엇인가?");
            }

            if (draft.getDeductionForbiddenReveals() == null) {
                draft.setDeductionForbiddenReveals(new ArrayList<>());
            }

            if (draft.getDeductionForbiddenReveals().stream().noneMatch(value -> same(value, draft.getFinalAnswer()))) {
                draft.getDeductionForbiddenReveals().add(draft.getFinalAnswer());
            }

            for (String keyword : keywords) {
                if (draft.getDeductionForbiddenReveals().stream().noneMatch(value -> same(value, keyword))) {
                    draft.getDeductionForbiddenReveals().add(keyword);
                }
            }
        }

        if (draft.getMissions() == null) {
            return;
        }

        for (int i = 0; i < draft.getMissions().size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(i);

            if (containsFinalKeywordText(mission.getQuestionText(), draft)) {
                mission.setQuestionText(saveSafeQuestionText(mission));
            }

            if (containsFinalKeywordText(mission.getAnswer(), draft)
                    || isGenericPuzzleAnswer(compact(mission.getAnswer()))
                    || isPlaceNameAnswer(compact(mission.getAnswer()), mission.getPlaceName())) {
                normalizeMissionForReview(mission, draft, draft.getMissions(), i);
            }

            if (containsFinalKeywordText(mission.getRewardClue(), draft)) {
                mission.setRewardClue(saveSafeRewardClue(mission, i));
            }

            List<String> sourceHints = mission.getHints() == null ? List.of() : mission.getHints();
            List<String> hints = new ArrayList<>();

            for (int hintIndex = 0; hintIndex < Math.min(3, sourceHints.size()); hintIndex++) {
                String hint = sourceHints.get(hintIndex);
                if (containsFinalKeywordText(hint, draft) || textContains(hint, mission.getAnswer())) {
                    hints.add(saveSafeHint(hintIndex, mission));
                } else {
                    hints.add(hint);
                }
            }

            while (hints.size() < 3) {
                hints.add(saveSafeHint(hints.size(), mission));
            }

            mission.setHints(hints);
        }
    }

    private AdminEpisodeDetailResponse.Spot toSpot(MissionSpot spot) {
        Puzzle puzzle = adminEpisodeRepository.findPuzzleBySpotId(spot.getId());
        return AdminEpisodeDetailResponse.Spot.builder()
                .spotId(spot.getId())
                .placeName(spot.getPlaceName())
                .address(spot.getAddress())
                .latitude(spot.getLatitude())
                .longitude(spot.getLongitude())
                .markerType(spot.getMarkerType())
                .publicMarkerType(spot.getPublicMarkerType())
                .clueRole(spot.getClueRole())
                .finalPlace(spot.getFinalPlace())
                .storyText(spot.getStoryText())
                .arrivalRadius(spot.getArrivalRadius())
                .fieldVerified(Boolean.TRUE.equals(spot.getFieldVerified()))
                .fieldVerificationNote(spot.getFieldVerificationNote())
                .puzzle(puzzle == null ? null : toPuzzle(puzzle))
                .build();
    }

    private AdminEpisodeDetailResponse.Puzzle toPuzzle(Puzzle puzzle) {
        return AdminEpisodeDetailResponse.Puzzle.builder()
                .puzzleId(puzzle.getId())
                .puzzleType(puzzle.getPuzzleType())
                .questionText(puzzle.getQuestionText())
                .answer(puzzle.getAnswer())
                .answerFormat(puzzle.getAnswerFormat())
                .rewardClue(puzzle.getRewardClue())
                .rewardPayload(puzzle.getRewardPayload())
                .difficulty(puzzle.getDifficulty())
                .hints(adminEpisodeRepository.findHints(puzzle.getId()).stream().map(this::toHint).toList())
                .build();
    }

    private AdminEpisodeDetailResponse.Hint toHint(PuzzleHint hint) {
        return AdminEpisodeDetailResponse.Hint.builder()
                .hintLevel(hint.getHintLevel())
                .hintText(hint.getHintText())
                .build();
    }

    private AdminEpisodeDetailResponse.Suspect toSuspect(CaseSuspect suspect) {
        return AdminEpisodeDetailResponse.Suspect.builder()
                .suspectId(suspect.getId())
                .displayName(suspect.getDisplayName())
                .alias(suspect.getAlias())
                .shortDescription(suspect.getShortDescription())
                .portraitImageUrl(suspect.getPortraitImageUrl())
                .imagePrompt(ensureKoreanPersonPrompt(suspect.getImagePrompt()))
                .relationToVictim(suspect.getRelationToVictim())
                .suspiciousPoint(suspect.getSuspiciousPoint())
                .alibiSummary(suspect.getAlibiSummary())
                .unlockedByDefault(suspect.getUnlockedByDefault())
                .displayOrder(suspect.getDisplayOrder())
                .build();
    }

    private AdminEpisodeDetailResponse.Evidence toEvidence(CaseEvidence evidence) {
        return AdminEpisodeDetailResponse.Evidence.builder()
                .evidenceId(evidence.getId())
                .title(evidence.getTitle())
                .type(evidence.getType())
                .imageUrl(evidence.getImageUrl())
                .imagePrompt(ensureKoreanEvidencePrompt(evidence.getImagePrompt()))
                .textSummary(evidence.getTextSummary())
                .sourceSpotId(evidence.getSourceSpotId())
                .relatedSuspectId(evidence.getRelatedSuspectId())
                .relatedClueType(evidence.getRelatedClueType())
                .unlockedByDefault(evidence.getUnlockedByDefault())
                .displayOrder(evidence.getDisplayOrder())
                .build();
    }

    private AdminEpisodeDetailResponse.PartnerReward toReward(EpisodePartnerReward reward) {
        return AdminEpisodeDetailResponse.PartnerReward.builder()
                .rewardId(reward.getId())
                .title(reward.getTitle())
                .description(reward.getDescription())
                .rewardType(reward.getRewardType())
                .partnerName(reward.getPartnerName())
                .locationName(reward.getLocationName())
                .status(reward.getStatus())
                .build();
    }

    private List<CaseSuspect> saveDraftSuspects(Long episodeId, List<AiEpisodeDraftResponse.SuspectDraft> drafts) {
        List<AiEpisodeDraftResponse.SuspectDraft> source = drafts == null || drafts.isEmpty() ? defaultDraftSuspects() : drafts;
        List<CaseSuspect> saved = new ArrayList<>();
        int index = 0;
        for (AiEpisodeDraftResponse.SuspectDraft draft : source) {
            CaseSuspect suspect = new CaseSuspect();
            suspect.setEpisodeId(episodeId);
            suspect.setAlias(blank(draft.getAlias(), "관계자 " + (char) ('A' + index)));
            suspect.setDisplayName(blank(draft.getDisplayName(), "이름 미정 관계자"));
            suspect.setShortDescription(blank(draft.getShortDescription(), "단서 흐름과 관련된 기본 관계자 카드입니다."));
            suspect.setRelationToVictim(blank(draft.getRelationToVictim(), "미션 파일 관계자"));
            suspect.setSuspiciousPoint(blank(draft.getSuspiciousPoint(), "동선 또는 기록 흐름에서 추가 확인이 필요한 지점이 있습니다."));
            suspect.setAlibiSummary(blank(draft.getAlibiSummary(), "해금된 미션 파일과 대조해 확인해야 합니다."));
            suspect.setPortraitImageUrl(draft.getPortraitImageUrl());
            suspect.setImagePrompt(ensureKoreanPersonPrompt(blank(draft.getImagePrompt(), buildSuspectImagePrompt(draft))));
            suspect.setUnlockedByDefault(index == 0);
            suspect.setDisplayOrder(index + 1);
            adminEpisodeRepository.insertSuspect(suspect);
            saved.add(suspect);
            index++;
        }
        return saved;
    }




    private Map<Integer, CaseEvidence> saveDraftEvidences(Long episodeId, List<AiEpisodeDraftResponse.EvidenceDraft> drafts, Map<Integer, MissionSpot> spotByOrder, List<CaseSuspect> suspects) {
        Map<Integer, CaseEvidence> evidenceByMissionOrder = new HashMap<>();
        List<AiEpisodeDraftResponse.EvidenceDraft> source = drafts == null ? List.of() : drafts;
        int index = 0;
        for (AiEpisodeDraftResponse.EvidenceDraft draft : source) {
            CaseEvidence evidence = new CaseEvidence();
            evidence.setEpisodeId(episodeId);
            evidence.setTitle(limitText(blank(draft.getTitle(), "미션 자료 " + (index + 1)), 255));
            evidence.setType(validateValue(
                    blank(draft.getType(), "NOTE"),
                    EVIDENCE_TYPES,
                    "INVALID_EVIDENCE_TYPE",
                    "지원하지 않는 evidence type입니다."
            ));
            evidence.setImageUrl(draft.getImageUrl());
            evidence.setImagePrompt(ensureKoreanEvidencePrompt(blank(draft.getImagePrompt(), buildEvidenceImagePrompt(draft))));
            evidence.setTextSummary(blank(
                    draft.getTextSummary(),
                    "현장 근거와 미션 파일을 연결해 다음 판단을 돕는 해금 자료입니다."
            ));
            evidence.setSourceSpotId(resolveSourceSpotId(draft, spotByOrder));
            evidence.setRelatedSuspectId(resolveLinkedSuspectId(draft, suspects, index));
            evidence.setRelatedClueType(blank(draft.getType(), "NOTE"));
            evidence.setUnlockedByDefault(index == 0);
            evidence.setDisplayOrder(index + 1);
            adminEpisodeRepository.insertEvidence(evidence);
            if (draft.getSourceMissionOrder() != null) evidenceByMissionOrder.put(draft.getSourceMissionOrder(), evidence);
            index++;
        }
        return evidenceByMissionOrder;
    }


    private Long resolveSourceSpotId(AiEpisodeDraftResponse.EvidenceDraft draft, Map<Integer, MissionSpot> spotByOrder) {
        if (draft == null || draft.getSourceMissionOrder() == null || spotByOrder == null) {
            return null;
        }
        MissionSpot spot = spotByOrder.get(draft.getSourceMissionOrder());
        return spot == null ? null : spot.getId();
    }

    private String buildSuspectImagePrompt(AiEpisodeDraftResponse.SuspectDraft draft) {
        String name = blank(draft.getDisplayName(), blank(draft.getAlias(), "관계자 카드"));
        String role = blank(draft.getRelationToVictim(), "단서 흐름과 관련된 인물");
        String note = blank(draft.getSuspiciousPoint(), "동선과 기록 흐름에서 추가 확인이 필요한 지점이 있습니다.");

        return "Create a high-quality character archive card illustration for a Korean outdoor story mission. "
                + "Subject: " + name + ". "
                + "Role in story: " + role + ". "
                + "Character note: " + note + ". "
                + "Casting is mandatory: depict a fictional Korean person from Seoul, South Korea. "
                + "The subject must look unmistakably Korean; preserve the story's specified age, gender, occupation, and historical era. "
                + "Do not cast a Western or European-looking model, and do not change the character's Korean identity. "
                + "Composition: bust portrait or half-body portrait, 3/4 view, natural Korean styling and grooming appropriate to the story era, calm restrained expression, clean silhouette. "
                + missionArchiveIllustrationStylePrompt()
                + missionArchiveNegativeImagePrompt()
                + " No celebrity likeness.";
    }

    private String ensureKoreanPersonPrompt(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }
        String styled = ensureCaseFileIllustrationStyle(prompt);
        String normalized = styled.toLowerCase(Locale.ROOT);
        if (normalized.contains("fictional korean person") || normalized.contains("korean identity")) {
            return styled;
        }
        return styled.trim()
                + " Casting is mandatory: every visible person must be a fictional Korean person from Seoul, South Korea. "
                + "Preserve the story's age, gender, occupation, and era. Do not cast a Western or European-looking model or change the character's Korean identity. "
                + "Keep the result consistent with a calm printed character archive card.";
    }

    private String ensureKoreanEvidencePrompt(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }
        String styled = ensureCaseFileIllustrationStyle(prompt);
        String normalized = styled.toLowerCase(Locale.ROOT);
        if (normalized.contains("if any person") || normalized.contains("every visible person")) {
            return styled;
        }
        return styled.trim()
                + " If any person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story's age and era. "
                + "Do not cast a Western or European-looking model. "
                + "Keep the result consistent with a calm printed mission archive card.";
    }

    private String buildEvidenceImagePrompt(AiEpisodeDraftResponse.EvidenceDraft draft) {
        String title = blank(draft.getTitle(), "해금 자료 카드");
        String summary = blank(draft.getTextSummary(), "현장 근거와 미션 파일을 연결해 다음 판단을 돕는 자료입니다.");

        return "Create a high-quality mission archive card illustration for a Korean outdoor story mission. "
                + "Subject: " + title + ". "
                + "Story detail: " + summary + ". "
                + missionArchiveIllustrationStylePrompt()
                + "If any person, hand, portrait, reflection, or human silhouette appears, it must belong to a fictional Korean person in Seoul and match the story era. "
                + missionArchiveNegativeImagePrompt();
    }

    private String ensureCaseFileIllustrationStyle(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }

        String normalized = prompt.toLowerCase(Locale.ROOT);
        String styled = prompt;

        if (!(normalized.contains("flat 2d korean webtoon")
                || normalized.contains("matte paper grain")
                || normalized.contains("mission archive card"))) {
            styled = prompt.trim() + " " + missionArchiveIllustrationStylePrompt();
        }

        String styledNormalized = styled.toLowerCase(Locale.ROOT);
        if (!styledNormalized.contains("no korean letters") || !styledNormalized.contains("no handwriting")) {
            styled += missionArchiveNegativeImagePrompt();
        }
        return styled;
    }

    private String caseFileIllustrationStylePrompt() {
        return missionArchiveIllustrationStylePrompt();
    }

    private String caseFileNegativeImagePrompt() {
        return missionArchiveNegativeImagePrompt();
    }

    private String missionArchiveIllustrationStylePrompt() {
        return "Visual style reference: flat 2D Korean webtoon / printed storybook illustration, muted earth-tone palette, soft matte paper grain, subtle archival texture, simplified shapes, clean dark ink outlines, gentle cel shading, calm documentary mood, poster-like composition, not photorealistic. "
                + "Match the story era exactly: Joseon, Daehan Empire, colonial modern, or contemporary Korean styling as specified by the story. "
                + "Use era-appropriate clothing, hair, props, architecture, paper materials, stamps, maps, route marks, archive labels, and handwritten-style marks. ";
    }

    private String missionArchiveNegativeImagePrompt() {
        return "Negative constraints: no photorealism, no 3D render, no glossy game art, no Western comic style, no European-looking models, no foreign tourist styling, no graphic violence, no threatening object emphasis, no modern objects unless the story era is contemporary, no readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark, no logo. ";
    }

    private Long resolveLinkedSuspectId(AiEpisodeDraftResponse.EvidenceDraft draft, List<CaseSuspect> suspects, int index) {
        if (suspects == null || suspects.isEmpty()) {
            return null;
        }
        return suspects.get(Math.floorMod(index, suspects.size())).getId();
    }




    private void applyDraftRewardPayloads(
            Long episodeId,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            List<AiEpisodeDraftResponse.MissionDraft> missions,
            Map<Integer, Puzzle> puzzleByOrder,
            Map<Integer, CaseEvidence> evidenceByMissionOrder
    ) {
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();

            Puzzle puzzle = puzzleByOrder.get(order);
            if (puzzle == null) {
                continue;
            }

            String clueType = switch (blank(mission.getClueRole(), "")) {
                case "ANSWER_HINT" -> "ANSWER_CLUE";
                case "DESTINATION_HINT", "FINAL_PLACE" -> "DESTINATION_CLUE";
                default -> "STORY_CLUE";
            };

            String rewardValue = sanitizeCategoryCodes(blank(
                    mission.getRewardClue(),
                    buildConcreteRewardClue(mission, draft, i)
            ));

            if (containsFinalKeywordText(rewardValue, draft)
                    || isGenericPuzzleAnswer(compact(rewardValue))
                    || isGenericRewardKey(rewardValue)
                    || containsCompact(rewardValue, "검수필요")) {
                rewardValue = buildConcreteRewardClue(mission, draft, i);
                mission.setRewardClue(rewardValue);
            }

            CaseEvidence evidence = evidenceByMissionOrder.get(order);

            List<Map<String, Object>> rewards = new ArrayList<>();

            Map<String, Object> clueReward = new LinkedHashMap<>();
            clueReward.put("type", clueType);
            clueReward.put("value", rewardValue);
            rewards.add(clueReward);

            if (evidence != null) {
                Map<String, Object> evidenceReward = new LinkedHashMap<>();
                evidenceReward.put("type", "EVIDENCE_UNLOCK");
                evidenceReward.put("targetId", evidence.getId());
                rewards.add(evidenceReward);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rewards", rewards);
            payload.put("interaction", buildPuzzleInteraction(draft, mission, i, clueType));

            puzzle.setRewardPayload(writeObjectJson(payload));

            AdminRewardPayloadValidationResponse validation =
                    validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));

            if (!validation.isValid()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRAFT_REWARD_PAYLOAD",
                        String.join(" / ", validation.getErrors())
                );
            }

            adminEpisodeRepository.updatePuzzle(puzzle);
        }
    }

    private Map<String, Object> buildPuzzleInteraction(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftResponse.MissionDraft mission,
            int index,
            String clueType
    ) {
        String basis = sanitizeCategoryCodes(firstGroundingText(mission));

        if (containsFinalKeywordText(basis, draft)
                || isGenericPuzzleAnswer(compact(basis))
                || isPlaceNameAnswer(compact(basis), mission.getPlaceName())) {
            basis = "검수필요";
        }

        String localSolution = localGameSolution(draft, mission, basis, index);
        String type = chooseInteractionType(mission, index, localSolution);

        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("version", 1);
        interaction.put("type", type);
        interaction.put("title", interactionTitle(type, clueType));
        interaction.put("prompt", sanitizeCategoryCodes(blank(mission.getQuestionText(), "단서 장치를 풀고 제출 버튼을 누르세요.")));
        interaction.put("storyHook", "해금 단서: " + sanitizeCategoryCodes(blank(mission.getRewardClue(), saveSafeRewardClue(mission, index))));
        interaction.put("basis", sanitizeCategoryCodes(basis));
        interaction.put("localSolution", localSolution);
        interaction.put("timeLimitSeconds", type.equals("RAPID_TAP") ? 12 : 0);
        interaction.put("config", interactionConfig(type, localSolution, basis, index));

        return interaction;
    }

    private String chooseInteractionType(AiEpisodeDraftResponse.MissionDraft mission, int index, String answer) {
        String puzzleType = normalizeType(mission.getPuzzleType());
        String answerFormat = normalizeType(mission.getAnswerFormat());
        if ("NUMBER_LOCK".equals(puzzleType) || "NUMBER".equals(answerFormat) || answer.matches("\\d{2,}")) return "NUMBER_LOCK";
        return switch (Math.floorMod(index, 10)) {
            case 0 -> "WORD_COMPOSE";
            case 1 -> "COLOR_CODE";
            case 2 -> "MEMORY_CARD";
            case 3 -> "PATTERN_LOCK";
            case 4 -> "SWITCH_TOGGLE";
            case 5 -> "RAPID_TAP";
            case 6 -> "DIRECTION_SEQUENCE";
            case 7 -> "SHADOW_FIND";
            case 8 -> "SLIDE_PUZZLE";
            default -> "WORD_COMPOSE";
        };
    }

    private Map<String, Object> interactionConfig(String type, String answer, String basis, int index) {
        Map<String, Object> config = new LinkedHashMap<>();
        switch (type) {
            case "NUMBER_LOCK" -> {
                String digits = answer.replaceAll("\\D", "");
                if (digits.isBlank()) digits = String.format("%04d", Math.abs((basis + index).hashCode()) % 10000);
                int digitCount = Math.min(6, Math.max(1, digits.length()));
                String solutionDigits = digits.substring(0, digitCount);
                config.put("digits", digitCount);
                config.put("initial", "0".repeat(digitCount));
                config.put("solutionDigits", solutionDigits);
            }
            case "WORD_COMPOSE" -> config.put("tiles", shuffledCharacters(answer));
            case "COLOR_CODE" -> {
                List<String> palette = List.of("#ef4444", "#f59e0b", "#10b981", "#3b82f6", "#111827");
                config.put("palette", palette);
                config.put("solution", colorSolution(answer, index, palette));
                config.put("labels", colorCodeLabels(answer, basis));
            }
            case "MEMORY_CARD" -> config.put("cards", memoryCards(answer, basis));
            case "PATTERN_LOCK" -> config.put("nodes", patternNodes(answer, index));
            case "SWITCH_TOGGLE" -> {
                List<String> switches = switchLabels(answer, basis);
                config.put("switches", switches);
                config.put("targetStates", switchTargetStates(switches.size()));
            }
            case "RAPID_TAP" -> {
                config.put("target", Math.min(9, Math.max(5, answer.length() + 2)));
                config.put("label", basis);
            }
            case "DIRECTION_SEQUENCE" -> config.put("sequence", directionSequence(answer, index));
            case "SHADOW_FIND" -> {
                int targetIndex = Math.floorMod(Math.abs((answer + basis + index).hashCode()), 4);
                List<String> shadows = shadowLabels(answer, basis, targetIndex);
                config.put("label", basis);
                config.put("targetIndex", targetIndex);
                config.put("shadows", shadows);
            }
            case "SLIDE_PUZZLE" -> {
                List<String> tiles = slideTiles(answer, basis);
                config.put("tiles", tiles);
                config.put("initialTiles", scrambledSlideTiles(tiles));
            }
            default -> config.put("label", basis);
        }
        return config;
    }

    private String interactionTitle(String type, String clueType) {
        String prefix = switch (clueType) {
            case "ANSWER_CLUE" -> "증거 해독";
            case "DESTINATION_CLUE" -> "동선 복원";
            default -> "미션 장치";
        };
        return prefix + " · " + switch (type) {
            case "NUMBER_LOCK" -> "숫자 락";
            case "WORD_COMPOSE" -> "단어 조합";
            case "COLOR_CODE" -> "색상 코드";
            case "MEMORY_CARD" -> "기억 카드";
            case "PATTERN_LOCK" -> "패턴 잠금";
            case "SWITCH_TOGGLE" -> "스위치 토글";
            case "RAPID_TAP" -> "빠른 탭";
            case "DIRECTION_SEQUENCE" -> "방향키 조합";
            case "SHADOW_FIND" -> "그림자 찾기";
            case "SLIDE_PUZZLE" -> "슬라이드 퍼즐";
            default -> "단서 입력";
        };
    }

    private String safeInteractionAnswer(AiEpisodeDraftResponse.MissionDraft mission) {
        String answer = blank(mission.getAnswer(), firstGroundingText(mission));
        if (containsCompact(answer, "검수필요") || containsCompact(answer, "review-required")) {
            return firstGroundingText(mission);
        }
        return answer;
    }

    private String localGameSolution(
            AiEpisodeDraftResponse.EpisodeDraft draft,
            AiEpisodeDraftResponse.MissionDraft mission,
            String basis,
            int index
    ) {
        String source = blank(basis, "");

        if (missing(source)
                || containsCompact(source, "검수필요")
                || containsCompact(source, "review-required")
                || containsFinalKeywordText(source, draft)
                || isGenericPuzzleAnswer(compact(source))
                || isPlaceNameAnswer(compact(source), mission.getPlaceName())) {
            source = saveSafeLocalSolution(mission, index);
        }

        source = source.trim();

        if (source.length() > 8) {
            source = source.substring(0, 8);
        }

        return source;
    }

    private List<String> shuffledCharacters(String answer) {
        String source = safeUiSource(answer, "확인어");

        List<String> chars = new ArrayList<>(source.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(value -> !value.isBlank())
                .toList());

        while (chars.size() < 2) {
            chars.add("확인");
            chars.add("기록");
        }

        java.util.Collections.rotate(chars, Math.max(1, chars.size() / 2));
        return chars;
    }

    private List<String> memoryCards(String answer, String basis) {
        return uiOptionLabels(answer, basis, 4, "기억카드");
    }

    private List<Integer> patternNodes(String answer, int index) {
        int seed = Math.abs((answer + index).hashCode());
        List<Integer> nodes = new ArrayList<>();
        for (int divisor : List.of(1, 3, 7, 11, 13)) {
            int node = (seed / divisor) % 9;
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
            if (nodes.size() >= 4) {
                break;
            }
        }
        return nodes;
    }

    private List<String> switchLabels(String answer, String basis) {
        return uiOptionLabels(answer, basis, 4, "스위치");
    }

    private List<String> colorSolution(String answer, int index, List<String> palette) {
        int seed = Math.abs((answer + index).hashCode());
        int length = Math.min(4, Math.max(3, answer.length()));
        List<String> solution = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            solution.add(palette.get((seed + i * 2) % palette.size()));
        }
        return solution;
    }

    private List<Boolean> switchTargetStates(int size) {
        List<Boolean> states = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            states.add(i == 0 || i == 1);
        }
        return states;
    }

    private List<String> shadowLabels(String answer, String basis, int targetIndex) {
        List<String> labels = uiOptionLabels(answer, basis, 4, "그림자후보");

        String target = safeUiSource(basis, safeUiSource(answer, "확인어"));
        labels.set(targetIndex, target);

        return labels;
    }

    private List<String> directionSequence(String answer, int index) {
        List<String> directions = List.of("UP", "RIGHT", "DOWN", "LEFT");
        int seed = Math.abs((answer + index).hashCode());
        return List.of(
                directions.get(seed % directions.size()),
                directions.get((seed / 3) % directions.size()),
                directions.get((seed / 7) % directions.size()),
                directions.get((seed / 11) % directions.size())
        );
    }

    private List<String> slideTiles(String answer, String basis) {
        String source = safeUiSource(answer, safeUiSource(basis, "확인어"));

        List<String> tiles = new ArrayList<>(source.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(value -> !value.isBlank())
                .limit(4)
                .toList());

        List<String> fallbackTiles = List.of("확", "인", "기", "록");

        while (tiles.size() < 4) {
            tiles.add(fallbackTiles.get(tiles.size()));
        }

        return tiles;
    }

    private List<String> scrambledSlideTiles(List<String> tiles) {
        List<String> initial = new ArrayList<>(tiles);
        java.util.Collections.reverse(initial);
        if (initial.equals(tiles) && initial.size() > 1) {
            java.util.Collections.rotate(initial, 1);
        }
        return initial;
    }

    private String writeObjectJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"rewards\":[]}";
        }
    }

    private void saveDraftPartnerReward(Long episodeId) {
        EpisodePartnerReward reward = new EpisodePartnerReward();
        reward.setEpisodeId(episodeId);
        reward.setTitle("지역 리워드 검수 필요");
        reward.setDescription("공개 전 실제 제휴처, 제공 조건, 사용 가능 기간, 위치 정보를 확인해야 하는 임시 리워드입니다.");
        reward.setRewardType("STAMP");
        reward.setPartnerName("제휴처 검수 필요");
        reward.setLocationName("리워드 제공 지점 검수 필요");
        reward.setStatus("PLANNED");

        adminEpisodeRepository.insertPartnerReward(reward);
    }
    private String validateEvidenceTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] targetId가 필요합니다.");
            return null;
        }

        return adminEpisodeRepository.findEvidences(episodeId).stream()
                .filter(evidence -> targetId.equals(evidence.getId()))
                .findFirst()
                .map(CaseEvidence::getTitle)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + "에 해당하는 해금 자료 카드를 찾을 수 없습니다.");
                    return null;
                });
    }


    private String validateSuspectTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] targetId가 필요합니다.");
            return null;
        }

        return adminEpisodeRepository.findSuspects(episodeId).stream()
                .filter(suspect -> targetId.equals(suspect.getId()))
                .findFirst()
                .map(CaseSuspect::getDisplayName)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + "에 해당하는 관계자 카드를 찾을 수 없습니다.");
                    return null;
                });
    }


    private Long validateOptionalSpot(Long episodeId, Long requestedId, Long fallbackId) {
        Long id = requestedId == null ? fallbackId : requestedId;

        if (id == null) {
            return null;
        }

        boolean exists = adminEpisodeRepository.findSpots(episodeId).stream()
                .anyMatch(spot -> id.equals(spot.getId()));

        if (!exists) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SOURCE_SPOT",
                    "연결하려는 조사 지점을 찾을 수 없습니다."
            );
        }

        return id;
    }

    private Long validateOptionalSuspect(Long episodeId, Long requestedId, Long fallbackId) {
        Long id = requestedId == null ? fallbackId : requestedId;

        if (id == null) {
            return null;
        }

        boolean exists = adminEpisodeRepository.findSuspects(episodeId).stream()
                .anyMatch(suspect -> id.equals(suspect.getId()));

        if (!exists) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RELATED_SUSPECT",
                    "연결하려는 관계자 카드를 찾을 수 없습니다."
            );
        }

        return id;
    }

    private void validatePublishReadiness(Episode episode) {
        List<String> errors = new ArrayList<>();

        if (missing(episode.getFictionSynopsis())) {
            errors.add("스토리 시놉시스가 필요합니다.");
        }

        if (missing(episode.getFinalAnswerType())) {
            errors.add("최종 정답 유형이 필요합니다.");
        }

        if (missing(episode.getFinalAnswer()) || containsCompact(episode.getFinalAnswer(), "검수필요")) {
            errors.add("최종 정답을 확정해야 합니다.");
        }

        if (missing(episode.getFinalQuestion())) {
            errors.add("최종 질문이 필요합니다.");
        }

        if (missing(episode.getFinalTruthSummary())) {
            errors.add("관리자용 최종 진실 요약이 필요합니다.");
        }

        if (missing(episode.getActualHistorySummary())) {
            errors.add("실제 배경 해설이 필요합니다.");
        }

        if (missing(episode.getDeductionSecretFacts())) {
            errors.add("최종 추론용 비공개 사실이 필요합니다.");
        }

        if (missing(episode.getDeductionForbiddenReveals())) {
            errors.add("최종 추론 중 노출 금지어가 필요합니다.");
        } else if (!containsCompact(episode.getDeductionForbiddenReveals(), episode.getFinalAnswer())) {
            errors.add("노출 금지어에 최종 정답이 포함되어야 합니다.");
        }

        if (episode.getMaxDeductionQuestions() == null || episode.getMaxDeductionQuestions() < 1) {
            errors.add("최대 추리 질문 수는 1 이상이어야 합니다.");
        }

        List<String> finalKeywords = finalKeywordsFromEpisode(episode);

        if (finalKeywords.isEmpty()) {
            errors.add("finalAnswerAliases에 KW:키워드1|키워드2 형식의 최종 정답 키워드 계약이 필요합니다.");
        }

        for (String keyword : finalKeywords) {
            if (!textContains(episode.getFinalAnswer(), keyword)) {
                errors.add("최종 정답에 필수 키워드가 포함되어야 합니다: " + keyword);
            }

            if (!containsCompact(episode.getDeductionForbiddenReveals(), keyword)) {
                errors.add("노출 금지어에 최종 정답 키워드가 포함되어야 합니다: " + keyword);
            }
        }

        validatePublicTextAnswerLeak("제목", episode.getTitle(), episode, finalKeywords, errors);
        validatePublicTextAnswerLeak("부제", episode.getSubtitle(), episode, finalKeywords, errors);
        validatePublicTextAnswerLeak("스토리 시놉시스", episode.getFictionSynopsis(), episode, finalKeywords, errors);
        validatePublicTextAnswerLeak("최종 질문", episode.getFinalQuestion(), episode, finalKeywords, errors);

        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episode.getId());

        if (spots.size() < MIN_EPISODE_SPOTS) {
            errors.add("공개 에피소드는 최소 " + MIN_EPISODE_SPOTS + "개 이상의 장소가 필요합니다.");
        }

        if (spots.size() > MAX_EPISODE_SPOTS) {
            errors.add("공개 에피소드는 최대 " + MAX_EPISODE_SPOTS + "개 장소를 초과할 수 없습니다.");
        }

        long startCount = spots.stream()
                .filter(spot -> "START".equals(spot.getMarkerType()))
                .count();

        long finalPlaceCount = spots.stream()
                .filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace()) || "FINAL".equals(spot.getMarkerType()))
                .count();

        long answerHintCount = spots.stream()
                .filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType()))
                .count();

        long destinationHintCount = spots.stream()
                .filter(spot -> "DESTINATION_HINT".equals(spot.getMarkerType()))
                .count();

        if (startCount != 1) {
            errors.add("START 장소는 정확히 1개여야 합니다.");
        }

        if (finalPlaceCount != 1) {
            errors.add("내부 최종 장소는 정확히 1개여야 합니다.");
        }

        if (spots.size() >= 5 && answerHintCount < 1) {
            errors.add("장소가 5개 이상이면 ANSWER_HINT 장소가 최소 1개 필요합니다.");
        }

        if (spots.size() >= 5 && destinationHintCount < 1) {
            errors.add("장소가 5개 이상이면 DESTINATION_HINT 장소가 최소 1개 필요합니다.");
        }

        for (MissionSpot spot : spots) {
            String spotName = blank(spot.getPlaceName(), "이름 없는 장소");

            if (missing(spot.getPlaceName()) || spot.getLatitude() == null || spot.getLongitude() == null) {
                errors.add("모든 장소에는 장소명과 좌표가 필요합니다: " + spotName);
            }

            if (spot.getArrivalRadius() == null || spot.getArrivalRadius() < 10) {
                errors.add("도착 반경은 최소 10m 이상이어야 합니다: " + spotName);
            }

            if ("FINAL".equals(spot.getPublicMarkerType())) {
                errors.add("publicMarkerType으로 FINAL을 노출하면 안 됩니다: " + spotName);
            }

            if (Boolean.TRUE.equals(spot.getFinalPlace()) || "FINAL".equals(spot.getMarkerType())) {
                if (!"DESTINATION_HINT".equals(spot.getPublicMarkerType())) {
                    errors.add("내부 최종 장소의 publicMarkerType은 DESTINATION_HINT여야 합니다: " + spotName);
                }

                if (!"FINAL_PLACE".equals(spot.getClueRole())) {
                    errors.add("내부 최종 장소의 clueRole은 FINAL_PLACE여야 합니다: " + spotName);
                }
            }

            if (same(episode.getFinalAnswer(), spot.getPlaceName())) {
                errors.add("최종 정답이 실제 장소명과 같으면 안 됩니다: " + spotName);
            }

            validatePublicTextAnswerLeak("장소 스토리", spot.getStoryText(), episode, finalKeywords, errors);

            for (String keyword : finalKeywords) {
                if (same(keyword, spot.getPlaceName())) {
                    errors.add("최종 정답 키워드가 실제 장소명과 같으면 안 됩니다: " + spotName);
                }
            }

            Puzzle puzzle = adminEpisodeRepository.findPuzzleBySpotId(spot.getId());

            if (puzzle == null) {
                errors.add("퍼즐이 누락되었습니다: " + spotName);
                continue;
            }

            if (missing(puzzle.getQuestionText())) {
                errors.add("퍼즐 질문이 누락되었습니다: " + spotName);
            }

            if (missing(puzzle.getAnswer())) {
                errors.add("퍼즐 정답이 누락되었습니다: " + spotName);
            }

            if (missing(puzzle.getRewardClue())) {
                errors.add("해금 단서가 누락되었습니다: " + spotName);
            }

            if (containsCompact(puzzle.getAnswer(), "검수필요")
                    || containsCompact(puzzle.getAnswer(), "review-required")
                    || isGenericPuzzleAnswer(compact(puzzle.getAnswer()))) {
                errors.add("퍼즐 정답을 관리자 검수 후 확정해야 합니다: " + spotName);
            }
            if (isLowQualityGenericValue(puzzle.getRewardClue()) || isTooShortRewardClue(puzzle.getRewardClue())) {
                errors.add("해금 단서가 실제 추론에 사용하기에는 너무 일반적입니다: " + spotName);
            }

            if (same(puzzle.getAnswer(), puzzle.getRewardClue())) {
                errors.add("퍼즐 정답과 해금 단서는 달라야 합니다: " + spotName);
            }

            if (isPlaceNameAnswer(compact(puzzle.getAnswer()), spot.getPlaceName())) {
                errors.add("퍼즐 정답이 장소명과 같으면 안 됩니다: " + spotName);
            }

            if (textContainsAny(puzzle.getAnswer(), finalKeywords)) {
                errors.add("퍼즐 정답에 최종 정답 키워드가 노출되었습니다: " + spotName);
            }

            if (textContainsAny(puzzle.getQuestionText(), finalKeywords)) {
                errors.add("퍼즐 질문에 최종 정답 키워드가 노출되었습니다: " + spotName);
            }

            if (textContainsAny(puzzle.getRewardClue(), finalKeywords)) {
                errors.add("해금 단서에 최종 정답 키워드가 노출되었습니다: " + spotName);
            }

            if (textContains(puzzle.getAnswer(), episode.getFinalAnswer())) {
                errors.add("퍼즐 정답에 최종 정답이 노출되었습니다: " + spotName);
            }

            if (textContains(puzzle.getQuestionText(), episode.getFinalAnswer())) {
                errors.add("퍼즐 질문에 최종 정답이 노출되었습니다: " + spotName);
            }

            if (textContains(puzzle.getRewardClue(), episode.getFinalAnswer())) {
                errors.add("해금 단서에 최종 정답이 노출되었습니다: " + spotName);
            }

            List<PuzzleHint> hints = adminEpisodeRepository.findHints(puzzle.getId());

            if (hints.size() < 3) {
                errors.add("퍼즐 힌트는 3개가 필요합니다: " + spotName);
            }

            for (PuzzleHint hint : hints) {
                if (textContainsAny(hint.getHintText(), finalKeywords)) {
                    errors.add("퍼즐 힌트에 최종 정답 키워드가 노출되었습니다: " + spotName);
                }

                if (textContains(hint.getHintText(), episode.getFinalAnswer())) {
                    errors.add("퍼즐 힌트에 최종 정답이 노출되었습니다: " + spotName);
                }

                if (textContains(hint.getHintText(), puzzle.getAnswer())) {
                    errors.add("퍼즐 힌트가 퍼즐 정답을 직접 노출합니다: " + spotName);
                }
            }

            validateRewardPayloadForPublish(episode, puzzle, spotName, finalKeywords, errors);
        }

        List<CaseSuspect> suspects = adminEpisodeRepository.findSuspects(episode.getId());
        List<CaseEvidence> evidences = adminEpisodeRepository.findEvidences(episode.getId());

        if (suspects.size() < 3) {
            errors.add("관계자 카드는 최소 3개가 필요합니다.");
        }

        for (CaseSuspect suspect : suspects) {
            String suspectName = blank(suspect.getDisplayName(), "이름 없는 관계자");

            if (missing(suspect.getImagePrompt()) && !validExternalImageUrl(suspect.getPortraitImageUrl())) {
                errors.add("관계자 카드에는 이미지 프롬프트 또는 외부 이미지 URL이 필요합니다: " + suspectName);
            }

            if (textContainsAny(suspect.getDisplayName(), finalKeywords)
                    || textContainsAny(suspect.getShortDescription(), finalKeywords)
                    || textContainsAny(suspect.getSuspiciousPoint(), finalKeywords)
                    || textContainsAny(suspect.getAlibiSummary(), finalKeywords)) {
                errors.add("관계자 카드에 최종 정답 키워드가 노출되었습니다: " + suspectName);
            }

            if (textContains(suspect.getDisplayName(), episode.getFinalAnswer())
                    || textContains(suspect.getShortDescription(), episode.getFinalAnswer())
                    || textContains(suspect.getSuspiciousPoint(), episode.getFinalAnswer())
                    || textContains(suspect.getAlibiSummary(), episode.getFinalAnswer())) {
                errors.add("관계자 카드에 최종 정답이 노출되었습니다: " + suspectName);
            }
            if (isLowQualityCardText(suspect.getShortDescription())) {
                errors.add("관계자 카드 설명에 구체적인 행동이나 모순이 필요합니다: " + suspectName);
            }
        }

        if (evidences.size() < Math.max(1, spots.size() - 1)) {
            errors.add("해금 자료 카드는 전체 동선을 설명할 수 있을 만큼 필요합니다.");
        }

        for (CaseEvidence evidence : evidences) {
            String evidenceTitle = blank(evidence.getTitle(), "이름 없는 해금 자료");

            if (missing(evidence.getImagePrompt()) && !validExternalImageUrl(evidence.getImageUrl())) {
                errors.add("해금 자료 카드에는 이미지 프롬프트 또는 외부 이미지 URL이 필요합니다: " + evidenceTitle);
            }
            if (isLowQualityCardText(evidence.getTextSummary())) {
                errors.add("해금 자료 카드 설명에 구체적인 흔적이나 관계가 필요합니다: " + evidenceTitle);
            }

            if (textContainsAny(evidence.getTitle(), finalKeywords)
                    || textContainsAny(evidence.getTextSummary(), finalKeywords)) {
                errors.add("해금 자료 카드에 최종 정답 키워드가 노출되었습니다: " + evidenceTitle);
            }

            if (textContains(evidence.getTitle(), episode.getFinalAnswer())
                    || textContains(evidence.getTextSummary(), episode.getFinalAnswer())) {
                errors.add("해금 자료 카드에 최종 정답이 노출되었습니다: " + evidenceTitle);
            }
        }

        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EPISODE_PUBLISH_NOT_READY",
                    "공개 불가: " + String.join(" / ", errors)
            );
        }
    }
    private List<String> finalKeywordsFromEpisode(Episode episode) {
        if (episode == null || missing(episode.getFinalAnswerAliases())) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();

        String[] aliases = episode.getFinalAnswerAliases().split("[,\\n]");

        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }

            String trimmed = alias.trim();

            if (!trimmed.startsWith("KW:")) {
                continue;
            }

            String raw = trimmed.substring(3);
            String[] parts = raw.split("\\|");

            for (String part : parts) {
                if (part != null && !part.isBlank()) {
                    keywords.add(part.trim());
                }
            }
        }

        return keywords.stream()
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean textContainsAny(String text, List<String> targets) {
        if (missing(text) || targets == null || targets.isEmpty()) {
            return false;
        }

        return targets.stream()
                .filter(value -> !missing(value))
                .anyMatch(target -> containsExactAnswerValue(text, target));
    }

    private void validatePublicTextAnswerLeak(
            String fieldLabel,
            String text,
            Episode episode,
            List<String> finalKeywords,
            List<String> errors
    ) {
        if (missing(text)) {
            return;
        }
        if (textContains(text, episode.getFinalAnswer())) {
            errors.add(fieldLabel + "에 최종 정답이 노출되었습니다.");
            return;
        }
        if (textContainsAny(text, finalKeywords)) {
            errors.add(fieldLabel + "에 최종 정답 키워드가 노출되었습니다.");
        }
    }

    private boolean containsExactAnswerValue(String text, String value) {
        if (missing(text) || missing(value)) {
            return false;
        }

        String normalizedText = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalizedValue = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String compactValue = compact(normalizedValue);

        if (compactValue.length() <= 2) {
            if (same(normalizedText, normalizedValue)) {
                return true;
            }
            for (String token : normalizedText.split("[\\s\\p{Punct}·|/]+")) {
                if (same(token, normalizedValue)) {
                    return true;
                }
            }
            return false;
        }

        return normalizedText.contains(normalizedValue)
                || compact(normalizedText).contains(compactValue);
    }

    private void validateRewardPayloadForPublish(
            Episode episode,
            Puzzle puzzle,
            String spotName,
            List<String> finalKeywords,
            List<String> errors
    ) {
        String payload = puzzle.getRewardPayload();

        if (missing(payload)) {
            errors.add("reward_payload가 누락되었습니다: " + spotName);
            return;
        }

        AdminRewardPayloadValidationResponse validation =
                validateRewardPayload(episode.getId(), AdminRewardPayloadValidationRequestWrapper.of(payload));

        if (!validation.isValid()) {
            errors.add("reward_payload 형식이 올바르지 않습니다: " + spotName + " / " + String.join(", ", validation.getErrors()));
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);

            JsonNode rewards = root.path("rewards");
            if (!rewards.isArray() || rewards.isEmpty()) {
                errors.add("reward_payload에는 rewards 배열이 필요합니다: " + spotName);
            }

            for (JsonNode reward : rewards) {
                String type = reward.path("type").asText("");
                String value = reward.path("value").asText("");
                boolean valueBasedClue = List.of("STORY_CLUE", "ANSWER_CLUE", "DESTINATION_CLUE").contains(type);

                if (!valueBasedClue) {
                    continue;
                }

                if (textContainsAny(value, finalKeywords)) {
                    errors.add("reward_payload에 최종 정답 키워드가 노출되었습니다: " + spotName);
                }

                if (textContains(value, episode.getFinalAnswer())) {
                    errors.add("reward_payload에 최종 정답이 노출되었습니다: " + spotName);
                }

                if (isGenericPuzzleAnswer(compact(value))) {
                    errors.add("reward_payload의 value가 너무 일반적인 값입니다: " + spotName);
                }
            }

            JsonNode interaction = root.path("interaction");

            if (interaction.isMissingNode() || !interaction.isObject()) {
                errors.add("reward_payload에 미니게임 interaction 정보가 누락되었습니다: " + spotName);
                return;
            }

            String localSolution = interaction.path("localSolution").asText("");
            String basis = interaction.path("basis").asText("");
            String prompt = interaction.path("prompt").asText("");
            String storyHook = interaction.path("storyHook").asText("");

            if (missing(localSolution)) {
                errors.add("미니게임 localSolution이 누락되었습니다: " + spotName);
            }

            if (containsCompact(localSolution, "검수필요")
                    || containsCompact(localSolution, "review-required")
                    || isGenericPuzzleAnswer(compact(localSolution))) {
                errors.add("미니게임 localSolution을 관리자 검수 후 확정해야 합니다: " + spotName);
            }

            if (textContainsAny(localSolution, finalKeywords)
                    || textContainsAny(basis, finalKeywords)
                    || textContainsAny(prompt, finalKeywords)
                    || textContainsAny(storyHook, finalKeywords)) {
                errors.add("미니게임 interaction에 최종 정답 키워드가 노출되었습니다: " + spotName);
            }

            if (textContains(localSolution, episode.getFinalAnswer())
                    || textContains(basis, episode.getFinalAnswer())
                    || textContains(prompt, episode.getFinalAnswer())
                    || textContains(storyHook, episode.getFinalAnswer())) {
                errors.add("미니게임 interaction에 최종 정답이 노출되었습니다: " + spotName);
            }

        } catch (Exception e) {
            errors.add("reward_payload는 올바른 JSON 형식이어야 합니다: " + spotName);
        }
    }

    private boolean validExternalImageUrl(String value) {
        if (missing(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return value.length() <= 1000 && (normalized.startsWith("https://") || normalized.startsWith("http://"));
    }

    private String sanitizeCategoryCodes(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("KakaoLocal:CE7", "카페/커피 휴식 지점")
                .replace("KakaoLocal:FD6", "음식점/식당 상권")
                .replace("KakaoLocal:CT1", "문화시설/전시 지점")
                .replace("KakaoLocal:AT4", "관광명소/명소 지점")
                .replace("CE7", "카페/커피 휴식 지점")
                .replace("FD6", "음식점/식당 상권")
                .replace("CT1", "문화시설/전시 지점")
                .replace("AT4", "관광명소/명소 지점");
    }




    @Transactional
    public AdminEpisodeDetailResponse createEpisode(AdminEpisodeUpdateRequest request) {
        AdminEpisodeUpdateRequest safeRequest = request == null ? new AdminEpisodeUpdateRequest() : request;

        String requestedStatus = text(safeRequest.getStatus(), "DRAFT");
        if ("PUBLISHED".equals(normalizeType(requestedStatus))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DRAFT_REVIEW_REQUIRED",
                    "새 에피소드는 먼저 DRAFT로 생성한 뒤 장소, 퍼즐, 정답, 보상 payload를 검수하고 공개하세요."
            );
        }

        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String title = blank(safeRequest.getTitle(), "신규 스토리 미션 초안 " + uniqueSuffix);

        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(safeRequest.getSubtitle(), "관리자 수동 생성 초안"));
        episode.setEra(blank(safeRequest.getEra(), "시대 검수 필요"));
        episode.setGenre(blank(safeRequest.getGenre(), "야외 스토리 미션"));
        episode.setDifficulty(blank(safeRequest.getDifficulty(), "NORMAL"));
        episode.setEstimatedTime(blank(safeRequest.getEstimatedTime(), "90~120분"));
        episode.setEstimatedDistance(blank(safeRequest.getEstimatedDistance(), "도보 동선 확인 필요"));

        episode.setFictionSynopsis(blank(
                safeRequest.getFictionSynopsis(),
                "관리자가 직접 작성해야 하는 스토리 미션 시놉시스입니다. 공개 전 실제 장소 동선, 픽션 설정, 최종 목표를 연결해 보강하세요."
        ));

        episode.setFinalAnswerType(blank(safeRequest.getFinalAnswerType(), "HIDDEN_TRUTH"));
        episode.setFinalAnswer(blank(safeRequest.getFinalAnswer(), "검수필요"));
        episode.setFinalAnswerAliases(safeRequest.getFinalAnswerAliases());

        episode.setFinalQuestion(blank(
                safeRequest.getFinalQuestion(),
                "모든 단서를 종합했을 때 이 미션의 최종 결론은 무엇인가?"
        ));

        episode.setFinalTruthSummary(blank(
                safeRequest.getFinalTruthSummary(),
                "관리자만 확인하는 최종 진실 요약입니다. 공개 전 정답 키워드, 단서 연결 방식, 최종 장소의 역할을 구체적으로 작성하세요."
        ));

        episode.setActualHistorySummary(blank(
                safeRequest.getActualHistorySummary(),
                """
                1. 모티브 공개
                이 임무는 실제 장소의 역사·문화적 배경을 모티브로 제작된 스토리 미션입니다.
    
                2. 실제 배경 해설
                공개 전 관리자는 공식 해설 자료, 현장 표지, TourAPI 설명을 확인해 실제 역사 배경과 픽션 요소의 차이를 보강해야 합니다.
                """.trim()
        ));

        episode.setDeductionSecretFacts(blank(
                safeRequest.getDeductionSecretFacts(),
                """
                최종 정답은 관리자가 확정한 핵심 키워드를 모두 포함해야 한다.
                일부 장소명이나 단서명만 맞히는 답은 최종 정답이 아니다.
                정답은 실제 장소명이나 실존 인물명이 아니라 픽션 미션 안의 결론이어야 한다.
                """.trim()
        ));

        episode.setDeductionForbiddenReveals(blank(
                safeRequest.getDeductionForbiddenReveals(),
                """
                검수필요
                실제 최종 장소명
                실존 인물의 부정적 역할화
                """.trim()
        ));

        episode.setMaxDeductionQuestions(safeRequest.getMaxDeductionQuestions() == null
                ? 20
                : Math.max(1, safeRequest.getMaxDeductionQuestions()));

        episode.setRecommendedPlayers(blank(safeRequest.getRecommendedPlayers(), "2~4명"));
        episode.setTeamRoleGuide(blank(
                safeRequest.getTeamRoleGuide(),
                "지도 담당, 미션 파일 담당, 퍼즐 담당, 기록 담당으로 역할을 나누어 진행하세요."
        ));
        episode.setNoticeText(blank(
                safeRequest.getNoticeText(),
                "공개 전 현장 좌표, 도착 반경, 퍼즐 정답, 힌트, reward_payload를 반드시 검수하세요."
        ));

        episode.setStatus(validateValue(
                text(safeRequest.getStatus(), "DRAFT"),
                EPISODE_STATUSES,
                "INVALID_EPISODE_STATUS",
                "status는 DRAFT, PUBLISHED, ARCHIVED 중 하나여야 합니다."
        ));
        adminEpisodeRepository.insertEpisode(episode);
        return getEpisode(episode.getId());
    }




    @Transactional
    public void deleteEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);

        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PUBLISHED_EPISODE_DELETE_BLOCKED",
                    "공개된 에피소드는 삭제할 수 없습니다. 먼저 DRAFT 또는 ARCHIVED 상태로 변경한 뒤 삭제하세요."
            );
        }

        adminEpisodeRepository.deleteEpisode(episodeId);
    }

    private Episode requireEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);

        if (episode == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EPISODE_NOT_FOUND",
                    "에피소드를 찾을 수 없습니다."
            );
        }

        return episode;
    }

    private void requireEditableEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);

        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PUBLISHED_EPISODE_LOCKED",
                    "공개된 에피소드는 직접 수정할 수 없습니다. 수정이 필요하면 먼저 DRAFT 상태로 되돌린 뒤 작업하세요."
            );
        }
    }
    private String text(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String validateValue(String value, Set<String> allowed, String code, String message) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return normalized;
    }

    private AiEpisodeDraftRequest.PlaceInput enrichPlace(AiEpisodeDraftRequest.PlaceInput source) {
        List<AdminPlaceCandidateResponse> rankedNearby = rankedNearbyCandidates(source);
        AiEpisodeDraftRequest.PlaceInput target = new AiEpisodeDraftRequest.PlaceInput();
        target.setName(source.getName());
        target.setAddress(source.getAddress());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setDescription(enrichedDescription(source, rankedNearby));
        target.setVisibleElements(mergeDistinct(source.getVisibleElements(), inferredVisibleElements(rankedNearby)));
        target.setNumbers(source.getNumbers() == null ? List.of() : source.getNumbers());
        target.setKeywords(mergeDistinct(source.getKeywords(), focusedKeywords(source, rankedNearby)));
        target.setAdminMemo(enrichedAdminMemo(source, rankedNearby));
        target.setRole(source.getRole());
        target.setPublicMarkerType(source.getPublicMarkerType());
        target.setArrivalRadius(source.getArrivalRadius());
        target.setPlaceId(source.getPlaceId());
        target.setDataQuality(source.getDataQuality());
        target.setUsablePuzzleSources(source.getUsablePuzzleSources());
        target.setVerificationNotes(source.getVerificationNotes());
        return target;
    }

    private List<AdminPlaceCandidateResponse> rankedNearbyCandidates(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getLatitude() == null || place.getLongitude() == null) {
            return List.of();
        }
        try {
            return kakaoLocalCandidateService.getNearbyCandidates(place.getLatitude(), place.getLongitude(), 900).stream()
                    .sorted((left, right) -> Double.compare(siteSignalScore(right, place), siteSignalScore(left, place)))
                    .limit(8)
                    .toList();
        } catch (ApiException e) {
            return List.of(AdminPlaceCandidateResponse.builder()
                    .title("external-search-failed:" + e.getCode())
                    .description(e.getMessage())
                    .source("RAG_ERROR")
                    .build());
        }
    }

    private double siteSignalScore(AdminPlaceCandidateResponse candidate, AiEpisodeDraftRequest.PlaceInput anchor) {
        String value = compact(String.join(" ", blank(candidate.getTitle(), ""), blank(candidate.getAddress(), ""), blank(candidate.getSource(), ""), blank(candidate.getDescription(), "")));
        double score = 0;
        if (containsCompact(value, "culture") || containsCompact(value, "museum") || containsCompact(value, "gallery") || containsCompact(value, "exhibition") || containsCompact(value, "history")) score += 45;
        if (containsCompact(value, "palace") || containsCompact(value, "gate") || containsCompact(value, "heritage")) score += 34;
        if (containsCompact(value, "park") || containsCompact(value, "square") || containsCompact(value, "street") || containsCompact(value, "market") || containsCompact(value, "bookstore")) score += 28;
        if (containsCompact(value, "KakaoLocal:CT1") || containsCompact(value, "KakaoLocal:AT4") || containsCompact(value, "문화시설") || containsCompact(value, "관광명소")) score += 30;
        if (containsCompact(value, "KakaoLocal:CE7") || containsCompact(value, "KakaoLocal:FD6") || containsCompact(value, "카페") || containsCompact(value, "음식점")) score += 20;
        double distance = distanceMeters(anchor.getLatitude(), anchor.getLongitude(), candidate.getLatitude(), candidate.getLongitude());
        if (Double.isFinite(distance)) {
            if (distance >= 80 && distance <= 700) score += 25;
            score -= Math.min(25, distance / 120.0);
        }
        return score;
    }




    private String enrichedDescription(AiEpisodeDraftRequest.PlaceInput source, List<AdminPlaceCandidateResponse> rankedNearby) {
        String base = blank(source.getDescription(), "선택된 조사 지점입니다.");
        String topSignals = rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(3)
                .map(AdminPlaceCandidateResponse::getTitle)
                .collect(Collectors.joining(", "));
        if (topSignals.isBlank()) {
            return base;
        }
        return base + " 주변 확인 후보: " + topSignals + ".";
    }

    private List<String> focusedKeywords(AiEpisodeDraftRequest.PlaceInput place, List<AdminPlaceCandidateResponse> rankedNearby) {
        return List.of();
    }


    private List<String> inferredVisibleElements(List<AdminPlaceCandidateResponse> rankedNearby) {
        return List.of();
    }

    private String enrichedAdminMemo(AiEpisodeDraftRequest.PlaceInput place, List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> memo = new ArrayList<>();
        if (!missing(place.getAdminMemo())) {
            memo.add(place.getAdminMemo());
        }
        memo.add("RAG/사이트 보강으로 주변 Kakao Local 신호를 사용해 관리자 확인 범위를 좁혔습니다.");
        memo.add("이 신호는 확인 후보로만 사용하세요. 간판, 숫자, 조형물, 영업시간은 현장 확인 전까지 확정 정보로 취급하지 않습니다.");
        if (place.getLatitude() == null || place.getLongitude() == null) {
            memo.add("좌표가 없어 외부 검색을 실행하지 못했습니다. 공개 전 위도/경도를 추가하세요.");
            return String.join("\n", memo);
        }
        if (rankedNearby.isEmpty()) {
            memo.add("900m 이내에서 주변 신호를 찾지 못했습니다. 관리자가 수동 현장 메모를 추가하세요.");
            return String.join("\n", memo);
        }
        List<AdminPlaceCandidateResponse> usable = rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(5)
                .toList();
        if (usable.isEmpty()) {
            rankedNearby.stream().findFirst().ifPresent(candidate -> memo.add(candidate.getTitle() + " - " + blank(candidate.getDescription(), "외부 검색 실패")));
            return String.join("\n", memo);
        }
        memo.add("주요 확인 후보:");
        for (int i = 0; i < usable.size(); i++) {
            AdminPlaceCandidateResponse candidate = usable.get(i);
            memo.add((i + 1) + ". " + candidate.getTitle()
                    + " / " + categoryKeyword(candidate)
                    + " / 약 " + Math.round(distanceMeters(place.getLatitude(), place.getLongitude(), candidate.getLatitude(), candidate.getLongitude())) + "m"
                    + " / 확인 대상: " + categoryVisibleElement(candidate));
        }
        memo.add("권장 퍼즐 근거: 현장 확인 후 관리자가 확정한 visibleElements/numbers만 사용하세요. 그 전에는 AI가 실제 관찰 사실이 아닌 스토리 단서와 확인용 임시 단서만 만듭니다.");
        return String.join("\n", memo);
    }

    private String categoryKeyword(AdminPlaceCandidateResponse candidate) {
        String source = blank(candidate.getSource(), "");
        String value = String.join(" ",
                blank(candidate.getTitle(), ""),
                blank(candidate.getSource(), ""),
                blank(candidate.getDescription(), ""),
                blank(candidate.getAddress(), "")
        );

        if (source.contains("CT1")
                || containsCompact(value, "문화시설")
                || containsCompact(value, "culture")
                || containsCompact(value, "museum")
                || containsCompact(value, "gallery")
                || containsCompact(value, "exhibition")) {
            return "문화전시 후보";
        }

        if (source.contains("AT4")
                || containsCompact(value, "관광")
                || containsCompact(value, "명소")
                || containsCompact(value, "heritage")
                || containsCompact(value, "palace")
                || containsCompact(value, "gate")) {
            return "관광명소 후보";
        }

        if (source.contains("CE7")
                || containsCompact(value, "카페")
                || containsCompact(value, "cafe")
                || containsCompact(value, "coffee")) {
            return "카페쉼터 후보";
        }

        if (source.contains("FD6")
                || containsCompact(value, "음식점")
                || containsCompact(value, "식당")
                || containsCompact(value, "restaurant")
                || containsCompact(value, "food")) {
            return "식당상권 후보";
        }

        if (containsCompact(value, "park")
                || containsCompact(value, "square")
                || containsCompact(value, "street")
                || containsCompact(value, "공원")
                || containsCompact(value, "광장")
                || containsCompact(value, "거리")) {
            return "공개동선 후보";
        }

        return "주변확인 후보";
    }




    private String categoryVisibleElement(AdminPlaceCandidateResponse candidate) {
        String keyword = categoryKeyword(candidate);

        return switch (keyword) {
            case "문화전시 후보" -> "전시 안내문, 운영 안내, 건물 표지 중 실제 확인 가능한 요소";
            case "관광명소 후보" -> "공식 안내판, 문화재 표지, 입구 안내 중 실제 확인 가능한 요소";
            case "카페쉼터 후보" -> "입구 표지, 메뉴판, 영업 안내 중 실제 확인 가능한 요소";
            case "식당상권 후보" -> "가게 간판, 상권 안내, 입구 표지 중 실제 확인 가능한 요소";
            case "공개동선 후보" -> "공공 표지석, 동선 안내표, 방향 안내 중 실제 확인 가능한 요소";
            default -> "관리자가 현장에서 직접 확인해야 하는 요소";
        };
    }




    private double distanceMeters(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.POSITIVE_INFINITY;
        }
        double earthRadius = 6_371_000;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lng2 - lng1);
        double h = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private List<String> mergeDistinct(List<String> first, List<String> second) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (first != null) {
            first.stream().filter(value -> value != null && !value.isBlank()).forEach(value -> unique.putIfAbsent(value.trim(), value.trim()));
        }
        if (second != null) {
            second.stream().filter(value -> value != null && !value.isBlank()).forEach(value -> unique.putIfAbsent(value.trim(), value.trim()));
        }
        return new ArrayList<>(unique.values());
    }

    private AdminEpisodeProgressStats safeStats(Long episodeId) {
        AdminEpisodeProgressStats stats = adminEpisodeRepository.findProgressStats(episodeId);
        return stats == null ? new AdminEpisodeProgressStats() : stats;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeRole(String role, int index, int total) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT", "FINAL").contains(normalized)) {
            return normalized;
        }
        if (index == 0) return "START";
        if (index >= total - 1) return "FINAL";
        if (index >= total - 4) return "DESTINATION_HINT";
        return "ANSWER_HINT";
    }

    private String publicMarkerType(String requested, boolean finalPlace, String markerType) {
        if (finalPlace) {
            return "DESTINATION_HINT";
        }
        String fallback = "FINAL".equals(markerType) ? "DESTINATION_HINT" : markerType;
        String value = blank(requested, fallback);
        return validateValue(
                value,
                PUBLIC_MARKER_TYPES,
                "INVALID_PUBLIC_MARKER_TYPE",
                "publicMarkerType으로 FINAL을 노출할 수 없습니다."
        );
    }

    private String toClueRole(String markerType) {
        return switch (markerType) {
            case "START" -> "START";
            case "DESTINATION_HINT" -> "DESTINATION_HINT";
            case "FINAL" -> "FINAL_PLACE";
            default -> "ANSWER_HINT";
        };
    }

    private String recommendPuzzleType(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return "NUMBER_LOCK";
        if (place.getKeywords() != null && place.getKeywords().stream().anyMatch(keyword -> keyword != null && keyword.length() <= 4)) return "INITIAL_SOUND";
        if (place.getVisibleElements() != null && place.getVisibleElements().size() >= 3) return "OBSERVATION";
        return "STORY_COMBINATION";
    }

    private String buildQuestion(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) {
            return "제공된 현장 숫자 중 미션 파일 기록과 연결되는 값을 입력하세요.";
        }

        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) {
            String element = place.getVisibleElements().get(0);
            return "현장에서 확인 가능한 요소 [" + element + "]를 미션 메모와 대조해 확인어를 입력하세요.";
        }

        if (!missing(place.getAdminMemo())) {
            return "관리자 메모에 기록된 현장 근거를 확인하고 짧은 확인어를 입력하세요.";
        }

        return "관리자 검수 후 이 지점의 실제 현장 근거를 기준으로 퍼즐 정답을 확정하세요.";
    }




    private boolean isMissionAnswerDisconnected(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            List<AiEpisodeDraftResponse.MissionDraft> missions
    ) {
        if (mission == null || missing(mission.getAnswer())) {
            return false;
        }

        String answer = compact(mission.getAnswer());

        if (answer.isBlank() || answer.contains("검수필요") || "review-required".equals(answer)) {
            return false;
        }

        if ("NUMBER".equals(normalizeType(mission.getAnswerFormat()))
                && mission.getAnswer().replaceAll("\\D", "").length() >= 1) {
            return false;
        }

        if (containsFinalKeywordText(mission.getAnswer(), draft)) {
            return true;
        }

        if (isGenericPuzzleAnswer(answer)) {
            return true;
        }

        if (isPlaceNameAnswer(answer, mission.getPlaceName())) {
            return true;
        }

        return missions != null && missions.stream()
                .map(AiEpisodeDraftResponse.MissionDraft::getPlaceName)
                .anyMatch(placeName -> isPlaceNameAnswer(answer, placeName));
    }

    private boolean isPlaceNameAnswer(String compactAnswer, String placeName) {
        if (compactAnswer == null || compactAnswer.isBlank() || missing(placeName)) {
            return false;
        }
        String compactPlaceName = compact(placeName);
        return compactPlaceName.equals(compactAnswer)
                || compactAnswer.equals(compactPlaceName)
                || (compactPlaceName.length() >= 4 && compactAnswer.contains(compactPlaceName));
    }

    private boolean isGenericPuzzleAnswer(String compactAnswer) {
        if (compactAnswer == null || compactAnswer.isBlank()) {
            return true;
        }

        return Set.of(
                "placedescription",
                "adminmemo",
                "casememo",
                "selectedoperationspot",
                "selected",
                "operation",
                "spot",
                "nearby",
                "verification",
                "focus",
                "place",
                "address",
                "entrance",
                "area",
                "siteverificationfocus",
                "nearbyfamousplacesignal",

                "memo",
                "record",
                "document",
                "clue",
                "info",
                "truth",
                "secret",
                "object",
                "event",

                "메모",
                "기록",
                "문서",
                "단서",
                "정보",
                "진실",
                "비밀",
                "물건",
                "사건",
                "흔적",
                "표식",
                "사진",
                "봉인",
                "그림자",
                "현장",
                "현장단서",
                "관리자검수",
                "검수필요",
                "확인필요",
                "보정단서",
                "검수단서",
                "서울",
                "seoul",
                "kakao",
                "kakaolocal",
                "tourapi",
                "tourapibased",
                "placecandidate",
                "장소후보",
                "후보지",
                "관광지",

                "문화전시후보",
                "관광명소후보",
                "카페쉼터후보",
                "식당상권후보",
                "공개동선후보",
                "주변확인후보",
                "관리자가현장에서직접확인해야하는요소",
                "전시안내문운영안내건물표지중실제확인가능한요소",
                "공식안내판문화재표지입구안내중실제확인가능한요소",
                "입구표지메뉴판영업안내중실제확인가능한요소",
                "가게간판상권안내입구표지중실제확인가능한요소",
                "공공표지석동선안내표방향안내중실제확인가능한요소",
                "현장에서확인할장소명간판",
                "현장에서확인할주소와입구영역",
                "현장에서확인할주변동선단서",
                "현장표지물또는주변구조물"
        ).contains(compactAnswer) || isLowQualityGenericValue(compactAnswer);
    }

    private void normalizeMissionForReview(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            List<AiEpisodeDraftResponse.MissionDraft> missions,
            int index
    ) {
        String basis = firstGroundingText(mission);

        if (missing(basis)
                || containsCompact(basis, "검수필요")
                || containsCompact(basis, "review-required")
                || isGenericPuzzleAnswer(compact(basis))
                || isPlaceNameAnswer(compact(basis), mission.getPlaceName())
                || containsFinalKeywordText(basis, draft)) {
            basis = "검수필요";
        }

        mission.setPuzzleType("STORY_COMBINATION");
        mission.setAnswerFormat("TEXT");

        if ("검수필요".equals(basis)) {
            mission.setQuestionText("관리자 검수 후 이 지점의 실제 현장 근거를 기준으로 퍼즐 정답을 확정하세요.");
            mission.setAnswer("검수필요");
            mission.setRewardClue(saveSafeRewardClue(mission, index));
            mission.setHints(List.of(
                    "이 미션은 자동 생성만으로 정답을 확정하기 어렵습니다.",
                    "현장에서 실제 확인 가능한 표지, 숫자, 조형물, 문구를 먼저 기록하세요.",
                    "관리자 화면에서 정답과 근거를 직접 보강한 뒤 공개하세요."
            ));
            return;
        }

        mission.setQuestionText("제공된 현장 근거 [" + basis + "]를 미션 파일 카드와 연결한 확인어를 입력하세요.");
        mission.setAnswer(basis);

        if (missing(mission.getRewardClue())
                || containsCompact(mission.getRewardClue(), "검수필요")
                || containsFinalKeywordText(mission.getRewardClue(), draft)) {
            mission.setRewardClue(saveSafeRewardClue(mission, index));
        }

        mission.setHints(List.of(
                "문제에 제시된 [" + basis + "] 근거를 먼저 확인하세요.",
                "장소명 글자 추출이 아니라 현장 근거와 미션 파일의 의미 연결을 보세요.",
                "정답은 최종 정답 키워드가 아니라 이 미션에서 확인 가능한 짧은 근거어입니다."
        ));
    }

    private String sanitizeHintText(String hint, AiEpisodeDraftResponse.MissionDraft mission) {
        String text = hint == null ? "" : hint.trim();
        String compactText = compact(text);

        if (text.isBlank()
                || isEnglishOnlyHint(text)
                || compactText.contains("가장최근")
                || compactText.contains("최근보상")
                || compactText.contains("이전증거")
                || compactText.contains("이전사건자료")
                || textContains(text, mission.getAnswer())) {
            return saveSafeHint(0, mission);
        }

        return text;
    }

    private boolean isEnglishOnlyHint(String text) {
        if (text == null || text.isBlank() || text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3)) {
            return false;
        }
        return text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count() >= 3;
    }

    private String firstGroundingText(AiEpisodeDraftResponse.MissionDraft mission) {
        if (mission == null) {
            return "검수필요";
        }

        if (!missing(mission.getAnswer())
                && !containsCompact(mission.getAnswer(), "검수필요")
                && !isGenericPuzzleAnswer(compact(mission.getAnswer()))
                && !isPlaceNameAnswer(compact(mission.getAnswer()), mission.getPlaceName())) {
            return mission.getAnswer().trim();
        }

        if (!missing(mission.getGroundRule())
                && !containsCompact(mission.getGroundRule(), "검수필요")) {
            String extracted = extractShortKoreanBasis(mission.getGroundRule());
            if (!missing(extracted)
                    && !isGenericPuzzleAnswer(compact(extracted))
                    && !isPlaceNameAnswer(compact(extracted), mission.getPlaceName())) {
                return extracted;
            }
        }

        if (!missing(mission.getRewardClue())
                && !containsCompact(mission.getRewardClue(), "검수필요")
                && !isGenericPuzzleAnswer(compact(mission.getRewardClue()))
                && !isPlaceNameAnswer(compact(mission.getRewardClue()), mission.getPlaceName())) {
            return mission.getRewardClue().trim();
        }

        return "검수필요";
    }

    private String buildAnswer(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return place.getNumbers().get(0);
        if (place.getKeywords() != null) {
            String keyword = place.getKeywords().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!missing(keyword)) return keyword;
        }
        if (place.getVisibleElements() != null) {
            String visible = place.getVisibleElements().stream()
                    .filter(value -> isUsableAnswerBasis(value, place.getName()))
                    .findFirst()
                    .orElse(null);
            if (!missing(visible)) return visible;
        }
        return "검수필요";
    }

    private boolean isUsableAnswerBasis(String value, String placeName) {
        if (missing(value)) {
            return false;
        }
        String compactValue = compact(value);
        return !isGenericPuzzleAnswer(compactValue) && !isPlaceNameAnswer(compactValue, placeName);
    }

    private String answerFormat(AiEpisodeDraftRequest.PlaceInput place) {
        return place.getNumbers() != null && !place.getNumbers().isEmpty() ? "NUMBER" : "TEXT";
    }

    private String buildRewardClue(String role, int index) {
        return switch (role) {
            case "ANSWER_HINT" -> "answer-clue-" + (index + 1);
            case "DESTINATION_HINT", "FINAL" -> "destination-clue-" + (index + 1);
            case "START" -> "story-clue-" + (index + 1);
            default -> "story-clue-" + (index + 1);
        };
    }

    private String estimateDraftDistance(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (missions == null || missions.size() < 2) return "도보 동선 확인 필요";
        double meters = 0;
        for (int i = 1; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft prev = missions.get(i - 1);
            AiEpisodeDraftResponse.MissionDraft current = missions.get(i);
            double segment = distanceMeters(prev.getLatitude(), prev.getLongitude(), current.getLatitude(), current.getLongitude());
            if (Double.isFinite(segment)) meters += segment;
        }
        if (meters <= 0) return "도보 동선 확인 필요";
        double km = Math.round((meters / 1000.0) * 10.0) / 10.0;
        return "약 " + km + "km";
    }




    private String resolveDraftTitle(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftResponse.MissionDraft> missions) {
        String candidate = blank(draft.getEpisodeTitle(), "");

        if (!isGenericDraftTitle(candidate)) {
            return uniqueDraftTitle(candidate);
        }

        String anchor = missions.stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType()))
                .map(AiEpisodeDraftResponse.MissionDraft::getPlaceName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> missions.stream()
                        .map(AiEpisodeDraftResponse.MissionDraft::getPlaceName)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse("Operation KOREA"));

        return uniqueDraftTitle("EP.NEW " + anchor + " 스토리 미션");
    }

    private boolean isGenericDraftTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }

        String normalized = title.toLowerCase(Locale.ROOT).replace(" ", "");

        return normalized.equals("ep.new")
                || normalized.equals("episode")
                || normalized.equals("draft")
                || normalized.equals("case")
                || normalized.equals("ep.newepisode")
                || normalized.equals("ep.newdraft")
                || normalized.equals("ep.newcase")
                || normalized.contains("operationkoreacase")
                || normalized.contains("operationkoreaepisode")
                || normalized.contains("operationkoreadraft");
    }

    private String uniqueDraftTitle(String title) {
        String base = blank(title, "EP.NEW Operation KOREA 스토리 미션");

        boolean duplicate = adminEpisodeRepository.findAllEpisodes().stream()
                .anyMatch(episode -> base.equals(episode.getTitle()));

        if (!duplicate) {
            return base;
        }

        return base + " " + (System.currentTimeMillis() % 100000);
    }

    private String generatedEvidenceImage(String type) {
        return switch (normalizeType(type)) {
            case "PHOTO" -> "/generated-case-card-photo.svg";
            case "MEMO", "POST_IT" -> "/generated-case-card-memo.svg";
            case "DOCUMENT", "EVIDENCE", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE" -> "/generated-case-card-document.svg";
            case "SUSPECT_CLUE" -> "/generated-case-card-suspect.svg";
            default -> "/generated-case-card-note.svg";
        };
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String join(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).collect(Collectors.joining(","));
    }

    private List<String> withKeywordContract(List<String> aliases, List<String> keywords) {
        List<String> values = new ArrayList<>();
        if (aliases != null) {
            aliases.stream()
                    .filter(value -> value != null && !value.isBlank() && !value.startsWith("KW:"))
                    .map(String::trim)
                    .forEach(values::add);
        }
        List<String> required = keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (!required.isEmpty()) {
            values.add("KW:" + String.join("|", required));
        }
        return values;
    }

    private String joinLines(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).collect(Collectors.joining("\n"));
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean missing(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsCompact(String text, String target) {
        if (missing(text) || missing(target)) {
            return false;
        }
        return compact(text).contains(compact(target));
    }

    private boolean same(String a, String b) {
        if (missing(a) || missing(b)) {
            return false;
        }
        return compact(a).equals(compact(b));
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private List<String> publishChecklist() {
        return List.of(
                "모든 장소의 좌표와 도착 반경이 실제 이동 가능한 범위인지 확인하세요.",
                "각 퍼즐 정답이 장소명, 최종 정답, 일반 단어가 아닌지 확인하세요.",
                "최종 장소는 내부 markerType만 FINAL이고, publicMarkerType으로는 FINAL이 노출되지 않아야 합니다.",
                "최종 정답은 실제 장소명, 실존 인물명, 실제 사건명을 그대로 사용하지 않아야 합니다.",
                "finalAnswerAliases에 KW:키워드1|키워드2 형식의 최종 정답 키워드 계약이 포함되어야 합니다.",
                "힌트, reward_clue, reward_payload, 미니게임 localSolution에 최종 정답 키워드가 노출되지 않는지 확인하세요.",
                "관계자 카드와 해금 자료 카드가 최종 정답을 직접 말하지 않는지 확인하세요.",
                "제휴 리워드는 실제 제공 조건이 확정되기 전까지 PLANNED 또는 DISABLED 상태로 유지하세요."
        );
    }





    private List<String> extractBlockingIssues(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        String normalized = message;
        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < normalized.length()) {
            normalized = normalized.substring(colonIndex + 1);
        }
        return java.util.Arrays.stream(normalized.split(" / "))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record AdminRewardPayloadValidationRequestWrapper(String rewardPayload) {
        static AdminRewardPayloadValidationRequestWrapper of(String rewardPayload) {
            return new AdminRewardPayloadValidationRequestWrapper(rewardPayload);
        }
    }

    private record AreaSeed(double lat, double lng) {
    }

    private boolean containsFinalKeywordText(String text, AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (missing(text) || draft == null) {
            return false;
        }

        if (!missing(draft.getFinalAnswer()) && containsExactAnswerValue(text, draft.getFinalAnswer())) {
            return true;
        }

        if (draft.getFinalAnswerKeywords() != null) {
            for (String keyword : draft.getFinalAnswerKeywords()) {
                if (!missing(keyword) && containsExactAnswerValue(text, keyword)) {
                    return true;
                }
            }
        }

        if (draft.getFinalAnswerAliases() != null) {
            for (String alias : draft.getFinalAnswerAliases()) {
                if (missing(alias)) {
                    continue;
                }

                if (!alias.trim().startsWith("KW:") && containsExactAnswerValue(text, alias)) {
                    return true;
                }

                if (alias.trim().startsWith("KW:")) {
                    String[] parts = alias.trim().substring(3).split("\\|");
                    for (String part : parts) {
                        if (!missing(part) && containsExactAnswerValue(text, part)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private String saveSafeQuestionText(AiEpisodeDraftResponse.MissionDraft mission) {
        String role = normalizeType(mission.getClueRole());

        if ("ANSWER_HINT".equals(role)) {
            return "이 지점의 현장 근거와 미션 파일을 대조해 정답 후보를 좁히는 보조 단서를 확인하세요.";
        }

        if ("DESTINATION_HINT".equals(role) || "FINAL_PLACE".equals(role)) {
            return "이 지점의 동선 근거와 미션 파일을 대조해 다음 위치 조건을 확인하세요.";
        }

        if ("START".equals(role)) {
            return "첫 미션 파일을 열고 작전의 시작 단서를 확인하세요.";
        }

        return "현장 근거와 미션 파일을 대조해 이 지점의 확인 단어를 입력하세요.";
    }

    private String saveSafeRewardClue(AiEpisodeDraftResponse.MissionDraft mission, int index) {
        String role = normalizeType(mission == null ? null : mission.getClueRole());

        if ("ANSWER_HINT".equals(role)) {
            return "answer-clue-" + (index + 1);
        }

        if ("DESTINATION_HINT".equals(role) || "FINAL_PLACE".equals(role)) {
            return "destination-clue-" + (index + 1);
        }

        return "story-clue-" + (index + 1);
    }

    private String buildConcreteRewardClue(
            AiEpisodeDraftResponse.MissionDraft mission,
            AiEpisodeDraftResponse.EpisodeDraft draft,
            int index) {
        String basis = mission == null ? null : mission.getAnswer();
        if (missing(basis)
                || isGenericPuzzleAnswer(compact(basis))
                || isPlaceNameAnswer(compact(basis), mission.getPlaceName())
                || containsFinalKeywordText(basis, draft)) {
            basis = extractShortKoreanBasis(mission == null ? null : mission.getGroundRule());
        }
        if (missing(basis)
                || isGenericPuzzleAnswer(compact(basis))
                || isPlaceNameAnswer(compact(basis), mission == null ? null : mission.getPlaceName())
                || containsFinalKeywordText(basis, draft)) {
            basis = "겹쳐 표시된 방향";
        }

        String role = normalizeType(mission == null ? null : mission.getClueRole());
        return switch (role) {
            case "ANSWER_HINT" -> basis + "이 가리키는 역할의 흔적";
            case "DESTINATION_HINT", "FINAL_PLACE" -> basis + "을 따라 이어지는 방향의 흔적";
            default -> basis + " 주변에 반복된 배열의 흔적";
        };
    }

    private boolean isGenericRewardKey(String value) {
        String compactValue = compact(value);
        return compactValue.startsWith("answer-clue-")
                || compactValue.startsWith("destination-clue-")
                || compactValue.startsWith("story-clue-");
    }

    private boolean isLowQualityGenericValue(String value) {
        String compactValue = compact(value).replaceAll("\\d+$", "");
        return Set.of(
                "동선확인", "증거확인", "최종검토", "기록확인", "단서확인", "자료확인",
                "현장확인", "미션확인", "흐름확인", "연결단서", "보조자료", "핵심자료",
                "관계단서", "작전개시"
        ).contains(compactValue);
    }

    private boolean isTooShortRewardClue(String value) {
        String compactValue = compact(value);
        return !compactValue.matches("\\d+")
                && compactValue.codePointCount(0, compactValue.length()) < 8;
    }

    private boolean isLowQualityCardText(String value) {
        if (missing(value)) {
            return true;
        }
        if (value.contains("서로 맞지 않는 기록을 비교")
                || value.contains("목격 자료입니다")
                || value.contains("정답 값을 직접 밝히지 않고")) {
            return true;
        }
        return List.of(
                "관계자의 행동 이유와 미션 흐름을 연결하는 단서입니다",
                "핵심 자료의 정체를 좁혀주는 보조 자료입니다",
                "다음 판단을 돕는 자료입니다",
                "기본 관계자 카드입니다",
                "연결 가능성이 있는 가상 관계자입니다"
        ).stream().anyMatch(text -> containsCompact(value, text));
    }

    private String naturalFinalAnswer(List<String> keywords) {
        if (keywords.size() == 1) {
            return "모든 흔적이 가리킨 진실은 " + keywords.get(0) + "이었다.";
        }
        if (keywords.size() == 2) {
            return withSubject(keywords.get(0)) + " 마지막 흔적이 가리킨 " + keywords.get(1) + "에 숨겨져 있었다.";
        }
        return withSubject(keywords.get(0)) + " " + withObject(keywords.get(1)) + " 옮겼고, "
                + withSubject(String.join(", ", keywords.subList(2, keywords.size())))
                + " 성립하는 순간 기록을 확인하려 했다.";
    }

    private String withSubject(String value) {
        return value + (hasFinalConsonant(value) ? "이" : "가");
    }

    private String withObject(String value) {
        return value + (hasFinalConsonant(value) ? "을" : "를");
    }

    private boolean hasFinalConsonant(String value) {
        if (missing(value)) {
            return false;
        }
        String trimmed = value.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String saveSafeHint(int index, AiEpisodeDraftResponse.MissionDraft mission) {
        return switch (index) {
            case 0 -> "문제에 제시된 현장 근거를 먼저 확인하세요.";
            case 1 -> "장소명이나 최종 정답이 아니라 이 지점에서 확인 가능한 단서 역할을 보세요.";
            default -> "해금 단서와 미션 파일을 함께 대조해 다음 판단 근거를 좁히세요.";
        };
    }

    private String saveSafeLocalSolution(AiEpisodeDraftResponse.MissionDraft mission, int index) {
        String role = normalizeType(mission == null ? null : mission.getClueRole());

        if ("ANSWER_HINT".equals(role)) {
            return "증거" + (index + 1);
        }

        if ("DESTINATION_HINT".equals(role) || "FINAL_PLACE".equals(role)) {
            return "동선" + (index + 1);
        }

        if ("START".equals(role)) {
            return "시작" + (index + 1);
        }

        return "단서" + (index + 1);
    }

    private String extractShortKoreanBasis(String text) {
        if (missing(text)) {
            return null;
        }

        String cleaned = text
                .replaceAll("[\\[\\]{}()\"'`.,:;!?/\\\\|<>]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String token : cleaned.split(" ")) {
            String candidate = token.trim();

            if (candidate.length() < 2 || candidate.length() > 8) {
                continue;
            }

            if (isGenericPuzzleAnswer(compact(candidate))) {
                continue;
            }

            if (candidate.chars().noneMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3)
                    && !candidate.matches("\\d+")) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    private boolean textContains(String text, String target) {
        if (missing(text) || missing(target)) {
            return false;
        }

        String compactText = compact(text);
        String compactTarget = compact(target);

        return !compactTarget.isBlank() && compactText.contains(compactTarget);
    }

    private List<String> colorCodeLabels(String answer, String basis) {
        return uiOptionLabels(answer, basis, 5, "색상카드");
    }

    private List<String> uiOptionLabels(String answer, String basis, int count, String prefix) {
        List<String> values = new ArrayList<>();

        String safeAnswer = safeUiSource(answer, null);
        String safeBasis = safeUiSource(basis, null);

        if (!missing(safeAnswer)) {
            values.add(safeAnswer);
        }

        if (!missing(safeBasis) && values.stream().noneMatch(value -> same(value, safeBasis))) {
            values.add(safeBasis);
        }

        int index = 1;
        while (values.size() < count) {
            values.add(prefix + "-" + index++);
        }

        return values.stream()
                .filter(value -> !missing(value))
                .distinct()
                .limit(count)
                .toList();
    }

    private String safeUiSource(String value, String fallback) {
        if (missing(value)) {
            return fallback;
        }

        String trimmed = value.trim();

        if (containsCompact(trimmed, "검수필요")
                || containsCompact(trimmed, "review-required")
                || isGenericPuzzleAnswer(compact(trimmed))) {
            return fallback;
        }

        if (trimmed.length() > 8) {
            return trimmed.substring(0, 8);
        }

        return trimmed;
    }
}
