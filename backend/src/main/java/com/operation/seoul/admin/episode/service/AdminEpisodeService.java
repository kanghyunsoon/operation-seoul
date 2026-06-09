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
                String title = blank(place.get("title"), "Review required.");
                String address = place.get("address");
                String key = (title + "|" + address + "|" + lat + "|" + lng).toLowerCase(Locale.ROOT);
                unique.putIfAbsent(key, AdminPlaceCandidateResponse.builder()
                        .title(title)
                        .address(address)
                        .latitude(lat)
                        .longitude(lng)
                        .areaCode(normalizedAreaCode)
                        .source(place.getOrDefault("source", "TourAPI"))
                        .description(place.getOrDefault("overview", "Review required."))
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SITE_ENRICHMENT_INPUT", "Review required.");
        }
        AiEpisodeDraftRequest enriched = new AiEpisodeDraftRequest();
        enriched.setArea(request.getArea());
        enriched.setEra(request.getEra());
        enriched.setTheme(request.getTheme());
        enriched.setTargetAudience(request.getTargetAudience());
        enriched.setPlayTime(request.getPlayTime());
        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            places.add(enrichPlace(place));
        }
        enriched.setPlaces(places);
        return enriched;
    }

    public AdminEpisodeDetailResponse getEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);
        if (episode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "Review required.");
        }
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
                    .message("Review required.")
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
                    .message("Review required.")
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
        episode.setStatus(validateValue(text(request.getStatus(), episode.getStatus()), EPISODE_STATUSES, "INVALID_EPISODE_STATUS", "Review required."));
        if ("PUBLISHED".equals(episode.getStatus())) {
            validatePublishReadiness(episode);
        }
        adminEpisodeRepository.updateEpisode(episode);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updateSpot(Long episodeId, Long spotId, AdminSpotUpdateRequest request) {
        requireEpisode(episodeId);
        MissionSpot spot = adminEpisodeRepository.findSpots(episodeId).stream()
                .filter(item -> spotId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "Review required."));
        spot.setPlaceName(text(request.getPlaceName(), spot.getPlaceName()));
        spot.setAddress(text(request.getAddress(), spot.getAddress()));
        spot.setLatitude(request.getLatitude() == null ? spot.getLatitude() : request.getLatitude());
        spot.setLongitude(request.getLongitude() == null ? spot.getLongitude() : request.getLongitude());
        spot.setMarkerType(validateValue(text(request.getMarkerType(), spot.getMarkerType()), MARKER_TYPES, "INVALID_MARKER_TYPE", "Unsupported markerType."));
        spot.setClueRole(validateValue(text(request.getClueRole(), spot.getClueRole()), CLUE_ROLES, "INVALID_CLUE_ROLE", "Unsupported clueRole."));
        spot.setPublicMarkerType(validateValue(text(request.getPublicMarkerType(), spot.getPublicMarkerType()), PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType must not expose FINAL."));
        spot.setStoryText(text(request.getStoryText(), spot.getStoryText()));
        spot.setArrivalRadius(request.getArrivalRadius() == null ? spot.getArrivalRadius() : Math.max(10.0, request.getArrivalRadius()));
        spot.setFinalPlace(request.getFinalPlace() == null ? spot.getFinalPlace() : request.getFinalPlace());
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
        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episodeId);
        if (spots.size() >= 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_SPOTS", "Review required.");
        }
        MissionSpot spot = new MissionSpot();
        spot.setEpisodeId(episodeId);
        spot.setPlaceName(text(request.getPlaceName(), "Review required."));
        spot.setAddress(text(request.getAddress(), ""));
        spot.setLatitude(request.getLatitude() == null ? 37.5665 : request.getLatitude());
        spot.setLongitude(request.getLongitude() == null ? 126.9780 : request.getLongitude());
        spot.setMarkerType(validateValue(text(request.getMarkerType(), "ANSWER_HINT"), MARKER_TYPES, "INVALID_MARKER_TYPE", "Unsupported markerType."));
        spot.setClueRole(validateValue(text(request.getClueRole(), toClueRole(spot.getMarkerType())), CLUE_ROLES, "INVALID_CLUE_ROLE", "Unsupported clueRole."));
        spot.setPublicMarkerType(validateValue(text(request.getPublicMarkerType(), spot.getMarkerType()), PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType must not expose FINAL."));
        spot.setStoryText(text(request.getStoryText(), "Review required."));
        spot.setArrivalRadius(request.getArrivalRadius() == null ? 50.0 : Math.max(10.0, request.getArrivalRadius()));
        spot.setFinalPlace(Boolean.TRUE.equals(request.getFinalPlace()));
        if (Boolean.TRUE.equals(spot.getFinalPlace())) {
            spot.setMarkerType("FINAL");
            spot.setClueRole("FINAL_PLACE");
            spot.setPublicMarkerType("DESTINATION_HINT");
        }
        adminEpisodeRepository.insertSpot(spot);

        Puzzle puzzle = new Puzzle();
        puzzle.setMissionSpotId(spot.getId());
        puzzle.setPuzzleType("OBSERVATION");
        puzzle.setQuestionText("Review required.");
        puzzle.setAnswer("관리자검수");
        puzzle.setAnswerFormat("TEXT");
        puzzle.setRewardClue("Review required.");
        puzzle.setRewardPayload("Reward payload requires admin review.");
        puzzle.setDifficulty("NORMAL");
        adminEpisodeRepository.insertPuzzle(puzzle);
        adminEpisodeRepository.insertHint(puzzle.getId(), 1, "Review required.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 2, "Review required.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 3, "Review required.");
        return getEpisode(episodeId);
    }

    @Transactional
    public AdminEpisodeDetailResponse deleteSpot(Long episodeId, Long spotId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findSpots(episodeId).stream().anyMatch(spot -> spotId.equals(spot.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "Review required.");
        }
        adminEpisodeRepository.detachEvidencesBySpotId(spotId);
        adminEpisodeRepository.deleteHintsBySpotId(spotId);
        adminEpisodeRepository.deletePuzzlesBySpotId(spotId);
        adminEpisodeRepository.deleteSpot(spotId);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updatePuzzle(Long episodeId, Long puzzleId, AdminPuzzleUpdateRequest request) {
        requireEpisode(episodeId);
        Puzzle puzzle = adminEpisodeRepository.findPuzzle(puzzleId);
        if (puzzle == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "Puzzle not found.");
        }
        boolean belongsToEpisode = adminEpisodeRepository.findSpots(episodeId).stream()
                .anyMatch(spot -> spot.getId().equals(puzzle.getMissionSpotId()));
        if (!belongsToEpisode) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "Review required.");
        }
        puzzle.setPuzzleType(validateValue(text(request.getPuzzleType(), puzzle.getPuzzleType()), PUZZLE_TYPES, "INVALID_PUZZLE_TYPE", "Unsupported puzzleType."));
        puzzle.setQuestionText(text(request.getQuestionText(), puzzle.getQuestionText()));
        puzzle.setAnswer(text(request.getAnswer(), puzzle.getAnswer()));
        puzzle.setAnswerFormat(validateValue(text(request.getAnswerFormat(), puzzle.getAnswerFormat()), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "Unsupported answerFormat."));
        puzzle.setRewardClue(text(request.getRewardClue(), puzzle.getRewardClue()));
        puzzle.setRewardPayload(text(request.getRewardPayload(), puzzle.getRewardPayload()));
        AdminRewardPayloadValidationResponse validation = validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));
        if (!validation.isValid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REWARD_PAYLOAD", String.join(" / ", validation.getErrors()));
        }
        puzzle.setDifficulty(text(request.getDifficulty(), puzzle.getDifficulty()));
        adminEpisodeRepository.updatePuzzle(puzzle);
        if (request.getHints() != null) {
            adminEpisodeRepository.deleteHints(puzzleId);
            int level = 1;
            for (String hint : request.getHints()) {
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
        requireEpisode(episodeId);
        CaseSuspect suspect = adminEpisodeRepository.findSuspects(episodeId).stream()
                .filter(item -> suspectId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUSPECT_NOT_FOUND", "Review required."));
        suspect.setDisplayName(text(request.getDisplayName(), suspect.getDisplayName()));
        suspect.setAlias(text(request.getAlias(), suspect.getAlias()));
        suspect.setShortDescription(text(request.getShortDescription(), suspect.getShortDescription()));
        suspect.setPortraitImageUrl(text(request.getPortraitImageUrl(), suspect.getPortraitImageUrl()));
        suspect.setRelationToVictim(text(request.getRelationToVictim(), suspect.getRelationToVictim()));
        suspect.setSuspiciousPoint(text(request.getSuspiciousPoint(), suspect.getSuspiciousPoint()));
        suspect.setAlibiSummary(text(request.getAlibiSummary(), suspect.getAlibiSummary()));
        suspect.setUnlockedByDefault(request.getUnlockedByDefault() == null ? suspect.getUnlockedByDefault() : request.getUnlockedByDefault());
        suspect.setDisplayOrder(request.getDisplayOrder() == null ? suspect.getDisplayOrder() : request.getDisplayOrder());
        adminEpisodeRepository.updateSuspect(suspect);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse createSuspect(Long episodeId, AdminSuspectUpdateRequest request) {
        requireEditableEpisode(episodeId);
        int nextOrder = adminEpisodeRepository.countSuspects(episodeId) + 1;
        CaseSuspect suspect = new CaseSuspect();
        suspect.setEpisodeId(episodeId);
        suspect.setAlias(text(request.getAlias(), "Review required." + nextOrder));
        suspect.setDisplayName(text(request.getDisplayName(), "임시 용의자"));
        suspect.setShortDescription(text(request.getShortDescription(), "Review required."));
        suspect.setPortraitImageUrl(text(request.getPortraitImageUrl(), null));
        suspect.setRelationToVictim(text(request.getRelationToVictim(), "Review required."));
        suspect.setSuspiciousPoint(text(request.getSuspiciousPoint(), "Review required."));
        suspect.setAlibiSummary(text(request.getAlibiSummary(), "Review required."));
        suspect.setUnlockedByDefault(request.getUnlockedByDefault() != null && request.getUnlockedByDefault());
        suspect.setDisplayOrder(request.getDisplayOrder() == null ? nextOrder : request.getDisplayOrder());
        adminEpisodeRepository.insertSuspect(suspect);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse deleteSuspect(Long episodeId, Long suspectId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findSuspects(episodeId).stream().anyMatch(suspect -> suspectId.equals(suspect.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUSPECT_NOT_FOUND", "Review required.");
        }
        adminEpisodeRepository.detachEvidencesBySuspectId(suspectId);
        adminEpisodeRepository.deleteSuspect(suspectId);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updateEvidence(Long episodeId, Long evidenceId, AdminEvidenceUpdateRequest request) {
        requireEpisode(episodeId);
        CaseEvidence evidence = adminEpisodeRepository.findEvidences(episodeId).stream()
                .filter(item -> evidenceId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Review required."));
        evidence.setTitle(text(request.getTitle(), evidence.getTitle()));
        evidence.setType(validateValue(text(request.getType(), evidence.getType()), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "Review required."));
        evidence.setImageUrl(text(request.getImageUrl(), evidence.getImageUrl()));
        evidence.setTextSummary(text(request.getTextSummary(), evidence.getTextSummary()));
        evidence.setSourceSpotId(validateOptionalSpot(episodeId, request.getSourceSpotId(), evidence.getSourceSpotId()));
        evidence.setRelatedSuspectId(validateOptionalSuspect(episodeId, request.getRelatedSuspectId(), evidence.getRelatedSuspectId()));
        evidence.setRelatedClueType(text(request.getRelatedClueType(), evidence.getRelatedClueType()));
        evidence.setUnlockedByDefault(request.getUnlockedByDefault() == null ? evidence.getUnlockedByDefault() : request.getUnlockedByDefault());
        evidence.setDisplayOrder(request.getDisplayOrder() == null ? evidence.getDisplayOrder() : request.getDisplayOrder());
        adminEpisodeRepository.updateEvidence(evidence);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse createEvidence(Long episodeId, AdminEvidenceUpdateRequest request) {
        requireEditableEpisode(episodeId);
        int nextOrder = adminEpisodeRepository.countEvidences(episodeId) + 1;
        CaseEvidence evidence = new CaseEvidence();
        evidence.setEpisodeId(episodeId);
        evidence.setTitle(text(request.getTitle(), "Review required."));
        evidence.setType(validateValue(text(request.getType(), "NOTE"), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "Review required."));
        evidence.setImageUrl(text(request.getImageUrl(), null));
        evidence.setTextSummary(text(request.getTextSummary(), "Review required."));
        evidence.setSourceSpotId(validateOptionalSpot(episodeId, request.getSourceSpotId(), null));
        evidence.setRelatedSuspectId(validateOptionalSuspect(episodeId, request.getRelatedSuspectId(), null));
        evidence.setRelatedClueType(text(request.getRelatedClueType(), evidence.getType()));
        evidence.setUnlockedByDefault(request.getUnlockedByDefault() != null && request.getUnlockedByDefault());
        evidence.setDisplayOrder(request.getDisplayOrder() == null ? nextOrder : request.getDisplayOrder());
        adminEpisodeRepository.insertEvidence(evidence);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse deleteEvidence(Long episodeId, Long evidenceId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findEvidences(episodeId).stream().anyMatch(evidence -> evidenceId.equals(evidence.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Review required.");
        }
        adminEpisodeRepository.deleteEvidence(evidenceId);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updatePartnerReward(Long episodeId, Long rewardId, AdminPartnerRewardUpdateRequest request) {
        requireEpisode(episodeId);
        EpisodePartnerReward reward = adminEpisodeRepository.findPartnerRewards(episodeId).stream()
                .filter(item -> rewardId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTNER_REWARD_NOT_FOUND", "Review required."));
        reward.setTitle(text(request.getTitle(), reward.getTitle()));
        reward.setDescription(text(request.getDescription(), reward.getDescription()));
        reward.setRewardType(validateValue(text(request.getRewardType(), reward.getRewardType()), PARTNER_REWARD_TYPES, "INVALID_REWARD_TYPE", "Review required."));
        reward.setPartnerName(text(request.getPartnerName(), reward.getPartnerName()));
        reward.setLocationName(text(request.getLocationName(), reward.getLocationName()));
        reward.setLatitude(request.getLatitude() == null ? reward.getLatitude() : request.getLatitude());
        reward.setLongitude(request.getLongitude() == null ? reward.getLongitude() : request.getLongitude());
        reward.setStatus(validateValue(text(request.getStatus(), reward.getStatus()), PARTNER_REWARD_STATUSES, "INVALID_REWARD_STATUS", "Review required."));
        adminEpisodeRepository.updatePartnerReward(reward);
        return getEpisode(episodeId);
    }

    private AdminRewardPayloadValidationResponse validateRewardPayload(Long episodeId, AdminRewardPayloadValidationRequestWrapper request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<AdminRewardPayloadValidationResponse.RewardItem> rewardItems = new ArrayList<>();
        String payload = request.rewardPayload();
        if (payload == null || payload.isBlank()) {
            warnings.add("reward_payload is empty; only reward_clue will be used.");
            return AdminRewardPayloadValidationResponse.builder().valid(true).errors(errors).warnings(warnings).rewards(rewardItems).build();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode rewards = root.path("rewards");
            if (!rewards.isArray()) {
                errors.add("reward_payload.rewards must be an array.");
            } else if (rewards.isEmpty()) {
                warnings.add("rewards array is empty.");
            } else {
                for (int i = 0; i < rewards.size(); i++) {
                    JsonNode reward = rewards.get(i);
                    String type = reward.path("type").asText("");
                    String value = reward.path("value").asText("");
                    Long targetId = reward.hasNonNull("targetId") ? reward.path("targetId").asLong() : null;
                    if (!REWARD_TYPES.contains(type)) errors.add("rewards[" + i + "].type is unsupported: " + type);
                    String targetLabel = null;
                    if (Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE").contains(type) && value.isBlank()) errors.add("rewards[" + i + "] " + type + " requires value.");
                    if ("MEMO_UNLOCK".equals(type) && targetId == null && value.isBlank()) errors.add("rewards[" + i + "] MEMO_UNLOCK requires targetId or value.");
                    if (Set.of("EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "MEMO_UNLOCK").contains(type) && targetId != null) targetLabel = validateEvidenceTarget(episodeId, targetId, i, errors);
                    if (Set.of("SUSPECT_UNLOCK", "SUSPECT_UPDATE").contains(type)) targetLabel = validateSuspectTarget(episodeId, targetId, i, errors);
                    rewardItems.add(AdminRewardPayloadValidationResponse.RewardItem.builder().type(type).value(value).targetId(targetId).targetLabel(targetLabel).build());
                }
            }
        } catch (Exception e) {
            errors.add("reward_payload must be valid JSON.");
        }
        return AdminRewardPayloadValidationResponse.builder().valid(errors.isEmpty()).errors(errors).warnings(warnings).rewards(rewardItems).build();
    }


    public AiEpisodeDraftResponse createAiDraft(AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request.getPlaces() == null ? List.of() : request.getPlaces();
        if (places.size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_PLACES", "At least 6 places are required for a case-file episode.");
        }
        List<String> warnings = new ArrayList<>();
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = places.get(i);
            String role = normalizeRole(place.getRole(), i, places.size());
            if (place.getVisibleElements() == null || place.getVisibleElements().isEmpty()) {
                warnings.add(blank(place.getName(), "spot " + (i + 1)) + ": visibleElements are missing; review observation puzzles.");
            }
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(blank(place.getName(), "Investigation spot " + (i + 1)))
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription(), i == 0 ? "Open the case file and establish the clue categories." : "Compare field material with the case memo."))
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendPuzzleType(place))
                    .questionText(buildQuestion(place))
                    .answer(buildAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(buildRewardClue(role, i))
                    .hints(List.of("Use only admin-provided field data.", "Classify whether this clue supports the answer or destination.", "Confirm the clue evidence on site before publishing."))
                    .groundRule("Rule-based admin draft. Verify every field claim before publishing.")
                    .build());
        }
        String finalObject = draftFinalObject(request, places);
        AiEpisodeDraftResponse.EpisodeDraft draft = AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("EP.NEW " + blank(request.getTheme(), "Hidden Record") + " Case")
                .subtitle(draftSubtitle(request, places))
                .genre(blank(request.getTheme(), "Outdoor case-file mystery"))
                .era(draftEra(request, places))
                .fictionSynopsis(draftFictionSynopsis(request, places))
                .finalAnswerType("EVIDENCE")
                .finalAnswer(finalObject)
                .finalAnswerAliases(List.of(finalObject.replace(" ", ""), draftFinalAlias(request, places)))
                .finalQuestion(draftFinalQuestion(request, places))
                .finalTruthSummary("Collected clues point to " + finalObject + ", a fictional evidence object inside the case.")
                .actualHistorySummary("Historical notes must be reviewed by an admin before publishing. Do not expose real people as culprits.")
                .deductionSecretFacts(List.of("The final answer is not a real place or real person.", "The answer requires combining at least four clue cards."))
                .deductionForbiddenReveals(List.of(finalObject, "actualFinalPlace", "realPersonAsCulprit"))
                .maxDeductionQuestions(20)
                .missions(missions)
                .suspects(defaultDraftSuspects())
                .evidences(defaultDraftEvidences(missions))
                .build();
        warnings.add("Rule-based draft generated. Review puzzle answers, reward_payload, and finalPlace before saving.");
        return AiEpisodeDraftResponse.builder()
                .generatorType("MVP_RULE_BASED_DRAFT")
                .message("Rule-based case-file draft created. It has not been saved to DB yet.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of("Review field observations", "Confirm final answer is not a place or real person", "Connect rewards to suspects and evidences", "Save as DRAFT after validation"))
                .build();
    }




    @Transactional
    private String draftSubtitle(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String area = blank(request.getArea(), "selected area");
        String anchor = places.stream()
                .filter(place -> "FINAL".equals(normalizeRole(place.getRole(), places.indexOf(place), places.size())))
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> places.isEmpty() ? "field site" : blank(places.get(places.size() - 1).getName(), "field site"));
        return area + " clues converge on " + anchor;
    }

    private String draftEra(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (!missing(request.getEra()) && !containsCompact(request.getEra(), "review") && !containsCompact(request.getEra(), "unknown")) return request.getEra().trim();
        String joined = routeText(request, places);
        if (containsCompact(joined, "1905") || containsCompact(joined, "1897") || containsCompact(joined, "empire")) return "Late Empire period";
        if (containsCompact(joined, "palace") || containsCompact(joined, "royal")) return "Royal archive case";
        if (containsCompact(joined, "independence") || containsCompact(joined, "colonial")) return "Independence-era mystery";
        return "Past and present crossover";
    }




    private String draftFictionSynopsis(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String area = blank(request.getArea(), "selected area");
        String first = places.isEmpty() ? "first spot" : blank(places.get(0).getName(), "first spot");
        String anchor = places.isEmpty() ? "last spot" : blank(places.get(places.size() - 1).getName(), "last spot");
        String object = draftFinalObject(request, places);
        String routeSignal = routeSignal(places);
        return "The case opens at " + first + " in " + area + ". Clues lead toward " + anchor + ", but the " + routeSignal + " records conflict. Players must compare field materials to identify " + object + ".";
    }




    private String draftFinalQuestion(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        return "What is the identity of " + draftFinalObject(request, places) + " indicated by the collected clues?";
    }




    private String draftFinalObject(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String joined = routeText(request, places);
        if (containsCompact(joined, "coffee") || containsCompact(joined, "cafe") || containsCompact(joined, "tea")) return "cold tea record";
        if (containsCompact(joined, "document") || containsCompact(joined, "seal") || containsCompact(joined, "signature")) return "red-sealed document";
        if (containsCompact(joined, "photo") || containsCompact(joined, "film") || containsCompact(joined, "lens")) return "sealed film envelope";
        if (containsCompact(joined, "market") || containsCompact(joined, "restaurant") || containsCompact(joined, "receipt")) return "torn receipt fragment";
        if (containsCompact(joined, "palace") || containsCompact(joined, "archive")) return "folded archive copy";
        return "sealed record fragment";
    }




    private String draftFinalAlias(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String object = draftFinalObject(request, places);
        String[] tokens = object.split("\\s+");
        return tokens.length == 0 ? object : tokens[tokens.length - 1];
    }

    private String routeSignal(List<AiEpisodeDraftRequest.PlaceInput> places) {
        return places.stream().flatMap(place -> place.getKeywords() == null ? java.util.stream.Stream.empty() : place.getKeywords().stream()).filter(value -> value != null && !value.isBlank()).findFirst().orElse("route");
    }





    private List<AiEpisodeDraftResponse.SuspectDraft> defaultDraftSuspects() {
        return List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("Suspect A").displayName("Witness with the red envelope").shortDescription("A witness who saw the last envelope near the field route.").relationToVictim("Last request contact").suspiciousPoint("Part of the route timeline is missing.").alibiSummary("Claims to have stayed near a cafe until the rain stopped.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("Suspect B").displayName("Missing record keeper").shortDescription("A helper who managed film and evidence files.").relationToVictim("Archive handler").suspiciousPoint("Knew where the missing film was stored.").alibiSummary("Claims to have stayed in the archive room, but no witness confirms it.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("Suspect C").displayName("Black envelope courier").shortDescription("A courier whose route overlaps with destination clues.").relationToVictim("Final clue carrier").suspiciousPoint("May have hidden the clue flow rather than stolen the answer.").alibiSummary("Delivery route and witness time do not match.").build()
        );
    }


    private List<AiEpisodeDraftResponse.EvidenceDraft> defaultDraftEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return missions.stream().limit(8).map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                .title(mission.getRewardClue() + " clue card")
                .type("ANSWER_HINT".equals(mission.getClueRole()) ? "ANSWER_CLUE" : "DESTINATION_HINT".equals(mission.getClueRole()) ? "DESTINATION_CLUE" : "NOTE")
                .imageUrl(generatedEvidenceImage("ANSWER_HINT".equals(mission.getClueRole()) ? "ANSWER_CLUE" : "NOTE"))
                .textSummary("Case material unlocked after solving this mission.")
                .sourceMissionOrder(mission.getOrder())
                .build()).toList();
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", "Review required.");
        }
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null ? List.of() : draft.getMissions();
        if (missions.size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_MISSIONS", "Review required.");
        }
        long finalCount = missions.stream().filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType())).count();
        if (finalCount < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINAL_PLACE_REQUIRED", "Review required.");
        }
        String title = resolveDraftTitle(draft, missions);

        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(draft.getSubtitle(), "AI draft case file"));
        episode.setEra(blank(draft.getEra(), "Review required"));
        episode.setGenre(draft.getGenre());
        episode.setDifficulty("NORMAL");
        episode.setEstimatedTime("90~120분");
        episode.setEstimatedDistance(estimateDraftDistance(missions));
        episode.setFictionSynopsis(draft.getFictionSynopsis());
        episode.setFinalAnswerType(blank(draft.getFinalAnswerType(), "EVIDENCE"));
        episode.setFinalAnswer(blank(draft.getFinalAnswer(), "검수필요"));
        episode.setFinalAnswerAliases(join(draft.getFinalAnswerAliases()));
        episode.setFinalQuestion(blank(draft.getFinalQuestion(), "Review required."));
        episode.setFinalTruthSummary(draft.getFinalTruthSummary());
        episode.setActualHistorySummary(draft.getActualHistorySummary());
        episode.setDeductionSecretFacts(joinLines(draft.getDeductionSecretFacts()));
        episode.setDeductionForbiddenReveals(joinLines(draft.getDeductionForbiddenReveals()));
        episode.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : draft.getMaxDeductionQuestions());
        episode.setRecommendedPlayers("2~4명");
        episode.setTeamRoleGuide("Review required.");
        episode.setNoticeText("Review required.");
        episode.setStatus(validateValue(text(request.getStatus(), "DRAFT"), EPISODE_STATUSES, "INVALID_EPISODE_STATUS", "Review required."));
        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DRAFT_REVIEW_REQUIRED", "Review required.");
        }
        adminEpisodeRepository.insertEpisode(episode);

        Map<Integer, MissionSpot> spotByOrder = new HashMap<>();
        Map<Integer, Puzzle> puzzleByOrder = new HashMap<>();
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            MissionSpot spot = new MissionSpot();
            spot.setEpisodeId(episode.getId());
            spot.setPlaceName(blank(mission.getPlaceName(), "Review required." + (i + 1)));
            spot.setAddress(mission.getAddress());
            spot.setLatitude(mission.getLatitude() == null ? 37.5665 + (i * 0.001) : mission.getLatitude());
            spot.setLongitude(mission.getLongitude() == null ? 126.9780 + (i * 0.001) : mission.getLongitude());
            boolean finalPlace = Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType());
            String markerType = finalPlace ? "FINAL" : validateValue(blank(mission.getMarkerType(), normalizeRole(null, i, missions.size())), MARKER_TYPES, "INVALID_MARKER_TYPE", "Unsupported markerType.");
            spot.setMarkerType(markerType);
            spot.setFinalPlace(finalPlace);
            spot.setClueRole(finalPlace ? "FINAL_PLACE" : validateValue(blank(mission.getClueRole(), toClueRole(markerType)), CLUE_ROLES, "INVALID_CLUE_ROLE", "Unsupported clueRole."));
            spot.setPublicMarkerType(publicMarkerType(mission.getPublicMarkerType(), finalPlace, markerType));
            spot.setStoryText(mission.getStoryText());
            spot.setArrivalRadius(mission.getArrivalRadius() == null ? 50.0 : Math.max(10.0, mission.getArrivalRadius()));
            adminEpisodeRepository.insertSpot(spot);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            spotByOrder.put(order, spot);
            if (isMissionAnswerDisconnected(mission, missions)) {
                normalizeMissionForReview(mission);
            }

            Puzzle puzzle = new Puzzle();
            puzzle.setMissionSpotId(spot.getId());
            puzzle.setPuzzleType(validateValue(blank(mission.getPuzzleType(), "OBSERVATION"), PUZZLE_TYPES, "INVALID_PUZZLE_TYPE", "Unsupported puzzleType."));
            puzzle.setQuestionText(blank(mission.getQuestionText(), "Review required."));
            puzzle.setAnswer(blank(mission.getAnswer(), "검수필요"));
            puzzle.setAnswerFormat(validateValue(blank(mission.getAnswerFormat(), "TEXT"), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "Unsupported answerFormat."));
            puzzle.setRewardClue(blank(mission.getRewardClue(), "검수필요"));
            puzzle.setRewardPayload(null);
            puzzle.setDifficulty("NORMAL");
            adminEpisodeRepository.insertPuzzle(puzzle);
            puzzleByOrder.put(order, puzzle);
            List<String> hints = mission.getHints() == null ? List.of() : mission.getHints();
            for (int hintIndex = 0; hintIndex < Math.min(3, hints.size()); hintIndex++) {
                adminEpisodeRepository.insertHint(puzzle.getId(), hintIndex + 1, sanitizeHintText(hints.get(hintIndex), mission));
            }
        }

        List<CaseSuspect> suspects = saveDraftSuspects(episode.getId(), draft.getSuspects());
        Map<Integer, CaseEvidence> evidenceByMissionOrder = saveDraftEvidences(episode.getId(), draft.getEvidences(), spotByOrder, suspects);
        applyDraftRewardPayloads(episode.getId(), missions, puzzleByOrder, evidenceByMissionOrder);
        saveDraftPartnerReward(episode.getId());
        return getEpisode(episode.getId());
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
            suspect.setAlias(blank(draft.getAlias(), "Suspect " + (char) ('A' + index)));
            suspect.setDisplayName(blank(draft.getDisplayName(), "Unnamed stakeholder"));
            suspect.setShortDescription(blank(draft.getShortDescription(), "A stakeholder who may have distorted the clue chain."));
            suspect.setRelationToVictim(blank(draft.getRelationToVictim(), "Case stakeholder"));
            suspect.setSuspiciousPoint(blank(draft.getSuspiciousPoint(), "There is an unexplained gap in the timeline."));
            suspect.setAlibiSummary(blank(draft.getAlibiSummary(), "The alibi requires comparison with evidence cards."));
            suspect.setPortraitImageUrl(draft.getPortraitImageUrl());
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
            evidence.setTitle(blank(draft.getTitle(), "Case material " + (index + 1)));
            evidence.setType(validateValue(blank(draft.getType(), "NOTE"), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "Unsupported evidence type."));
            evidence.setImageUrl(draft.getImageUrl());
            evidence.setTextSummary(blank(draft.getTextSummary(), "Case material that helps combine field clues for final deduction."));
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

    private Long resolveLinkedSuspectId(AiEpisodeDraftResponse.EvidenceDraft draft, List<CaseSuspect> suspects, int index) {
        if (suspects == null || suspects.isEmpty()) {
            return null;
        }
        return suspects.get(Math.floorMod(index, suspects.size())).getId();
    }




    private void applyDraftRewardPayloads(Long episodeId, List<AiEpisodeDraftResponse.MissionDraft> missions, Map<Integer, Puzzle> puzzleByOrder, Map<Integer, CaseEvidence> evidenceByMissionOrder) {
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
            CaseEvidence evidence = evidenceByMissionOrder.get(order);
            StringBuilder payload = new StringBuilder();
            payload.append("{\"rewards\":[");
            payload.append("{\"type\":\"").append(clueType).append("\",\"value\":\"").append(jsonEscape(blank(mission.getRewardClue(), "검수필요"))).append("\"}");
            if (evidence != null) {
                payload.append(",{\"type\":\"EVIDENCE_UNLOCK\",\"targetId\":").append(evidence.getId()).append("}");
            }
            payload.append("]}");
            puzzle.setRewardPayload(payload.toString());
            AdminRewardPayloadValidationResponse validation = validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));
            if (!validation.isValid()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFT_REWARD_PAYLOAD", String.join(" / ", validation.getErrors()));
            }
            adminEpisodeRepository.updatePuzzle(puzzle);
        }
    }

    private void saveDraftPartnerReward(Long episodeId) {
        EpisodePartnerReward reward = new EpisodePartnerReward();
        reward.setEpisodeId(episodeId);
        reward.setTitle("지역 리워드 준비 중");
        reward.setDescription("Review required.");
        reward.setRewardType("STAMP");
        reward.setPartnerName("Operation Korea");
        reward.setLocationName("검수 예정 지점");
        reward.setStatus("PLANNED");
        adminEpisodeRepository.insertPartnerReward(reward);
    }

    private String validateEvidenceTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] targetId is required.");
            return null;
        }
        return adminEpisodeRepository.findEvidences(episodeId).stream()
                .filter(evidence -> targetId.equals(evidence.getId()))
                .findFirst()
                .map(CaseEvidence::getTitle)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + " evidence card not found.");
                    return null;
                });
    }


    private String validateSuspectTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] targetId is required.");
            return null;
        }
        return adminEpisodeRepository.findSuspects(episodeId).stream()
                .filter(suspect -> targetId.equals(suspect.getId()))
                .findFirst()
                .map(CaseSuspect::getDisplayName)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + " suspect card not found.");
                    return null;
                });
    }


    private Long validateOptionalSpot(Long episodeId, Long requestedId, Long fallbackId) {
        Long id = requestedId == null ? fallbackId : requestedId;
        if (id == null) {
            return null;
        }
        boolean exists = adminEpisodeRepository.findSpots(episodeId).stream().anyMatch(spot -> id.equals(spot.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_SPOT", "Review required.");
        }
        return id;
    }

    private Long validateOptionalSuspect(Long episodeId, Long requestedId, Long fallbackId) {
        Long id = requestedId == null ? fallbackId : requestedId;
        if (id == null) {
            return null;
        }
        boolean exists = adminEpisodeRepository.findSuspects(episodeId).stream().anyMatch(suspect -> id.equals(suspect.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RELATED_SUSPECT", "Review required.");
        }
        return id;
    }

    private void validatePublishReadiness(Episode episode) {
        List<String> errors = new ArrayList<>();
        if (missing(episode.getFictionSynopsis())) errors.add("Fiction synopsis is required.");
        if (missing(episode.getFinalAnswerType())) errors.add("Final answer type is required.");
        if (missing(episode.getFinalAnswer())) errors.add("Final answer is required.");
        if (missing(episode.getFinalQuestion())) errors.add("Final question is required.");
        if (missing(episode.getFinalTruthSummary())) errors.add("Private truth summary is required.");
        if (missing(episode.getDeductionSecretFacts())) errors.add("Deduction secret facts are required.");
        if (missing(episode.getDeductionForbiddenReveals())) errors.add("Forbidden reveal terms are required.");
        else if (!containsCompact(episode.getDeductionForbiddenReveals(), episode.getFinalAnswer())) errors.add("Forbidden reveals must include the final answer.");
        if (episode.getMaxDeductionQuestions() == null || episode.getMaxDeductionQuestions() < 1) errors.add("Max deduction questions must be at least 1.");
        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episode.getId());
        if (spots.size() < 7 || spots.size() > 9) errors.add("Published episodes require 7 to 9 spots.");
        long startCount = spots.stream().filter(spot -> "START".equals(spot.getMarkerType())).count();
        long answerHintCount = spots.stream().filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType())).count();
        long destinationHintCount = spots.stream().filter(spot -> "DESTINATION_HINT".equals(spot.getMarkerType())).count();
        long finalPlaceCount = spots.stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count();
        if (startCount != 1) errors.add("Exactly one START spot is required.");
        if (answerHintCount < 4) errors.add("At least four ANSWER_HINT spots are required.");
        if (destinationHintCount < 3) errors.add("At least three DESTINATION_HINT spots are required.");
        if (finalPlaceCount != 1) errors.add("Exactly one internal final place is required.");
        for (MissionSpot spot : spots) {
            if (missing(spot.getPlaceName()) || spot.getLatitude() == null || spot.getLongitude() == null) errors.add("Every spot needs a name and coordinates.");
            if (spot.getArrivalRadius() == null || spot.getArrivalRadius() < 10) errors.add("Arrival radius must be at least 10m: " + spot.getPlaceName());
            if (same(episode.getFinalAnswer(), spot.getPlaceName())) errors.add("Final answer must not equal a real place name: " + spot.getPlaceName());
            Puzzle puzzle = adminEpisodeRepository.findPuzzleBySpotId(spot.getId());
            if (puzzle == null) { errors.add("Puzzle is missing: " + spot.getPlaceName()); continue; }
            if (missing(puzzle.getQuestionText())) errors.add("Puzzle question is missing: " + spot.getPlaceName());
            if (missing(puzzle.getAnswer())) errors.add("Puzzle answer is missing: " + spot.getPlaceName());
            if (missing(puzzle.getRewardClue())) errors.add("Reward clue is missing: " + spot.getPlaceName());
            if (same(puzzle.getAnswer(), puzzle.getRewardClue())) errors.add("Puzzle answer and reward clue must differ: " + spot.getPlaceName());
            if (adminEpisodeRepository.findHints(puzzle.getId()).size() < 3) errors.add("Three puzzle hints are required: " + spot.getPlaceName());
            if (containsCompact(puzzle.getQuestionText(), episode.getFinalAnswer())) errors.add("Puzzle question exposes final answer: " + spot.getPlaceName());
        }
        if (adminEpisodeRepository.findSuspects(episode.getId()).size() < 3) errors.add("At least three suspect cards are required.");
        if (adminEpisodeRepository.findEvidences(episode.getId()).size() < Math.max(1, spots.size() - 1)) errors.add("Evidence cards should cover the route.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_PUBLISH_NOT_READY", "Cannot publish: " + String.join(" / ", errors));
    }




    @Transactional
    public AdminEpisodeDetailResponse createEpisode(AdminEpisodeUpdateRequest request) {
        AdminEpisodeUpdateRequest safeRequest = request == null ? new AdminEpisodeUpdateRequest() : request;
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String title = blank(safeRequest.getTitle(), "New case-file draft " + uniqueSuffix);
        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(safeRequest.getSubtitle(), "Admin draft"));
        episode.setEra(blank(safeRequest.getEra(), "Review required"));
        episode.setGenre(blank(safeRequest.getGenre(), "Outdoor case-file mystery"));
        episode.setDifficulty(blank(safeRequest.getDifficulty(), "NORMAL"));
        episode.setEstimatedTime(blank(safeRequest.getEstimatedTime(), "90~120 min"));
        episode.setEstimatedDistance(blank(safeRequest.getEstimatedDistance(), "Review required"));
        episode.setFictionSynopsis(blank(safeRequest.getFictionSynopsis(), "Admin draft. Write the case synopsis before publishing."));
        episode.setFinalAnswerType(blank(safeRequest.getFinalAnswerType(), "EVIDENCE"));
        episode.setFinalAnswer(blank(safeRequest.getFinalAnswer(), "review-required"));
        episode.setFinalAnswerAliases(safeRequest.getFinalAnswerAliases());
        episode.setFinalQuestion(blank(safeRequest.getFinalQuestion(), "Enter the final question."));
        episode.setFinalTruthSummary(blank(safeRequest.getFinalTruthSummary(), "Enter the private truth summary."));
        episode.setActualHistorySummary(safeRequest.getActualHistorySummary());
        episode.setDeductionSecretFacts(blank(safeRequest.getDeductionSecretFacts(), "Enter internal facts for deduction."));
        episode.setDeductionForbiddenReveals(blank(safeRequest.getDeductionForbiddenReveals(), "Do not reveal the final answer or actual final place."));
        episode.setMaxDeductionQuestions(safeRequest.getMaxDeductionQuestions() == null ? 20 : safeRequest.getMaxDeductionQuestions());
        episode.setRecommendedPlayers(blank(safeRequest.getRecommendedPlayers(), "2~4 players"));
        episode.setTeamRoleGuide(blank(safeRequest.getTeamRoleGuide(), "Split roles into map, case-file, puzzle, and recorder."));
        episode.setNoticeText(blank(safeRequest.getNoticeText(), "Complete field review before publishing."));
        episode.setStatus(validateValue(text(safeRequest.getStatus(), "DRAFT"), EPISODE_STATUSES, "INVALID_EPISODE_STATUS", "Status must be DRAFT, PUBLISHED, or ARCHIVED."));
        if ("PUBLISHED".equals(episode.getStatus())) validatePublishReadiness(episode);
        adminEpisodeRepository.insertEpisode(episode);
        return getEpisode(episode.getId());
    }




    @Transactional
    public void deleteEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);
        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHED_EPISODE_DELETE_BLOCKED", "Review required.");
        }
        adminEpisodeRepository.deleteEpisode(episodeId);
    }

    private Episode requireEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);
        if (episode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "Review required.");
        }
        return episode;
    }

    private void requireEditableEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);
        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHED_EPISODE_LOCKED", "Review required.");
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
        if (containsCompact(value, "KakaoLocal:CT1") || containsCompact(value, "KakaoLocal:AT4")) score += 30;
        if (containsCompact(value, "KakaoLocal:CE7") || containsCompact(value, "KakaoLocal:FD6")) score += 20;
        double distance = distanceMeters(anchor.getLatitude(), anchor.getLongitude(), candidate.getLatitude(), candidate.getLongitude());
        if (Double.isFinite(distance)) {
            if (distance >= 80 && distance <= 700) score += 25;
            score -= Math.min(25, distance / 120.0);
        }
        return score;
    }




    private String enrichedDescription(AiEpisodeDraftRequest.PlaceInput source, List<AdminPlaceCandidateResponse> rankedNearby) {
        String base = blank(source.getDescription(), "Selected operation spot.");
        String topSignals = rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(3)
                .map(AdminPlaceCandidateResponse::getTitle)
                .collect(Collectors.joining(", "));
        if (topSignals.isBlank()) {
            return base;
        }
        return base + " Nearby verification focus: " + topSignals + ".";
    }

    private List<String> focusedKeywords(AiEpisodeDraftRequest.PlaceInput place, List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> values = new ArrayList<>();
        rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(5)
                .forEach(candidate -> {
                    values.add(categoryKeyword(candidate));
                });
        values.add("\ud604\uc7a5\ub2e8\uc11c");
        values.add("\ub3d9\uc120\ud754\uc801");
        return values;
    }


    private List<String> inferredVisibleElements(List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> values = new ArrayList<>();
        values.add("place name sign to verify on site");
        values.add("address and entrance area to verify on site");
        rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(4)
                .map(this::categoryVisibleElement)
                .forEach(values::add);
        values.add("nearby route context to verify on site");
        return values;
    }

    private String enrichedAdminMemo(AiEpisodeDraftRequest.PlaceInput place, List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> memo = new ArrayList<>();
        if (!missing(place.getAdminMemo())) {
            memo.add(place.getAdminMemo());
        }
        memo.add("RAG/site enrichment narrowed the admin verification scope using nearby Kakao Local signals.");
        memo.add("Use these signals only as candidate verification targets. Do not treat signs, numbers, sculptures, or opening hours as confirmed until on-site inspection.");
        if (place.getLatitude() == null || place.getLongitude() == null) {
            memo.add("No coordinates: external search could not run. Add latitude/longitude before publishing.");
            return String.join("\n", memo);
        }
        if (rankedNearby.isEmpty()) {
            memo.add("No nearby signals found within 900m. Admin should add manual field notes.");
            return String.join("\n", memo);
        }
        List<AdminPlaceCandidateResponse> usable = rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(5)
                .toList();
        if (usable.isEmpty()) {
            rankedNearby.stream().findFirst().ifPresent(candidate -> memo.add(candidate.getTitle() + " - " + blank(candidate.getDescription(), "external search failed")));
            return String.join("\n", memo);
        }
        memo.add("Top verification targets:");
        for (int i = 0; i < usable.size(); i++) {
            AdminPlaceCandidateResponse candidate = usable.get(i);
            memo.add((i + 1) + ". " + candidate.getTitle()
                    + " / " + categoryKeyword(candidate)
                    + " / approx " + Math.round(distanceMeters(place.getLatitude(), place.getLongitude(), candidate.getLatitude(), candidate.getLongitude())) + "m"
                    + " / verify: " + categoryVisibleElement(candidate));
        }
        memo.add("Recommended puzzle basis: use only admin-confirmed visibleElements/numbers after field check. Until then, AI may create story clues and verification placeholders, not factual observation claims.");
        return String.join("\n", memo);
    }

    private String categoryKeyword(AdminPlaceCandidateResponse candidate) {
        String source = blank(candidate.getSource(), "");
        String value = String.join(" ", blank(candidate.getTitle(), ""), blank(candidate.getSource(), ""), blank(candidate.getDescription(), ""), blank(candidate.getAddress(), ""));
        if (source.contains("CT1") || containsCompact(value, "culture") || containsCompact(value, "museum") || containsCompact(value, "gallery") || containsCompact(value, "exhibition")) return "\ubb38\ud654\uc804\uc2dc";
        if (source.contains("CE7") || containsCompact(value, "cafe") || containsCompact(value, "coffee")) return "\uce74\ud398\uc270\ud130";
        if (containsCompact(value, "park") || containsCompact(value, "square") || containsCompact(value, "street")) return "\uacf5\uac1c\uad11\uc7a5";
        return "\ud604\uc7a5\ub2e8\uc11c";
    }




    private String categoryVisibleElement(AdminPlaceCandidateResponse candidate) {
        String keyword = categoryKeyword(candidate);
        return switch (keyword) {
            case "culture-exhibition" -> "exhibition sign or building marker";
            case "cafe-rest-point" -> "menu board or entrance marker";
            case "open-public-space" -> "public sign stone or route guide";
            default -> "field marker or nearby structure";
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
        return validateValue(value, PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType must not expose FINAL.");
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
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return "Check which provided field number connects to the case record.";
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) return "Check the field element '" + place.getVisibleElements().get(0) + "' and compare it with the case memo.";
        return "Check the admin memo and enter the field clue keyword.";
    }




    private boolean isMissionAnswerDisconnected(AiEpisodeDraftResponse.MissionDraft mission, List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (mission == null || missing(mission.getAnswer())) {
            return false;
        }
        String answer = compact(mission.getAnswer());
        if (answer.isBlank() || answer.contains("\uac80\uc218\ud544\uc694") || "review-required".equals(answer)) {
            return false;
        }
        if ("NUMBER".equals(normalizeType(mission.getAnswerFormat()))) {
            return false;
        }
        if (isGenericPuzzleAnswer(answer) || isPlaceNameAnswer(answer, mission.getPlaceName())) {
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
        return Set.of(
                "placedescription", "adminmemo", "casememo", "selectedoperationspot",
                "selected", "operation", "spot", "nearby", "verification", "focus",
                "place", "address", "entrance", "area", "siteverificationfocus", "nearbyfamousplacesignal"
        ).contains(compactAnswer);
    }

    private void normalizeMissionForReview(AiEpisodeDraftResponse.MissionDraft mission) {
        mission.setPuzzleType("STORY_COMBINATION");
        mission.setQuestionText("\ubb38\uc81c\uc640 \uc815\ub2f5\uc758 \uadfc\uac70\uac00 \uc5f0\uacb0\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4. \uad00\ub9ac\uc790 \ud654\uba74\uc5d0\uc11c \ubb38\uc81c, \ud78c\ud2b8, \uc815\ub2f5\uc744 \ub2e4\uc2dc \ub9de\ucdb0 \uc8fc\uc138\uc694.");
        mission.setAnswer("\uac80\uc218\ud544\uc694");
        mission.setAnswerFormat("TEXT");
        mission.setRewardClue("\uac80\uc218\ud544\uc694");
        mission.setHints(List.of(
                "\ubb38\uc81c \ubb38\uc7a5 \uc548\uc5d0 \uc815\ub2f5\uc73c\ub85c \uc774\uc5b4\uc9c0\ub294 \ud604\uc7a5 \uadfc\uac70\ub97c \uba85\ud655\ud788 \ub123\uc5b4 \uc8fc\uc138\uc694.",
                "\uc608: \uc77c\uae30\uc7a5 \ub2e8\uc11c\ub77c\uba74 \uc77c\uae30\uc7a5 \uc548\uc758 \ubc29\ud5a5, \uc0c9, \ubb38\uad6c \uc911 \uc5b4\ub5a4 \uc694\uc18c\uac00 \uc815\ub2f5 \uadfc\uac70\uc778\uc9c0 \uc801\uc5b4 \uc8fc\uc138\uc694.",
                "\ud50c\ub808\uc774\uc5b4 \ub3d9\uc120\uacfc \ubb34\uad00\ud558\uac8c \uc774 \uc7a5\uc18c\uc5d0\uc11c \uc5bb\uc740 \ub2e8\uc11c\ub9cc\uc73c\ub85c \ud480 \uc218 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."
        ));
    }

    private String sanitizeHintText(String hint, AiEpisodeDraftResponse.MissionDraft mission) {
        String text = hint == null ? "" : hint.trim();
        String compactText = compact(text);
        if (text.isBlank() || isEnglishOnlyHint(text) || compactText.contains("\uac00\uc7a5\ucd5c\uadfc") || compactText.contains("\ucd5c\uadfc\ubcf4\uc0c1")
                || compactText.contains("\uc774\uc804\uc99d\uac70") || compactText.contains("\uc774\uc804\uc0ac\uac74\uc790\ub8cc")) {
            String basis = firstGroundingText(mission);
            return "\ubb38\uc81c\uc5d0 \uc81c\uc2dc\ub41c [" + basis + "] \ub2e8\uc11c\ub97c \uae30\uc900\uc73c\ub85c \ub2f5\uc744 \uc881\ud788\uc138\uc694.";
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
            return "\ud604\uc7a5 \uadfc\uac70";
        }
        if (!missing(mission.getAnswer()) && !compact(mission.getAnswer()).contains("\uac80\uc218\ud544\uc694")) {
            return mission.getAnswer().trim();
        }
        if (!missing(mission.getRewardClue()) && !compact(mission.getRewardClue()).contains("\uac80\uc218\ud544\uc694")) {
            return mission.getRewardClue().trim();
        }
        return "\ud604\uc7a5 \uadfc\uac70";
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
        return "\ud604\uc7a5\ub2e8\uc11c";
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
            case "ANSWER_HINT" -> List.of("seal", "photo", "document", "shadow").get(Math.min(Math.max(index - 1, 0), 3));
            case "DESTINATION_HINT", "FINAL" -> index % 2 == 0 ? "red wall" : "last door";
            case "START" -> "case start";
            default -> "case clue";
        };
    }




    private String estimateDraftDistance(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (missions == null || missions.size() < 2) return "walking route review required";
        double meters = 0;
        for (int i = 1; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft prev = missions.get(i - 1);
            AiEpisodeDraftResponse.MissionDraft current = missions.get(i);
            double segment = distanceMeters(prev.getLatitude(), prev.getLongitude(), current.getLatitude(), current.getLongitude());
            if (Double.isFinite(segment)) meters += segment;
        }
        if (meters <= 0) return "walking route review required";
        double km = Math.round((meters / 1000.0) * 10.0) / 10.0;
        return "about " + km + "km";
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
        return uniqueDraftTitle("EP.NEW " + anchor + " Case");
    }

    private boolean isGenericDraftTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.contains("ep.new") || normalized.contains("draft") || normalized.contains("episode");
    }

    private String uniqueDraftTitle(String title) {
        String base = blank(title, "EP.NEW Operation KOREA Case");
        boolean duplicate = adminEpisodeRepository.findAllEpisodes().stream().anyMatch(episode -> base.equals(episode.getTitle()));
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
                "Confirm coordinates and arrival radius with mobile GPS.",
                "Confirm every number, sign, marker, and object used by puzzles on site.",
                "Confirm the final place is not exposed by publicMarkerType.",
                "Confirm the final answer is not a real place, real person, or real event.",
                "Confirm hidden history notes are not overexposed during gameplay.",
                "Keep reward coupons PLANNED or DISABLED unless they are real benefits."
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
}
