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
    private static final Set<String> REWARD_TYPES = Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE", "SUSPECT_CLUE", "MEMO_UNLOCK", "EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "SUSPECT_UNLOCK", "SUSPECT_UPDATE");
    private static final Set<String> EVIDENCE_TYPES = Set.of("PHOTO", "MEMO", "NOTE", "DOCUMENT", "EVIDENCE", "SUSPECT_CLUE", "POST_IT", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE");
    private static final Set<String> PARTNER_REWARD_TYPES = Set.of("COUPON", "GIFT_CARD", "LOCAL_CURRENCY", "CAFE_DISCOUNT", "STAMP");
    private static final Set<String> PARTNER_REWARD_STATUSES = Set.of("DISABLED", "PLANNED", "ACTIVE", "ENDED");
    private static final Set<String> INTERNAL_CONTENT_MARKERS = Set.of(
            "관리자", "검수", "확인 필요", "현장 확인", "자료 부족", "보강 필요", "공식 설명 없음",
            "추정", "review required", "admin review", "field required", "verification",
            "adminmemo", "siteverificationfocus", "kakao local", "tourapi"
    );
    private static final List<String> STORY_TOKEN_FALLBACKS = List.of(
            "봉투", "수첩", "문", "기록", "조각", "기둥", "발자국", "사진", "열쇠", "표식", "메모", "시간표"
    );
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
                    .message("Ready to publish. AI/site-data verification gates passed.")
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
                    .message("Fix blocking issues before publishing.")
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
        spot.setFieldVerified(request.getFieldVerified() == null ? spot.getFieldVerified() : request.getFieldVerified());
        spot.setFieldVerificationNote(text(request.getFieldVerificationNote(), spot.getFieldVerificationNote()));
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
        spot.setFieldVerified(Boolean.TRUE.equals(request.getFieldVerified()));
        spot.setFieldVerificationNote(text(request.getFieldVerificationNote(), null));
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
        suspect.setImagePrompt(ensureKoreanPersonPrompt(text(request.getImagePrompt(), suspect.getImagePrompt())));
        suspect.setRelationToVictim(text(request.getRelationToVictim(), suspect.getRelationToVictim()));
        suspect.setSuspiciousPoint(text(request.getSuspiciousPoint(), suspect.getSuspiciousPoint()));
        suspect.setAlibiSummary(text(request.getAlibiSummary(), suspect.getAlibiSummary()));
        suspect.setUnlockedByDefault(true);
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
        suspect.setImagePrompt(ensureKoreanPersonPrompt(text(request.getImagePrompt(), null)));
        suspect.setRelationToVictim(text(request.getRelationToVictim(), "Review required."));
        suspect.setSuspiciousPoint(text(request.getSuspiciousPoint(), "Review required."));
        suspect.setAlibiSummary(text(request.getAlibiSummary(), "Review required."));
        suspect.setUnlockedByDefault(true);
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
        evidence.setImagePrompt(ensureKoreanEvidencePrompt(text(request.getImagePrompt(), evidence.getImagePrompt())));
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
        evidence.setImagePrompt(ensureKoreanEvidencePrompt(text(request.getImagePrompt(), null)));
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
                    if (Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE", "SUSPECT_CLUE").contains(type) && value.isBlank()) errors.add("rewards[" + i + "] " + type + " requires value.");
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
                    .placeName(blank(place.getName(), "\uC870\uC0AC \uC9C0\uC810 " + (i + 1)))
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription(), i == 0 ? "\uC0AC\uAC74\uD30C\uC77C\uC744 \uC5F4\uACE0 \uB2E8\uC11C \uBD84\uB958\uB97C \uD655\uC778\uD558\uC138\uC694." : "\uD604\uC7A5 \uC790\uB8CC\uC640 \uC0AC\uAC74 \uBA54\uBAA8\uB97C \uBE44\uAD50\uD558\uC138\uC694."))
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendPuzzleType(place))
                    .questionText(buildQuestion(place))
                    .answer(buildAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(buildRewardClue(role, i))
                    .hints(List.of("\uAD00\uB9AC\uC790\uAC00 \uC81C\uACF5\uD55C \uD604\uC7A5 \uB370\uC774\uD130\uB9CC \uAE30\uC900\uC73C\uB85C \uBCF4\uC138\uC694.", "\uC774 \uB2E8\uC11C\uAC00 \uC815\uB2F5 \uD78C\uD2B8\uC778\uC9C0 \uBAA9\uC801\uC9C0 \uD78C\uD2B8\uC778\uC9C0 \uBD84\uB958\uD558\uC138\uC694.", "\uACF5\uAC1C \uC804 \uD604\uC7A5\uC5D0\uC11C \uB2E8\uC11C \uADFC\uAC70\uB97C \uD655\uC778\uD558\uC138\uC694."))
                    .groundRule("\uADDC\uCE59 \uAE30\uBC18 \uAD00\uB9AC\uC790 \uCD08\uC548\uC785\uB2C8\uB2E4. \uACF5\uAC1C \uC804 \uD604\uC7A5 \uC8FC\uC7A5\uC744 \uD655\uC778\uD558\uC138\uC694.")
                    .build());
        }
        DraftObjective objective = draftObjective(request, places);
        AiEpisodeDraftResponse.EpisodeDraft draft = AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("EP.NEW " + blank(request.getTheme(), "\uC228\uACA8\uC9C4 \uAE30\uB85D") + " \uC0AC\uAC74")
                .subtitle(draftSubtitle(request, places))
                .genre(blank(request.getTheme(), "\uC57C\uC678 \uC0AC\uAC74\uD30C\uC77C \uBBF8\uC2A4\uD130\uB9AC"))
                .era(draftEra(request, places))
                .fictionSynopsis(objective.synopsis())
                .selectedGenre(objective.genre())
                .finalAnswerKeywords(objective.keywords())
                .finalAnswerType(objective.answerType())
                .finalAnswer(objective.finalAnswer())
                .finalAnswerAliases(withKeywordContract(objective.aliases(), objective.keywords()))
                .finalQuestion(objective.finalQuestion())
                .finalTruthSummary(String.join("\n",
                        "3. 픽션과 역사의 매칭 (디브리핑)",
                        "스토리 속 [" + objective.finalAnswer() + "] -> 실제 역사 속 [장소에 남은 기록과 기억]: 시놉시스가 요구한 해결 조건 전체를 하나의 최종 진실로 묶은 장치입니다.",
                        "스토리 속 [현장 지령] -> 실제 역사 속 [최종 목적지의 역사적 맥락]: 장소에 남은 사건의 흔적을 동선과 퍼즐로 바꾼 장치입니다.",
                        "스토리 속 [암호 카드] -> 실제 역사 속 [기록과 증언]: 플레이어가 단서를 대조하도록 실제 자료 해석 과정을 은유했습니다.",
                        "스토리 속 [조력자/용의자 진술] -> 실제 역사 속 [관련 인물과 이해관계]: 실존 인물을 범인으로 만들지 않고 역할과 갈등만 차용했습니다."
                ))
                .actualHistorySummary("""
                        1. 모티브 공개
                        이 임무는 실제 장소에 남은 역사적 기억과 주변 동선의 분위기를 모티브로 제작되었습니다.

                        2. 실제 사건 해설
                        이 초안은 규칙 기반 안전 fallback입니다. 플레이어는 장소의 기록, 관찰 요소, 이동 흔적을 조합해 사건의 배경과 의미를 해석하게 됩니다.
                        """.trim())
                .deductionSecretFacts(List.of(
                        "최종 정답은 시놉시스가 요구한 해결 조건을 모두 포함해야 한다.",
                        "일부 단서 물건이나 문서 위치만 맞히는 답은 최종 정답이 아니다.",
                        "정답은 실제 장소명이나 실존 인물명이 아니라 픽션 사건 안의 완결된 진실이다."))
                .deductionForbiddenReveals(List.of(objective.finalAnswer(), "actualFinalPlace", "realPersonAsCulprit"))
                .maxDeductionQuestions(20)
                .missions(missions)
                .suspects(defaultDraftSuspects())
                .evidences(defaultDraftEvidences(missions))
                .build();
        warnings.add("\uADDC\uCE59 \uAE30\uBC18 \uCD08\uC548\uC744 \uC0DD\uC131\uD588\uC2B5\uB2C8\uB2E4. \uC800\uC7A5 \uC804 \uD37C\uC990 \uC815\uB2F5, reward_payload, finalPlace\uB97C \uD655\uC778\uD558\uC138\uC694.");
        return AiEpisodeDraftResponse.builder()
                .generatorType("MVP_RULE_BASED_DRAFT")
                .message("\uADDC\uCE59 \uAE30\uBC18 \uC0AC\uAC74\uD30C\uC77C \uCD08\uC548\uC744 \uC0DD\uC131\uD588\uC2B5\uB2C8\uB2E4. \uC544\uC9C1 DB\uC5D0 \uC800\uC7A5\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of("\uD604\uC7A5 \uAD00\uCC30 \uADFC\uAC70 \uD655\uC778", "\uCD5C\uC885 \uC815\uB2F5\uC774 \uC7A5\uC18C\uB098 \uC2E4\uC874 \uC778\uBB3C\uC774 \uC544\uB2CC\uC9C0 \uD655\uC778", "\uBCF4\uC0C1\uC744 \uC6A9\uC758\uC790/\uC99D\uAC70\uC640 \uC5F0\uACB0", "\uAC80\uC99D \uD6C4 DRAFT\uB85C \uC800\uC7A5"))
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
                .orElseGet(() -> places.isEmpty() ? "\uD604\uC7A5 \uC9C0\uC810" : blank(places.get(places.size() - 1).getName(), "\uD604\uC7A5 \uC9C0\uC810"));
        return area + "\uC758 \uB2E8\uC11C\uAC00 " + anchor + "\uB85C \uC218\uB834\uB429\uB2C8\uB2E4";
    }

    private String draftEra(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (!missing(request.getEra()) && !containsCompact(request.getEra(), "review") && !containsCompact(request.getEra(), "unknown")) return request.getEra().trim();
        String joined = routeText(request, places);
        if (containsCompact(joined, "1905") || containsCompact(joined, "1897") || containsCompact(joined, "empire")) return "\uB300\uD55C\uC81C\uAD6D \uD6C4\uAE30";
        if (containsCompact(joined, "palace") || containsCompact(joined, "royal")) return "\uC655\uC2E4 \uAE30\uB85D\uACE0 \uC0AC\uAC74";
        if (containsCompact(joined, "independence") || containsCompact(joined, "colonial")) return "\uB3C5\uB9BD\uC6B4\uB3D9\uAE30 \uBBF8\uC2A4\uD130\uB9AC";
        return "\uACFC\uAC70\uC640 \uD604\uC7AC\uAC00 \uACB9\uCE58\uB294 \uC2DC\uB300";
    }




    private DraftObjective draftObjective(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (request.getFinalAnswerKeywords() != null && !request.getFinalAnswerKeywords().isEmpty()) {
            List<String> keywords = request.getFinalAnswerKeywords().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            String genre = blank(request.getSelectedGenreName(), "역사 미스터리");
            String finalAnswer = genre + "의 최종 진실은 " + String.join(", ", keywords) + "입니다";
            return new DraftObjective(
                    genre,
                    keywords.size() > 1 ? "HIDDEN_TRUTH" : "EVIDENCE",
                    finalAnswer,
                    keywords,
                    List.of(finalAnswer.replace(" ", "")),
                    genre + "의 최종 진실을 이루는 핵심 요소들을 종합하면 어떤 결론인가?",
                    "선택한 장소의 역사·문화 단서는 " + genre + " 구조로 재배열됩니다. 플레이어는 사건파일과 현장 단서를 대조해 가려진 핵심 요소들을 모두 추론해야 합니다."
            );
        }
        String joined = routeText(request, places);
        String area = blank(request.getArea(), "selected area");
        String first = places.isEmpty() ? "\uCCAB \uC870\uC0AC \uC9C0\uC810" : blank(places.get(0).getName(), "\uCCAB \uC870\uC0AC \uC9C0\uC810");
        String anchor = places.isEmpty() ? "\uB9C8\uC9C0\uB9C9 \uC870\uC0AC \uC9C0\uC810" : blank(places.get(places.size() - 1).getName(), "\uB9C8\uC9C0\uB9C9 \uC870\uC0AC \uC9C0\uC810");
        String routeSignal = routeSignal(places);
        if (requiresIdentityAndHideout(joined)) {
            String identity = containsCompact(joined, "royal") || containsCompact(joined, "황실") || containsCompact(joined, "대한제국")
                    ? "광영회의 위장 연락책"
                    : "검은 그림자의 내부 전달자";
            String hideout = containsCompact(joined, "archive") || containsCompact(joined, "기록") || containsCompact(joined, "문서")
                    ? "봉인된 기록고"
                    : "닫힌 골목 은신처";
            String finalAnswer = "검은 그림자는 " + identity + "이며 은신처는 " + hideout + "이다";
            return new DraftObjective(
                    "역사 음모 추적",
                    "HIDDEN_TRUTH",
                    finalAnswer,
                    List.of(identity, hideout),
                    List.of(finalAnswer.replace(" ", ""), identity + "와 " + hideout),
                    "검은 그림자의 정체와 그들이 숨어든 은신처는 무엇인가?",
                    area + "의 " + first + "에서 도난 기록이 발견됩니다. 설계도와 장부는 단서일 뿐이며, " + routeSignal + " 표식은 " + anchor + "로 이어집니다. 플레이어는 사건자료를 대조해 검은 그림자의 정체를 밝히고 그들이 숨어든 은신처를 찾아야 합니다."
            );
        }
        String object = draftFinalObject(request, places);
        return new DraftObjective(
                "역사 미스터리",
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
                || containsCompact(text, "검은그림자")
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
        String area = blank(request.getArea(), "selected area");
        String first = places.isEmpty() ? "\uCCAB \uC870\uC0AC \uC9C0\uC810" : blank(places.get(0).getName(), "\uCCAB \uC870\uC0AC \uC9C0\uC810");
        String anchor = places.isEmpty() ? "\uB9C8\uC9C0\uB9C9 \uC870\uC0AC \uC9C0\uC810" : blank(places.get(places.size() - 1).getName(), "\uB9C8\uC9C0\uB9C9 \uC870\uC0AC \uC9C0\uC810");
        String object = draftFinalObject(request, places);
        String routeSignal = routeSignal(places);
        return area + "\uC758 " + first + "\uC5D0\uC11C \uC0AC\uAC74\uC774 \uC2DC\uC791\uB429\uB2C8\uB2E4. \uB2E8\uC11C\uB294 " + anchor + "\uB97C \uD5A5\uD558\uC9C0\uB9CC, " + routeSignal + " \uAE30\uB85D\uC774 \uC11C\uB85C \uC5C7\uAC08\uB9BD\uB2C8\uB2E4. \uD50C\uB808\uC774\uC5B4\uB294 \uD604\uC7A5 \uC790\uB8CC\uB97C \uBE44\uAD50\uD574 [" + object + "]\uC758 \uC815\uCCB4\uB97C \uBC1D\uD600\uC57C \uD569\uB2C8\uB2E4.";
    }




    private String draftFinalQuestion(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        return "\uBAA8\uC740 \uB2E8\uC11C\uAC00 \uAC00\uB9AC\uD0A4\uB294 [" + draftFinalObject(request, places) + "]\uC758 \uC815\uCCB4\uB294 \uBB34\uC5C7\uC785\uB2C8\uAE4C?";
    }




    private String draftFinalObject(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String joined = routeText(request, places);
        if (containsCompact(joined, "coffee") || containsCompact(joined, "cafe") || containsCompact(joined, "tea")) return "\uCC28\uAC00\uC6B4 \uCC28 \uAE30\uB85D";
        if (containsCompact(joined, "document") || containsCompact(joined, "seal") || containsCompact(joined, "signature")) return "\uBD89\uC740 \uBD09\uC778 \uBB38\uC11C";
        if (containsCompact(joined, "photo") || containsCompact(joined, "film") || containsCompact(joined, "lens")) return "\uBD09\uC778\uB41C \uD544\uB984 \uBD09\uD22C";
        if (containsCompact(joined, "market") || containsCompact(joined, "restaurant") || containsCompact(joined, "receipt")) return "\uCC22\uAE34 \uC601\uC218\uC99D \uC870\uAC01";
        if (containsCompact(joined, "palace") || containsCompact(joined, "archive")) return "\uC811\uD78C \uAE30\uB85D\uACE0 \uC0AC\uBCF8";
        return "\uBD09\uC778\uB41C \uAE30\uB85D \uC870\uAC01";
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
        return places.stream().flatMap(place -> place.getKeywords() == null ? java.util.stream.Stream.empty() : place.getKeywords().stream()).filter(value -> value != null && !value.isBlank()).findFirst().orElse("\uB3D9\uC120");
    }





    private List<AiEpisodeDraftResponse.SuspectDraft> defaultDraftSuspects() {
        return List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("\uC6A9\uC758\uC790 A").displayName("\uBD89\uC740 \uBD09\uD22C\uB97C \uBCF8 \uBAA9\uACA9\uC790").shortDescription("\uD604\uC7A5 \uB3D9\uC120 \uADFC\uCC98\uC5D0\uC11C \uB9C8\uC9C0\uB9C9 \uBD09\uD22C\uB97C \uBCF8 \uC778\uBB3C\uC785\uB2C8\uB2E4.").relationToVictim("\uB9C8\uC9C0\uB9C9 \uC758\uB8B0 \uC5F0\uB77D\uCC45").suspiciousPoint("\uB3D9\uC120 \uC2DC\uAC04\uD45C \uC77C\uBD80\uAC00 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4.").alibiSummary("\uBE44\uAC00 \uADF8\uCE60 \uB54C\uAE4C\uC9C0 \uCE74\uD398 \uADFC\uCC98\uC5D0 \uC788\uC5C8\uB2E4\uACE0 \uC8FC\uC7A5\uD569\uB2C8\uB2E4.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("\uC6A9\uC758\uC790 B").displayName("\uC0AC\uB77C\uC9C4 \uAE30\uB85D \uAD00\uB9AC\uC778").shortDescription("\uD544\uB984\uACFC \uC99D\uAC70 \uD30C\uC77C\uC744 \uAD00\uB9AC\uD558\uB358 \uBCF4\uC870\uC778\uC785\uB2C8\uB2E4.").relationToVictim("\uAE30\uB85D\uACE0 \uCDE8\uAE09\uC790").suspiciousPoint("\uC0AC\uB77C\uC9C4 \uD544\uB984\uC758 \uBCF4\uAD00 \uC704\uCE58\uB97C \uC54C\uACE0 \uC788\uC5C8\uC2B5\uB2C8\uB2E4.").alibiSummary("\uAE30\uB85D\uC2E4\uC5D0 \uC788\uC5C8\uB2E4\uACE0 \uC8FC\uC7A5\uD558\uC9C0\uB9CC \uD655\uC778\uD55C \uBAA9\uACA9\uC790\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("\uC6A9\uC758\uC790 C").displayName("\uAC80\uC740 \uBD09\uD22C\uB97C \uC62E\uAE34 \uBC30\uB2EC\uC778").shortDescription("\uBAA9\uC801\uC9C0 \uB2E8\uC11C\uC640 \uB3D9\uC120\uC774 \uACB9\uCE58\uB294 \uBC30\uB2EC\uC778\uC785\uB2C8\uB2E4.").relationToVictim("\uCD5C\uC885 \uB2E8\uC11C \uC6B4\uBC18\uC790").suspiciousPoint("\uC815\uB2F5\uC744 \uD6D4\uCE5C \uAC83\uC774 \uC544\uB2C8\uB77C \uB2E8\uC11C \uD750\uB984\uC744 \uC228\uACBC\uC744 \uAC00\uB2A5\uC131\uC774 \uC788\uC2B5\uB2C8\uB2E4.").alibiSummary("\uBC30\uB2EC \uB3D9\uC120\uACFC \uBAA9\uACA9 \uC2DC\uAC04\uC774 \uB9DE\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.").build()
        );
    }


    private List<AiEpisodeDraftResponse.EvidenceDraft> defaultDraftEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return missions.stream().limit(8).map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                .title(mission.getRewardClue() + " \uB2E8\uC11C \uCE74\uB4DC")
                .type(evidenceTypeForMission(mission))
                .imageUrl("")
                .imagePrompt("Create a high-quality detective evidence image for a Korean outdoor escape-room case file. Subject: "
                        + mission.getRewardClue() + " clue card. Story detail: Case material unlocked after solving this mission. "
                        + caseFileIllustrationStylePrompt()
                        + "If any person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story era. "
                        + caseFileNegativeImagePrompt())
                .textSummary("\uC774 \uBBF8\uC158\uC744 \uD480\uBA74 \uD574\uAE08\uB418\uB294 \uC0AC\uAC74 \uC790\uB8CC\uC785\uB2C8\uB2E4.")
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
        episode.setFinalAnswerAliases(join(withKeywordContract(draft.getFinalAnswerAliases(), draft.getFinalAnswerKeywords())));
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
            spot.setStoryText(sanitizeCategoryCodes(storyTextForSave(mission, draft)));
            spot.setArrivalRadius(mission.getArrivalRadius() == null ? 50.0 : Math.max(10.0, mission.getArrivalRadius()));
            spot.setFieldVerified(true);
            spot.setFieldVerificationNote("AI/\uC0AC\uC774\uD2B8 \uB370\uC774\uD130 \uAE30\uBC18 \uAC80\uC218 \uC644\uB8CC \uCD08\uC548\uC785\uB2C8\uB2E4. \uC88C\uD45C, \uB3C4\uCC29 \uBC18\uACBD, \uD37C\uC990 \uADFC\uAC70\uB294 \uC81C\uACF5\uB41C \uD6C4\uBCF4 \uB370\uC774\uD130\uB85C \uD655\uC778\uD588\uC73C\uBA70 \uC2E4\uC81C GPS QA\uB294 \uC120\uD0DD \uC0AC\uD56D\uC785\uB2C8\uB2E4.");
            adminEpisodeRepository.insertSpot(spot);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            spotByOrder.put(order, spot);
            if (isMissionAnswerDisconnected(mission, missions)) {
                normalizeMissionForReview(mission);
            }

            Puzzle puzzle = new Puzzle();
            puzzle.setMissionSpotId(spot.getId());
            puzzle.setPuzzleType(validateValue(blank(mission.getPuzzleType(), "OBSERVATION"), PUZZLE_TYPES, "INVALID_PUZZLE_TYPE", "Unsupported puzzleType."));
            puzzle.setQuestionText(blank(sanitizeCategoryCodes(mission.getQuestionText()), "Review required."));
            puzzle.setAnswer(blank(sanitizeCategoryCodes(mission.getAnswer()), "현장단서"));
            puzzle.setAnswerFormat(validateValue(blank(mission.getAnswerFormat(), "TEXT"), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "Unsupported answerFormat."));
            puzzle.setRewardClue(blank(sanitizeCategoryCodes(rewardClueForSave(mission, i)), "보정 단서"));
            puzzle.setRewardPayload(null);
            puzzle.setDifficulty("NORMAL");
            adminEpisodeRepository.insertPuzzle(puzzle);
            puzzleByOrder.put(order, puzzle);
            List<String> hints = mission.getHints() == null ? List.of() : mission.getHints();
            for (int hintIndex = 0; hintIndex < Math.min(3, hints.size()); hintIndex++) {
                adminEpisodeRepository.insertHint(puzzle.getId(), hintIndex + 1, sanitizeHintText(sanitizeCategoryCodes(hints.get(hintIndex)), mission));
            }
        }

        List<String> approvedFinalKeywords = finalKeywordValues(request.getSourceInput());
        List<CaseSuspect> suspects = saveDraftSuspects(episode.getId(), draft.getSuspects(), approvedFinalKeywords);
        Map<Integer, CaseEvidence> evidenceByMissionOrder = saveDraftEvidences(episode.getId(), draft.getEvidences(), missions, spotByOrder, suspects);
        applyDraftRewardPayloads(episode.getId(), missions, puzzleByOrder, evidenceByMissionOrder, approvedFinalKeywords);
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
                .unlockedByDefault(true)
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

    private List<CaseSuspect> saveDraftSuspects(Long episodeId, List<AiEpisodeDraftResponse.SuspectDraft> drafts, List<String> finalAnswerKeywords) {
        List<AiEpisodeDraftResponse.SuspectDraft> source = drafts == null || drafts.isEmpty() ? defaultDraftSuspects() : drafts;
        String relatedPersonKeyword = finalAnswerKeywords == null || finalAnswerKeywords.isEmpty() ? "" : finalAnswerKeywords.get(0);
        int relatedPersonIndex = relatedPersonTargetIndex(source, relatedPersonKeyword);
        List<CaseSuspect> saved = new ArrayList<>();
        int index = 0;
        for (AiEpisodeDraftResponse.SuspectDraft draft : source) {
            CaseSuspect suspect = new CaseSuspect();
            suspect.setEpisodeId(episodeId);
            suspect.setAlias(blank(draft.getAlias(), "Suspect " + (char) ('A' + index)));
            String displayName = safeSuspectDisplayName(draft.getDisplayName(), index);
            if (!missing(relatedPersonKeyword) && (same(displayName, relatedPersonKeyword) || index == relatedPersonIndex)) {
                displayName = relatedPersonKeyword.trim();
            }
            suspect.setDisplayName(displayName);
            suspect.setShortDescription(blank(draft.getShortDescription(), "A stakeholder who may have distorted the clue chain."));
            suspect.setRelationToVictim(blank(draft.getRelationToVictim(), "Case stakeholder"));
            suspect.setSuspiciousPoint(blank(draft.getSuspiciousPoint(), "There is an unexplained gap in the timeline."));
            suspect.setAlibiSummary(blank(draft.getAlibiSummary(), "The alibi requires comparison with evidence cards."));
            suspect.setPortraitImageUrl(draft.getPortraitImageUrl());
            suspect.setImagePrompt(ensureKoreanPersonPrompt(blank(draft.getImagePrompt(), buildSuspectImagePrompt(draft))));
            suspect.setUnlockedByDefault(true);
            suspect.setDisplayOrder(index + 1);
            adminEpisodeRepository.insertSuspect(suspect);
            saved.add(suspect);
            index++;
        }
        return saved;
    }

    private int relatedPersonTargetIndex(List<AiEpisodeDraftResponse.SuspectDraft> source, String relatedPersonKeyword) {
        if (missing(relatedPersonKeyword) || source == null || source.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < source.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft draft = source.get(i);
            if (draft != null && same(draft.getDisplayName(), relatedPersonKeyword)) {
                return i;
            }
        }
        return Math.floorMod(compact(relatedPersonKeyword).hashCode(), source.size());
    }

    private boolean isUsableSuspectName(String value) {
        if (missing(value)) {
            return false;
        }
        String normalized = value.trim().replaceAll("\\s+", "");
        if (!normalized.matches("[\\uAC00-\\uD7A3]{2,4}")) {
            return false;
        }
        return !looksLikeSuspectAlias(normalized);
    }

    private String safeSuspectDisplayName(String value, int index) {
        String name = blank(value, "");
        if (missing(name) || looksLikeSuspectAlias(name)) {
            return switch (Math.floorMod(index, 3)) {
                case 0 -> "한서윤";
                case 1 -> "강도윤";
                default -> "윤재하";
            };
        }
        return name;
    }

    private boolean looksLikeSuspectAlias(String value) {
        if (missing(value)) {
            return true;
        }
        String compactValue = compact(value);
        return compactValue.length() <= 2
                || containsCompact(compactValue, "의뢰인")
                || containsCompact(compactValue, "정리관")
                || containsCompact(compactValue, "전달자")
                || containsCompact(compactValue, "연락책")
                || containsCompact(compactValue, "보관담당자")
                || containsCompact(compactValue, "기록중개인")
                || containsCompact(compactValue, "관계자");
    }




    private Map<Integer, CaseEvidence> saveDraftEvidences(Long episodeId, List<AiEpisodeDraftResponse.EvidenceDraft> drafts, List<AiEpisodeDraftResponse.MissionDraft> missions, Map<Integer, MissionSpot> spotByOrder, List<CaseSuspect> suspects) {
        Map<Integer, CaseEvidence> evidenceByMissionOrder = new HashMap<>();
        List<AiEpisodeDraftResponse.EvidenceDraft> source = drafts == null ? List.of() : drafts;
        Map<Integer, AiEpisodeDraftResponse.MissionDraft> missionByOrder = missionByOrder(missions);
        int index = 0;
        for (AiEpisodeDraftResponse.EvidenceDraft draft : source) {
            AiEpisodeDraftResponse.MissionDraft mission = missionByOrder.get(draft.getSourceMissionOrder());
            String evidenceType = evidenceTypeForMission(mission);
            CaseEvidence evidence = new CaseEvidence();
            evidence.setEpisodeId(episodeId);
            evidence.setTitle(blank(draft.getTitle(), "\uC0AC\uAC74 \uC790\uB8CC " + (index + 1)));
            evidence.setType(validateValue(evidenceType, EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "Unsupported evidence type."));
            evidence.setImageUrl(draft.getImageUrl());
            evidence.setImagePrompt(ensureKoreanEvidencePrompt(blank(draft.getImagePrompt(), buildEvidenceImagePrompt(draft))));
            evidence.setTextSummary(blank(draft.getTextSummary(), "\uD604\uC7A5 \uB2E8\uC11C\uB97C \uC870\uD569\uD574 \uCD5C\uC885 \uCD94\uB9AC\uB97C \uB3D5\uB294 \uC0AC\uAC74 \uC790\uB8CC\uC785\uB2C8\uB2E4."));
            evidence.setSourceSpotId(resolveSourceSpotId(draft, spotByOrder));
            evidence.setRelatedSuspectId("STORY_CLUE".equals(evidenceType) ? null : resolveLinkedSuspectId(draft, suspects, index));
            evidence.setRelatedClueType(evidenceType);
            evidence.setUnlockedByDefault(index == 0);
            evidence.setDisplayOrder(index + 1);
            adminEpisodeRepository.insertEvidence(evidence);
            if (draft.getSourceMissionOrder() != null) evidenceByMissionOrder.put(draft.getSourceMissionOrder(), evidence);
            index++;
        }
        return evidenceByMissionOrder;
    }

    private Map<Integer, AiEpisodeDraftResponse.MissionDraft> missionByOrder(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        Map<Integer, AiEpisodeDraftResponse.MissionDraft> result = new HashMap<>();
        if (missions == null) {
            return result;
        }
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            result.put(order, mission);
        }
        return result;
    }

    private String evidenceTypeForMission(AiEpisodeDraftResponse.MissionDraft mission) {
        if (mission == null) {
            return "NOTE";
        }
        if (isStartMission(mission)) {
            return "STORY_CLUE";
        }
        String slotId = normalizeSlotId(mission.getRewardClueSlotId());
        if ("RELATED_PERSON".equals(slotId)) {
            return "SUSPECT_CLUE";
        }
        if ("ANSWER_CLUE".equals(slotId)) {
            return "ANSWER_CLUE";
        }
        if ("FINAL_DESTINATION".equals(slotId)) {
            return "DESTINATION_CLUE";
        }
        return switch (blank(mission.getClueRole(), "")) {
            case "DESTINATION_HINT", "FINAL_PLACE" -> "DESTINATION_CLUE";
            case "ANSWER_HINT" -> "ANSWER_CLUE";
            default -> "STORY_CLUE";
        };
    }

    private String rewardTypeForMission(AiEpisodeDraftResponse.MissionDraft mission) {
        if (isStartMission(mission)) {
            return "STORY_CLUE";
        }
        String evidenceType = evidenceTypeForMission(mission);
        if ("SUSPECT_CLUE".equals(evidenceType)) {
            return "SUSPECT_CLUE";
        }
        if ("DESTINATION_CLUE".equals(evidenceType)) {
            return "DESTINATION_CLUE";
        }
        if ("STORY_CLUE".equals(evidenceType)) {
            return "STORY_CLUE";
        }
        return "ANSWER_CLUE";
    }

    private boolean isStartMission(AiEpisodeDraftResponse.MissionDraft mission) {
        if (mission == null) {
            return false;
        }
        return Integer.valueOf(1).equals(mission.getOrder())
                || "START".equals(normalizeType(mission.getMarkerType()))
                || "START".equals(normalizeType(mission.getClueRole()));
    }

    private String normalizeSlotId(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RELATED_PERSON", "ANSWER_CLUE", "FINAL_DESTINATION" -> normalized;
            default -> "";
        };
    }


    private Long resolveSourceSpotId(AiEpisodeDraftResponse.EvidenceDraft draft, Map<Integer, MissionSpot> spotByOrder) {
        if (draft == null || draft.getSourceMissionOrder() == null || spotByOrder == null) {
            return null;
        }
        MissionSpot spot = spotByOrder.get(draft.getSourceMissionOrder());
        return spot == null ? null : spot.getId();
    }

    private String buildSuspectImagePrompt(AiEpisodeDraftResponse.SuspectDraft draft) {
        String name = blank(draft.getDisplayName(), blank(draft.getAlias(), "case-file suspect"));
        String suspicion = blank(draft.getSuspiciousPoint(), "ambiguous motive and hidden route contradiction");
        return "Create a high-quality fictional detective case-file portrait of " + name
                + ". Casting is mandatory: depict a fictional Korean person from Seoul, South Korea. The subject must look unmistakably Korean; preserve the story's specified age, gender, occupation, and historical era. Do not cast a Western or European-looking model, and do not change the character's Korean identity. "
                + caseFileIllustrationStylePrompt()
                + "Character clue: " + suspicion
                + ". Composition: bust portrait, 3/4 view, natural Korean styling and grooming appropriate to the character, restrained expression, sharp facial silhouette. "
                + caseFileNegativeImagePrompt() + " No celebrity likeness.";
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
                + "Preserve the story's age, gender, occupation, and era. Do not cast a Western or European-looking model or change the character's Korean identity.";
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
                + "Do not cast a Western or European-looking model.";
    }

    private String buildEvidenceImagePrompt(AiEpisodeDraftResponse.EvidenceDraft draft) {
        String title = blank(draft.getTitle(), "case evidence card");
        String summary = blank(draft.getTextSummary(), "a clue object connected to the route and final deduction");
        return "Create a high-quality detective evidence image for a Korean outdoor escape-room case file. "
                + "Subject: " + title + ". Story detail: " + summary
                + ". " + caseFileIllustrationStylePrompt()
                + "If any person, hand, portrait, reflection, or human silhouette appears, it must belong to a fictional Korean person in Seoul and match the story's era. "
                + caseFileNegativeImagePrompt();
    }

    private String ensureCaseFileIllustrationStyle(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (normalized.contains("flat 2d korean webtoon") || normalized.contains("matte paper grain")) {
            return prompt;
        }
        return prompt.trim() + " " + caseFileIllustrationStylePrompt() + caseFileNegativeImagePrompt();
    }

    private String caseFileIllustrationStylePrompt() {
        return "Visual style reference: flat 2D Korean webtoon / printed storybook illustration like the attached references, muted earth-tone palette, soft matte paper grain, subtle archival texture, simplified shapes, clean dark ink outlines, gentle cel shading, restrained noir mood, poster-like composition, not photorealistic. Match the case era exactly: Joseon, Daehan Empire, colonial modern, or contemporary Korean styling as specified by the story; use era-appropriate clothing, hair, props, architecture, paper, seals, and handwriting-like marks. ";
    }

    private String caseFileNegativeImagePrompt() {
        return "Negative constraints: no photorealism, no 3D render, no glossy game art, no Western comic style, no European-looking models, no foreign tourist styling, no modern objects unless the story era is contemporary, no readable real text, no watermark, no logo, no UI frame. ";
    }

    private Long resolveLinkedSuspectId(AiEpisodeDraftResponse.EvidenceDraft draft, List<CaseSuspect> suspects, int index) {
        if (suspects == null || suspects.isEmpty()) {
            return null;
        }
        return suspects.get(Math.floorMod(index, suspects.size())).getId();
    }




    private void applyDraftRewardPayloads(Long episodeId, List<AiEpisodeDraftResponse.MissionDraft> missions, Map<Integer, Puzzle> puzzleByOrder, Map<Integer, CaseEvidence> evidenceByMissionOrder, List<String> finalKeywords) {
        String coreKeyword = finalKeywords != null && finalKeywords.size() >= 2 ? finalKeywords.get(1) : "";
        int answerClueRevealIndex = 0;
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            Puzzle puzzle = puzzleByOrder.get(order);
            if (puzzle == null) {
                continue;
            }
            String slotId = isStartMission(mission) ? "" : normalizeSlotId(mission.getRewardClueSlotId());
            String clueType = rewardTypeForMission(mission);
            CaseEvidence evidence = evidenceByMissionOrder.get(order);
            List<Map<String, Object>> rewards = new ArrayList<>();
            Map<String, Object> clueReward = new LinkedHashMap<>();
            clueReward.put("type", clueType);
            clueReward.put("value", sanitizeCategoryCodes(blank(rewardClueForSave(mission, i), "보상 단서")));
            if (!slotId.isBlank()) {
                clueReward.put("slotId", slotId);
            }
            if (shouldAttachAnswerLetterReveal(slotId, clueType) && coreKeyword != null && !coreKeyword.isBlank()) {
                clueReward.put("letterReveal", distributedLetterReveal(coreKeyword, answerClueRevealIndex));
                answerClueRevealIndex++;
            }
            rewards.add(clueReward);
            if (evidence != null) {
                Map<String, Object> evidenceReward = new LinkedHashMap<>();
                evidenceReward.put("type", "EVIDENCE_UNLOCK");
                evidenceReward.put("targetId", evidence.getId());
                rewards.add(evidenceReward);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rewards", rewards);
            payload.put("interaction", buildPuzzleInteraction(mission, i, clueType));
            puzzle.setRewardPayload(writeObjectJson(payload));
            AdminRewardPayloadValidationResponse validation = validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));
            if (!validation.isValid()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFT_REWARD_PAYLOAD", String.join(" / ", validation.getErrors()));
            }
            adminEpisodeRepository.updatePuzzle(puzzle);
        }
    }

    private String storyTextForSave(AiEpisodeDraftResponse.MissionDraft mission, AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (mission == null) {
            return "";
        }
        if (!"START".equals(normalizeType(mission.getMarkerType()))
                && !"START".equals(normalizeType(mission.getClueRole()))) {
            return mission.getStoryText();
        }
        String story = blank(mission.getStoryText(), "");
        if (story.length() >= 120 && !looksLikeOperationBriefing(story)) {
            return story;
        }
        String synopsis = draft == null ? "" : blank(draft.getFictionSynopsis(), "");
        if (!synopsis.isBlank() && synopsis.length() >= 120) {
            return synopsis;
        }
        return "작전 파일이 활성화되었습니다. 현장에 남은 기록, 이동 흔적, 관계자 진술을 순서대로 대조해 사건의 배후와 핵심 단서, 마지막 장소를 밝혀야 합니다. 첫 지점에서는 전체 임무의 목적과 조사 기준을 확인하고 다음 단서로 이동하세요.";
    }

    private String rewardClueForSave(AiEpisodeDraftResponse.MissionDraft mission, int index) {
        if (mission == null) {
            return "보상 단서";
        }
        String role = normalizeType(blank(mission.getClueRole(), mission.getMarkerType()));
        if ("START".equals(role)) {
            return startStoryRevealClue(mission, index);
        }
        String reward = blank(mission.getRewardClue(), "");
        if (reward.isBlank() || reward.length() > 90 || looksLikeOperationBriefing(reward)) {
            return buildRewardClue(role, index);
        }
        return reward;
    }

    private String conciseStartRewardClue(String reward) {
        String text = blank(reward, "");
        if (!text.isBlank() && text.length() <= 45 && !looksLikeOperationBriefing(text)) {
            return text;
        }
        return "현장 기록에서 서로 맞지 않는 움직임이 포착되었다.";
    }

    private String startStoryRevealClue(AiEpisodeDraftResponse.MissionDraft mission, int index) {
        String reward = blank(mission.getRewardClue(), "");
        if (isUsableStartStoryReveal(reward)) {
            return reward.trim();
        }
        String basis = safeStoryBasis(firstGroundingText(mission), index);
        return "시작 기록이 해금되었습니다. 사라진 대상이 남긴 첫 기록에는 실제 동선과 맞지 않는 " + basis
                + " 흔적이 있습니다. 이 단서는 단순한 이탈이 아니라 누군가가 순서와 시간을 조정해 사건의 방향을 흐렸을 가능성을 보여줍니다.";
    }

    private boolean isUsableStartStoryReveal(String text) {
        if (text == null || text.isBlank() || text.length() < 70 || text.length() > 240) {
            return false;
        }
        if (looksLikeOperationBriefing(text) || isInternalContentText(text)) {
            return false;
        }
        return !containsAny(text, "관련자 힌트", "관계자 힌트", "용의자", "범인", "알리바이", "관계자 카드", "SUSPECT");
    }

    private String safeStoryBasis(String value, int index) {
        String token = safeMemoryCardToken(value);
        if (token.isBlank() || isInternalContentText(token)) {
            return STORY_TOKEN_FALLBACKS.get(Math.floorMod(index, STORY_TOKEN_FALLBACKS.size()));
        }
        return token;
    }

    private boolean looksLikeOperationBriefing(String text) {
        if (text == null) {
            return false;
        }
        return containsAny(text, "작전 파일이 활성화", "추적해야", "배후 인물", "접선 장소", "미션 파일", "임무를 시작");
    }

    private boolean shouldAttachAnswerLetterReveal(String slotId, String clueType) {
        return "ANSWER_CLUE".equals(slotId) || "ANSWER_CLUE".equals(clueType);
    }

    private Map<String, Object> distributedLetterReveal(String keyword, int revealIndex) {
        String source = keyword == null ? "" : keyword.trim();
        StringBuilder revealed = new StringBuilder();
        int syllableIndex = 0;
        int nonSpaceCount = Math.max(1, source.codePointCount(0, source.length()) - (int) source.codePoints().filter(Character::isWhitespace).count());
        int[] preferredPositions = new int[] {
                0,
                Math.min(2, nonSpaceCount - 1),
                nonSpaceCount - 1
        };
        int revealPosition = revealIndex < preferredPositions.length
                ? preferredPositions[revealIndex]
                : Math.floorMod(revealIndex, nonSpaceCount);

        for (int i = 0; i < source.length(); ) {
            int codePoint = source.codePointAt(i);
            if (Character.isWhitespace(codePoint)) {
                revealed.appendCodePoint(codePoint);
            } else {
                boolean expose = syllableIndex == revealPosition;
                revealed.append(expose ? new String(Character.toChars(codePoint)) : "□");
                syllableIndex++;
            }
            i += Character.charCount(codePoint);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetKeyword", source);
        payload.put("revealedText", revealed.toString());
        return payload;
    }

    private Map<String, Object> buildPuzzleInteraction(AiEpisodeDraftResponse.MissionDraft mission, int index, String clueType) {
        String basis = sanitizeCategoryCodes(firstGroundingText(mission));
        String localSolution = safeMiniGameSolution(localGameSolution(mission, basis, index), index);
        String type = chooseInteractionType(mission, index, localSolution);
        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("version", 1);
        interaction.put("type", type);
        interaction.put("title", interactionTitle(type, clueType));
        Map<String, Object> config = interactionConfig(type, localSolution, basis, index);
        interaction.put("prompt", "아래 미션을 해결하여 단서를 얻으세요.");
        interaction.put("missionDescription", missionDescription(type));
        interaction.put("storyHook", "아래 미션을 해결하여 단서를 얻으세요.");
        interaction.put("basis", sanitizeCategoryCodes(basis));
        interaction.put("localSolution", localSolution);
        interaction.put("timeLimitSeconds", switch (type) {
            case "RAPID_TAP", "UP_DOWN_TIMER", "NUMBER_SEQUENCE_TAP", "COLOR_STROOP", "LEFT_RIGHT_SORT" -> ((Number) config.getOrDefault("durationSeconds", 10)).intValue();
            case "MEMORY_CARD" -> 45;
            case "DIRECTION_SEQUENCE", "PATTERN_LOCK" -> 60;
            default -> 0;
        });
        interaction.put("config", config);
        return interaction;
    }

    private String chooseInteractionType(AiEpisodeDraftResponse.MissionDraft mission, int index, String answer) {
        String puzzleType = normalizeType(mission.getPuzzleType());
        String answerFormat = normalizeType(mission.getAnswerFormat());
        if ("NUMBER_LOCK".equals(puzzleType) || "NUMBER".equals(answerFormat) || answer.matches("\\d{2,}")) return "NUMBER_LOCK";
        return switch (Math.floorMod(index, 9)) {
            case 0 -> "MEMORY_CARD";
            case 1 -> "DIRECTION_SEQUENCE";
            case 2 -> "PATTERN_LOCK";
            case 3 -> "UP_DOWN_TIMER";
            case 4 -> "NUMBER_BASEBALL";
            case 5 -> "NUMBER_SEQUENCE_TAP";
            case 6 -> "COLOR_STROOP";
            case 7 -> "LEFT_RIGHT_SORT";
            default -> "RAPID_TAP";
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
            case "MEMORY_CARD" -> {
                config.put("cards", safeMemoryCards(answer, basis));
                config.put("maxMistakes", 5);
                config.put("previewSeconds", 2);
            }
            case "PATTERN_LOCK" -> config.put("nodes", patternNodes(answer, index));
            case "RAPID_TAP" -> {
                int seed = Math.abs((answer + basis + index).hashCode());
                int durationSeconds = 4 + Math.floorMod(seed, 7);
                int maxTaps = durationSeconds * 6;
                int gap = 1 + Math.floorMod(seed / 7, 5);
                config.put("durationSeconds", durationSeconds);
                config.put("maxTaps", maxTaps);
                config.put("target", maxTaps - gap);
                config.put("label", basis);
            }
            case "DIRECTION_SEQUENCE" -> {
                config.put("sequence", directionSequence(answer, index));
                config.put("strict", true);
            }
            case "UP_DOWN_TIMER" -> {
                int seed = Math.abs((answer + basis + index).hashCode());
                int min = 1;
                int max = 100;
                config.put("min", min);
                config.put("max", max);
                config.put("solution", min + Math.floorMod(seed, max));
                config.put("durationSeconds", 12 + Math.floorMod(seed / 11, 5));
            }
            case "NUMBER_BASEBALL" -> {
                config.put("digits", 3);
                config.put("solution", baseballDigits(answer, basis, index));
                config.put("maxAttempts", 8);
            }
            case "NUMBER_SEQUENCE_TAP" -> {
                List<Integer> sequence = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
                List<Integer> skipNumbers = numberTapRuleNumbers(answer, basis, index, 0, List.of());
                List<Integer> doubleNumbers = numberTapRuleNumbers(answer, basis, index, 3, skipNumbers);
                config.put("sequence", sequence);
                config.put("skipNumbers", skipNumbers);
                config.put("doubleNumbers", doubleNumbers);
                config.put("durationSeconds", 7);
                config.put("buttons", sequence);
            }
            case "COLOR_STROOP" -> {
                config.put("rounds", 10);
                config.put("perRoundSeconds", 2);
                config.put("durationSeconds", 20);
                config.put("passCorrectCount", 8);
                config.put("colors", colorStroopColors());
                config.put("items", colorStroopItems(answer, basis, index));
            }
            case "LEFT_RIGHT_SORT" -> {
                config.put("rounds", 30);
                config.put("durationSeconds", 15);
                config.put("passCorrectCount", 20);
                config.put("targets", List.of(
                        Map.of("key", "CAT", "label", "고양이", "correctSide", "LEFT"),
                        Map.of("key", "DOG", "label", "개", "correctSide", "RIGHT")
                ));
            }
            default -> config.put("label", basis);
        }
        return config;
    }

    private String interactionTitle(String type, String clueType) {
        String prefix = switch (clueType) {
            case "ANSWER_CLUE" -> "증거 해독";
            case "DESTINATION_CLUE" -> "동선 복원";
            default -> "사건 장치";
        };
        return prefix + " · " + switch (type) {
            case "NUMBER_LOCK" -> "숫자 락";
            case "WORD_COMPOSE" -> "단어 조합";
            case "MEMORY_CARD" -> "기억 카드";
            case "PATTERN_LOCK" -> "패턴 잠금";
            case "RAPID_TAP" -> "빠른 탭";
            case "DIRECTION_SEQUENCE" -> "방향키 조합";
            case "UP_DOWN_TIMER" -> "시간제한 업다운";
            case "NUMBER_BASEBALL" -> "숫자야구";
            case "NUMBER_SEQUENCE_TAP" -> "숫자 누르기";
            case "COLOR_STROOP" -> "색깔 반전퀴즈";
            case "LEFT_RIGHT_SORT" -> "좌우 분류 반응";
            default -> "단서 입력";
        };
    }

    private String missionDescription(String type) {
        return switch (type) {
            case "NUMBER_LOCK" -> "숫자 암호를 맞춘 뒤 결과를 제출하세요.";
            case "WORD_COMPOSE" -> "흩어진 단어 조각을 올바르게 조합한 뒤 결과를 제출하세요.";
            case "MEMORY_CARD" -> "카드 위치를 기억해 모든 짝을 맞춘 뒤 결과를 제출하세요.";
            case "PATTERN_LOCK" -> "잠깐 점등되는 노드 순서를 기억하고 그대로 입력한 뒤 결과를 제출하세요.";
            case "RAPID_TAP" -> "제한 시간 안에 목표 횟수만큼 정확히 탭한 뒤 결과를 제출하세요.";
            case "DIRECTION_SEQUENCE" -> "제시된 방향 순서를 빠짐없이 입력한 뒤 결과를 제출하세요.";
            case "UP_DOWN_TIMER" -> "UP/DOWN 힌트로 범위를 좁혀 제한 시간 안에 숨겨진 숫자를 추리하세요.";
            case "NUMBER_BASEBALL" -> "스트라이크와 볼 결과를 기억해 숨겨진 숫자와 위치를 추리하세요.";
            case "NUMBER_SEQUENCE_TAP" -> "7초 안에 1부터 9까지 누르되, 건너뛰기와 두 번 누르기 조건을 정확히 지키세요.";
            case "COLOR_STROOP" -> "글자의 의미가 아니라 글자에 칠해진 실제 색깔을 제한 시간 안에 고르세요.";
            case "LEFT_RIGHT_SORT" -> "중앙에 표시되는 동물을 정해진 좌우 방향으로 빠르게 분류하세요.";
            default -> "미션을 완료한 뒤 결과를 제출하세요.";
        };
    }

    private String safeInteractionAnswer(AiEpisodeDraftResponse.MissionDraft mission) {
        String answer = blank(mission.getAnswer(), firstGroundingText(mission));
        if (containsCompact(answer, "검수필요") || containsCompact(answer, "review-required")) {
            return firstGroundingText(mission);
        }
        return answer;
    }

    private String localGameSolution(AiEpisodeDraftResponse.MissionDraft mission, String basis, int index) {
        String source = blank(basis, "");
        if (containsCompact(source, "검수필요") || containsCompact(source, "review-required") || source.isBlank()) {
            source = blank(mission.getRewardClue(), "단서" + (index + 1));
        }
        return source.length() > 8 ? source.substring(0, 8) : source;
    }

    private String safeMiniGameSolution(String value, int index) {
        String token = safeMemoryCardToken(value);
        if (token.isBlank() || isInternalContentText(token)) {
            return STORY_TOKEN_FALLBACKS.get(Math.floorMod(index, STORY_TOKEN_FALLBACKS.size()));
        }
        return token;
    }

    private List<String> shuffledCharacters(String answer) {
        List<String> chars = new ArrayList<>(answer.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(value -> !value.isBlank())
                .toList());
        if (chars.size() < 2) {
            chars.add("단");
            chars.add("서");
        }
        java.util.Collections.rotate(chars, Math.max(1, chars.size() / 2));
        return chars;
    }

    private List<String> safeMemoryCards(String answer, String basis) {
        List<String> seeds = new ArrayList<>(List.of(answer, basis, "봉투", "사진", "문서", "조각", "동선", "증거", "수첩", "표식"));
        List<String> cards = seeds.stream()
                .map(this::safeMemoryCardToken)
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !isInternalContentText(value))
                .distinct()
                .limit(6)
                .collect(Collectors.toCollection(ArrayList::new));
        for (String fallback : STORY_TOKEN_FALLBACKS) {
            if (cards.size() >= 6) {
                break;
            }
            if (!cards.contains(fallback)) {
                cards.add(fallback);
            }
        }
        return cards;
    }

    private String safeMemoryCardToken(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[^가-힣A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank() || isInternalContentText(cleaned)) {
            return "";
        }
        List<String> blocked = List.of(
                "관리자", "검수", "확인", "필요", "공식", "설명", "없음", "자료", "부족", "보강", "추정",
                "admin", "review", "required", "field", "verification", "kakao", "tourapi"
        );
        for (String token : cleaned.split("\\s+")) {
            String normalized = token.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2
                    && normalized.length() <= 4
                    && blocked.stream().noneMatch(lower::contains)) {
                return normalized;
            }
        }
        String compacted = cleaned.replace(" ", "");
        String lowerCompacted = compacted.toLowerCase(Locale.ROOT);
        if (isInternalContentText(compacted) || blocked.stream().anyMatch(lowerCompacted::contains)) {
            return "";
        }
        return compacted.length() > 4 ? compacted.substring(0, 4) : compacted;
    }

    private List<String> memoryCards(String answer, String basis) {
        List<String> seeds = new ArrayList<>(List.of(answer, basis, "봉인", "사진", "문서", "그림자", "동선", "증거"));
        List<String> cards = seeds.stream()
                .map(this::memoryCardToken)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(6)
                .collect(Collectors.toCollection(ArrayList::new));
        while (cards.size() < 6) {
            cards.add(List.of("추적", "대조", "확인", "복원", "해독", "잠금").get(cards.size()));
        }
        return cards;
    }

    private String memoryCardToken(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[^가-힣A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "";
        }
        List<String> blocked = List.of("관리자", "현장", "메모", "기록된", "사건", "단서", "해금", "최종", "정답", "힌트");
        for (String token : cleaned.split("\\s+")) {
            String normalized = token.trim();
            if (normalized.length() >= 2
                    && normalized.length() <= 4
                    && blocked.stream().noneMatch(normalized::contains)) {
                return normalized;
            }
        }
        String compacted = cleaned.replace(" ", "");
        return compacted.length() > 4 ? compacted.substring(0, 4) : compacted;
    }

    private List<Integer> patternNodes(String answer, int index) {
        int seed = Math.abs((answer + index).hashCode());
        List<Integer> nodes = new ArrayList<>();
        for (int divisor : List.of(1, 3, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41)) {
            int node = (seed / divisor) % 9;
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
            if (nodes.size() >= 7) {
                break;
            }
        }
        for (int node = 0; nodes.size() < 7 && node < 9; node++) {
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private List<String> directionSequence(String answer, int index) {
        List<String> directions = List.of("UP", "RIGHT", "DOWN", "LEFT");
        int seed = Math.abs((answer + index).hashCode());
        return List.of(
                directions.get(seed % directions.size()),
                directions.get((seed / 3) % directions.size()),
                directions.get((seed / 7) % directions.size()),
                directions.get((seed / 11) % directions.size()),
                directions.get((seed / 17) % directions.size()),
                directions.get((seed / 19) % directions.size())
        );
    }

    private String baseballDigits(String answer, String basis, int index) {
        int seed = Math.abs((answer + basis + index).hashCode());
        List<Integer> digits = new ArrayList<>();
        for (int divisor : List.of(1, 3, 7, 11, 13, 17, 19, 23, 29, 31)) {
            int digit = Math.floorMod(seed / divisor, 10);
            if (!digits.contains(digit)) {
                digits.add(digit);
            }
            if (digits.size() >= 3) {
                break;
            }
        }
        while (digits.size() < 3) {
            int digit = digits.size() + 1;
            if (!digits.contains(digit)) {
                digits.add(digit);
            }
        }
        return digits.stream().map(String::valueOf).collect(Collectors.joining());
    }

    private List<Integer> numberTapRuleNumbers(String answer, String basis, int index, int offset, List<Integer> excluded) {
        int seed = Math.abs((answer + basis + index).hashCode());
        List<Integer> values = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
        values.removeAll(excluded);
        java.util.Collections.rotate(values, Math.floorMod(seed / (offset + 1), values.size()));
        int count = 1 + Math.floorMod(seed / (offset + 7), 2);
        return values.subList(0, Math.min(count, values.size()));
    }

    private List<Map<String, String>> colorStroopColors() {
        return List.of(
                Map.of("key", "RED", "label", "빨강", "hex", "#ef4444"),
                Map.of("key", "BLUE", "label", "파랑", "hex", "#3b82f6"),
                Map.of("key", "GREEN", "label", "초록", "hex", "#22c55e"),
                Map.of("key", "YELLOW", "label", "노랑", "hex", "#eab308")
        );
    }

    private List<Map<String, String>> colorStroopItems(String answer, String basis, int index) {
        List<Map<String, String>> colors = colorStroopColors();
        int seed = Math.abs((answer + basis + index).hashCode());
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int textIndex = Math.floorMod(seed / (i + 1) + i, colors.size());
            int colorIndex = Math.floorMod(textIndex + 1 + Math.floorMod(seed / (i + 3), colors.size() - 1), colors.size());
            Map<String, String> text = colors.get(textIndex);
            Map<String, String> color = colors.get(colorIndex);
            items.add(Map.of(
                    "text", text.get("label"),
                    "textColorKey", color.get("key"),
                    "textColorLabel", color.get("label"),
                    "textColorHex", color.get("hex")
            ));
        }
        return items;
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
        List<CaseSuspect> suspects = adminEpisodeRepository.findSuspects(episode.getId());
        List<CaseEvidence> evidences = adminEpisodeRepository.findEvidences(episode.getId());
        if (suspects.size() < 3) errors.add("At least three suspect cards are required.");
        for (CaseSuspect suspect : suspects) {
            if (missing(suspect.getImagePrompt()) && !validExternalImageUrl(suspect.getPortraitImageUrl())) {
                errors.add("Suspect image prompt or external portrait URL is required: " + suspect.getDisplayName());
            }
        }
        if (evidences.size() < Math.max(1, spots.size() - 1)) errors.add("Evidence cards should cover the route.");
        for (CaseEvidence evidence : evidences) {
            if (missing(evidence.getImagePrompt()) && !validExternalImageUrl(evidence.getImageUrl())) {
                errors.add("Evidence image prompt or external image URL is required: " + evidence.getTitle());
            }
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_PUBLISH_NOT_READY", "Cannot publish: " + String.join(" / ", errors));
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
                .replace("KakaoLocal:CE7", "\uce74\ud398/\ucee4\ud53c \ud734\uc2dd \uc9c0\uc810")
                .replace("KakaoLocal:FD6", "\uc74c\uc2dd\uc810/\uc2dd\ub2f9 \uc0c1\uad8c")
                .replace("KakaoLocal:CT1", "\ubb38\ud654\uc2dc\uc124/\uc804\uc2dc \uc9c0\uc810")
                .replace("KakaoLocal:AT4", "\uad00\uad11\uba85\uc18c/\uba85\uc18c \uc9c0\uc810")
                .replace("CE7", "\uce74\ud398/\ucee4\ud53c \ud734\uc2dd \uc9c0\uc810")
                .replace("FD6", "\uc74c\uc2dd\uc810/\uc2dd\ub2f9 \uc0c1\uad8c")
                .replace("CT1", "\ubb38\ud654\uc2dc\uc124/\uc804\uc2dc \uc9c0\uc810")
                .replace("AT4", "\uad00\uad11\uba85\uc18c/\uba85\uc18c \uc9c0\uc810");
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
        episode.setEstimatedTime(blank(safeRequest.getEstimatedTime(), "90~120\uBD84"));
        episode.setEstimatedDistance(blank(safeRequest.getEstimatedDistance(), "\uB3C4\uBCF4 \uB3D9\uC120 \uD655\uC778 \uD544\uC694"));
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
        episode.setRecommendedPlayers(blank(safeRequest.getRecommendedPlayers(), "2~4\uBA85"));
        episode.setTeamRoleGuide(blank(safeRequest.getTeamRoleGuide(), "\uC9C0\uB3C4, \uC0AC\uAC74\uD30C\uC77C, \uD37C\uC990, \uAE30\uB85D \uC5ED\uD560\uB85C \uB098\uB204\uC5B4 \uC9C4\uD589\uD558\uC138\uC694."));
        episode.setNoticeText(blank(safeRequest.getNoticeText(), "\uACF5\uAC1C \uC804 \uD604\uC7A5 \uAC80\uC218\uB97C \uC644\uB8CC\uD558\uC138\uC694."));
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
        if (containsCompact(value, "KakaoLocal:CT1") || containsCompact(value, "KakaoLocal:AT4") || containsCompact(value, "\uBB38\uD654\uC2DC\uC124") || containsCompact(value, "\uAD00\uAD11\uBA85\uC18C")) score += 30;
        if (containsCompact(value, "KakaoLocal:CE7") || containsCompact(value, "KakaoLocal:FD6") || containsCompact(value, "\uCE74\uD398") || containsCompact(value, "\uC74C\uC2DD\uC810")) score += 20;
        double distance = distanceMeters(anchor.getLatitude(), anchor.getLongitude(), candidate.getLatitude(), candidate.getLongitude());
        if (Double.isFinite(distance)) {
            if (distance >= 80 && distance <= 700) score += 25;
            score -= Math.min(25, distance / 120.0);
        }
        return score;
    }




    private String enrichedDescription(AiEpisodeDraftRequest.PlaceInput source, List<AdminPlaceCandidateResponse> rankedNearby) {
        String base = blank(source.getDescription(), "\uC120\uD0DD\uB41C \uC870\uC0AC \uC9C0\uC810\uC785\uB2C8\uB2E4.");
        String topSignals = rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(3)
                .map(AdminPlaceCandidateResponse::getTitle)
                .collect(Collectors.joining(", "));
        if (topSignals.isBlank()) {
            return base;
        }
        return base + " \uC8FC\uBCC0 \uD655\uC778 \uD6C4\uBCF4: " + topSignals + ".";
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
        values.add("\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC7A5\uC18C\uBA85 \uAC04\uD310");
        values.add("\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC8FC\uC18C\uC640 \uC785\uAD6C \uC601\uC5ED");
        rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(4)
                .map(this::categoryVisibleElement)
                .forEach(values::add);
        values.add("\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC8FC\uBCC0 \uB3D9\uC120 \uB2E8\uC11C");
        return values;
    }

    private String enrichedAdminMemo(AiEpisodeDraftRequest.PlaceInput place, List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> memo = new ArrayList<>();
        if (!missing(place.getAdminMemo())) {
            memo.add(place.getAdminMemo());
        }
        memo.add("RAG/\uC0AC\uC774\uD2B8 \uBCF4\uAC15\uC73C\uB85C \uC8FC\uBCC0 Kakao Local \uC2E0\uD638\uB97C \uC0AC\uC6A9\uD574 \uAD00\uB9AC\uC790 \uD655\uC778 \uBC94\uC704\uB97C \uC881\uD614\uC2B5\uB2C8\uB2E4.");
        memo.add("\uC774 \uC2E0\uD638\uB294 \uD655\uC778 \uD6C4\uBCF4\uB85C\uB9CC \uC0AC\uC6A9\uD558\uC138\uC694. \uAC04\uD310, \uC22B\uC790, \uC870\uD615\uBB3C, \uC601\uC5C5\uC2DC\uAC04\uC740 \uD604\uC7A5 \uD655\uC778 \uC804\uAE4C\uC9C0 \uD655\uC815 \uC815\uBCF4\uB85C \uCDE8\uAE09\uD558\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.");
        if (place.getLatitude() == null || place.getLongitude() == null) {
            memo.add("\uC88C\uD45C\uAC00 \uC5C6\uC5B4 \uC678\uBD80 \uAC80\uC0C9\uC744 \uC2E4\uD589\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4. \uACF5\uAC1C \uC804 \uC704\uB3C4/\uACBD\uB3C4\uB97C \uCD94\uAC00\uD558\uC138\uC694.");
            return String.join("\n", memo);
        }
        if (rankedNearby.isEmpty()) {
            memo.add("900m \uC774\uB0B4\uC5D0\uC11C \uC8FC\uBCC0 \uC2E0\uD638\uB97C \uCC3E\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4. \uAD00\uB9AC\uC790\uAC00 \uC218\uB3D9 \uD604\uC7A5 \uBA54\uBAA8\uB97C \uCD94\uAC00\uD558\uC138\uC694.");
            return String.join("\n", memo);
        }
        List<AdminPlaceCandidateResponse> usable = rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(5)
                .toList();
        if (usable.isEmpty()) {
            rankedNearby.stream().findFirst().ifPresent(candidate -> memo.add(candidate.getTitle() + " - " + blank(candidate.getDescription(), "\uC678\uBD80 \uAC80\uC0C9 \uC2E4\uD328")));
            return String.join("\n", memo);
        }
        memo.add("\uC8FC\uC694 \uD655\uC778 \uD6C4\uBCF4:");
        for (int i = 0; i < usable.size(); i++) {
            AdminPlaceCandidateResponse candidate = usable.get(i);
            memo.add((i + 1) + ". " + candidate.getTitle()
                    + " / " + categoryKeyword(candidate)
                    + " / \uC57D " + Math.round(distanceMeters(place.getLatitude(), place.getLongitude(), candidate.getLatitude(), candidate.getLongitude())) + "m"
                    + " / \uD655\uC778 \uB300\uC0C1: " + categoryVisibleElement(candidate));
        }
        memo.add("\uAD8C\uC7A5 \uD37C\uC990 \uADFC\uAC70: \uD604\uC7A5 \uD655\uC778 \uD6C4 \uAD00\uB9AC\uC790\uAC00 \uD655\uC815\uD55C visibleElements/numbers\uB9CC \uC0AC\uC6A9\uD558\uC138\uC694. \uADF8 \uC804\uC5D0\uB294 AI\uAC00 \uC2E4\uC81C \uAD00\uCC30 \uC0AC\uC2E4\uC774 \uC544\uB2CC \uC2A4\uD1A0\uB9AC \uB2E8\uC11C\uC640 \uD655\uC778\uC6A9 \uC784\uC2DC \uB2E8\uC11C\uB9CC \uB9CC\uB4ED\uB2C8\uB2E4.");
        return String.join("\n", memo);
    }

    private String categoryKeyword(AdminPlaceCandidateResponse candidate) {
        String source = blank(candidate.getSource(), "");
        String value = String.join(" ", blank(candidate.getTitle(), ""), blank(candidate.getSource(), ""), blank(candidate.getDescription(), ""), blank(candidate.getAddress(), ""));
        if (source.contains("CT1") || containsCompact(value, "\ubb38\ud654\uc2dc\uc124") || containsCompact(value, "culture") || containsCompact(value, "museum") || containsCompact(value, "gallery") || containsCompact(value, "exhibition")) return "\ubb38\ud654\uc804\uc2dc";
        if (source.contains("CE7") || containsCompact(value, "\uce74\ud398") || containsCompact(value, "cafe") || containsCompact(value, "coffee")) return "\uce74\ud398\uc270\ud130";
        if (source.contains("FD6") || containsCompact(value, "\uc74c\uc2dd\uc810") || containsCompact(value, "\uc2dd\ub2f9") || containsCompact(value, "restaurant") || containsCompact(value, "food")) return "\uc2dd\ub2f9\uc0c1\uad8c";
        if (containsCompact(value, "park") || containsCompact(value, "square") || containsCompact(value, "street")) return "\uacf5\uac1c\uad11\uc7a5";
        return "\ud604\uc7a5\ub2e8\uc11c";
    }




    private String categoryVisibleElement(AdminPlaceCandidateResponse candidate) {
        String keyword = categoryKeyword(candidate);
        return switch (keyword) {
            case "\ubb38\ud654\uc804\uc2dc" -> "\uc804\uc2dc \uc548\ub0b4\ubb38 \ub610\ub294 \uac74\ubb3c \ud45c\uc9c0";
            case "\uce74\ud398\uc270\ud130" -> "\uba54\ub274\ud310 \ub610\ub294 \uc785\uad6c \ud45c\uc9c0";
            case "\uc2dd\ub2f9\uc0c1\uad8c" -> "\uac00\uac8c \uac04\ud310 \ub610\ub294 \uc0c1\uad8c \ub3d9\uc120 \ud45c\uc9c0";
            case "\uacf5\uac1c\uad11\uc7a5" -> "\uacf5\uacf5 \ud45c\uc9c0\uc11d \ub610\ub294 \ub3d9\uc120 \uc548\ub0b4\ud45c";
            default -> "\ud604\uc7a5 \ud45c\uc9c0\ubb3c \ub610\ub294 \uc8fc\ubcc0 \uad6c\uc870\ubb3c";
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

    private List<String> finalKeywordValues(AiEpisodeDraftRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty()) {
            return request.getFinalAnswerKeywordItems().stream()
                    .map(this::finalKeywordValue)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();
        }
        return request.getFinalAnswerKeywords() == null ? List.of() : request.getFinalAnswerKeywords().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String finalKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) {
            return "";
        }
        if ("RELATED_PERSON".equals(normalizeType(item.getSlotId())) || containsAny(item.getLabel(), "관련자", "관계자", "용의자", "실종자", "협력자")) {
            String personName = blank(item.getPersonName(), "");
            if (isUsableSuspectName(personName)) {
                return personName;
            }
        }
        return item.getKeyword();
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
        String basis = firstGroundingText(mission);
        if (containsCompact(basis, "검수필요") || containsCompact(basis, "review-required")) {
            basis = "현장단서";
        }
        mission.setPuzzleType("STORY_COMBINATION");
        mission.setQuestionText("제공된 현장 근거 [" + basis + "]를 사건파일 카드와 연결한 핵심 단어를 입력하세요.");
        mission.setAnswer(basis);
        mission.setAnswerFormat("TEXT");
        if (missing(mission.getRewardClue()) || containsCompact(mission.getRewardClue(), "검수필요")) {
            mission.setRewardClue("보정 단서");
        }
        mission.setHints(List.of(
                "문제에 제시된 [" + basis + "] 단서를 먼저 확인하세요.",
                "장소명 글자 추출이 아니라 현장 근거와 사건파일의 의미 연결을 보세요.",
                "현재 장소에서 얻은 단서만으로 풀 수 있어야 합니다."
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
            case "ANSWER_HINT" -> List.of("\uBC00\uB78D \uC778\uC7A5", "\uD750\uB9B0 \uC0AC\uC9C4", "\uC811\uD78C \uBB38\uC11C", "\uAE34 \uADF8\uB9BC\uC790").get(Math.min(Math.max(index - 1, 0), 3));
            case "DESTINATION_HINT", "FINAL" -> index % 2 == 0 ? "\uBD89\uC740 \uB2F4\uC7A5" : "\uB9C8\uC9C0\uB9C9 \uBB38";
            case "START" -> "\uC0AC\uAC74 \uC2DC\uC791 \uB2E8\uC11C";
            default -> "\uC0AC\uAC74 \uB2E8\uC11C";
        };
    }




    private String estimateDraftDistance(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (missions == null || missions.size() < 2) return "\uB3C4\uBCF4 \uB3D9\uC120 \uD655\uC778 \uD544\uC694";
        double meters = 0;
        for (int i = 1; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft prev = missions.get(i - 1);
            AiEpisodeDraftResponse.MissionDraft current = missions.get(i);
            double segment = distanceMeters(prev.getLatitude(), prev.getLongitude(), current.getLatitude(), current.getLongitude());
            if (Double.isFinite(segment)) meters += segment;
        }
        if (meters <= 0) return "\uB3C4\uBCF4 \uB3D9\uC120 \uD655\uC778 \uD544\uC694";
        double km = Math.round((meters / 1000.0) * 10.0) / 10.0;
        return "\uC57D " + km + "km";
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

    private boolean containsAny(String text, String... targets) {
        if (missing(text) || targets == null) {
            return false;
        }
        for (String target : targets) {
            if (!missing(target) && text.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalContentText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String compacted = compact(text);
        return INTERNAL_CONTENT_MARKERS.stream()
                .map(this::compact)
                .filter(marker -> !marker.isBlank())
                .anyMatch(compacted::contains);
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
                "Confirm coordinates and arrival radius from selected place data or admin GPS QA.",
                "Confirm every puzzle uses provided candidate data, admin memo, AI/site enrichment, or generated fiction-safe clues.",
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
