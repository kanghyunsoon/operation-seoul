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

@Service
@RequiredArgsConstructor
public class AdminEpisodeService {
    private static final Set<String> EPISODE_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> MARKER_TYPES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY", "FINAL_CANDIDATE", "FINAL");
    private static final Set<String> PUBLIC_MARKER_TYPES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY", "FINAL_CANDIDATE");
    private static final Set<String> CLUE_ROLES = Set.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY_CONTEXT", "FINAL_PLACE");
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
                String title = blank(place.get("title"), "이름 없는 장소");
                String address = place.get("address");
                String key = (title + "|" + address + "|" + lat + "|" + lng).toLowerCase(Locale.ROOT);
                unique.putIfAbsent(key, AdminPlaceCandidateResponse.builder()
                        .title(title)
                        .address(address)
                        .latitude(lat)
                        .longitude(lng)
                        .areaCode(normalizedAreaCode)
                        .source(place.getOrDefault("source", "TourAPI"))
                        .description(place.getOrDefault("overview", "TourAPI 관광지 후보입니다. 실제 역사/현장 정보는 관리자 검수 후 사용하세요."))
                        .contentId(place.get("contentId"))
                        .build());
            }
        }
        return unique.values().stream()
                .sorted(java.util.Comparator.comparing(AdminPlaceCandidateResponse::getTitle))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    public AdminEpisodeDetailResponse getEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);
        if (episode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "에피소드를 찾을 수 없습니다.");
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
                .finalCandidateCount(spots.stream().filter(spot -> "FINAL_CANDIDATE".equals(spot.getPublicMarkerType())).count())
                .puzzleCount(adminEpisodeRepository.countPuzzles(episodeId))
                .suspectCount(adminEpisodeRepository.countSuspects(episodeId))
                .evidenceCount(adminEpisodeRepository.countEvidences(episodeId))
                .build();
        try {
            validatePublishReadiness(episode);
            return AdminEpisodePublishReadinessResponse.builder()
                    .ready(true)
                    .status(episode.getStatus())
                    .message("공개 전환 조건을 충족했습니다. 그래도 현장 검수 후 PUBLISHED로 전환하세요.")
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
                    .message("아직 공개할 수 없습니다. 차단 항목을 수정하세요.")
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
        episode.setStatus(validateValue(text(request.getStatus(), episode.getStatus()), EPISODE_STATUSES, "INVALID_EPISODE_STATUS", "에피소드 상태는 DRAFT, PUBLISHED, ARCHIVED만 사용할 수 있습니다."));
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "장소를 찾을 수 없습니다."));
        spot.setPlaceName(text(request.getPlaceName(), spot.getPlaceName()));
        spot.setAddress(text(request.getAddress(), spot.getAddress()));
        spot.setLatitude(request.getLatitude() == null ? spot.getLatitude() : request.getLatitude());
        spot.setLongitude(request.getLongitude() == null ? spot.getLongitude() : request.getLongitude());
        spot.setMarkerType(validateValue(text(request.getMarkerType(), spot.getMarkerType()), MARKER_TYPES, "INVALID_MARKER_TYPE", "지원하지 않는 markerType입니다."));
        spot.setClueRole(validateValue(text(request.getClueRole(), spot.getClueRole()), CLUE_ROLES, "INVALID_CLUE_ROLE", "지원하지 않는 clueRole입니다."));
        spot.setPublicMarkerType(validateValue(text(request.getPublicMarkerType(), spot.getPublicMarkerType()), PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType에는 FINAL을 사용할 수 없습니다."));
        spot.setStoryText(text(request.getStoryText(), spot.getStoryText()));
        spot.setArrivalRadius(request.getArrivalRadius() == null ? spot.getArrivalRadius() : Math.max(10.0, request.getArrivalRadius()));
        spot.setFinalPlace(request.getFinalPlace() == null ? spot.getFinalPlace() : request.getFinalPlace());
        if (Boolean.TRUE.equals(spot.getFinalPlace())) {
            spot.setMarkerType("FINAL");
            spot.setClueRole("FINAL_PLACE");
            spot.setPublicMarkerType("FINAL_CANDIDATE");
        }
        adminEpisodeRepository.updateSpot(spot);
        return getEpisode(episodeId);
    }

    @Transactional
    public AdminEpisodeDetailResponse createSpot(Long episodeId, AdminSpotUpdateRequest request) {
        requireEditableEpisode(episodeId);
        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episodeId);
        if (spots.size() >= 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_SPOTS", "장소는 최대 9개까지 구성할 수 있습니다.");
        }
        MissionSpot spot = new MissionSpot();
        spot.setEpisodeId(episodeId);
        spot.setPlaceName(text(request.getPlaceName(), "새 조사 장소"));
        spot.setAddress(text(request.getAddress(), ""));
        spot.setLatitude(request.getLatitude() == null ? 37.5665 : request.getLatitude());
        spot.setLongitude(request.getLongitude() == null ? 126.9780 : request.getLongitude());
        spot.setMarkerType(validateValue(text(request.getMarkerType(), "STORY"), MARKER_TYPES, "INVALID_MARKER_TYPE", "지원하지 않는 markerType입니다."));
        spot.setClueRole(validateValue(text(request.getClueRole(), toClueRole(spot.getMarkerType())), CLUE_ROLES, "INVALID_CLUE_ROLE", "지원하지 않는 clueRole입니다."));
        spot.setPublicMarkerType(validateValue(text(request.getPublicMarkerType(), spot.getMarkerType()), PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType에는 FINAL을 사용할 수 없습니다."));
        spot.setStoryText(text(request.getStoryText(), "관리자 검수용 새 조사 문구입니다."));
        spot.setArrivalRadius(request.getArrivalRadius() == null ? 50.0 : Math.max(10.0, request.getArrivalRadius()));
        spot.setFinalPlace(Boolean.TRUE.equals(request.getFinalPlace()));
        if (Boolean.TRUE.equals(spot.getFinalPlace())) {
            spot.setMarkerType("FINAL");
            spot.setClueRole("FINAL_PLACE");
            spot.setPublicMarkerType("FINAL_CANDIDATE");
        }
        adminEpisodeRepository.insertSpot(spot);

        Puzzle puzzle = new Puzzle();
        puzzle.setMissionSpotId(spot.getId());
        puzzle.setPuzzleType("OBSERVATION");
        puzzle.setQuestionText("관리자가 현장 관찰 요소를 입력한 뒤 문제를 수정하세요.");
        puzzle.setAnswer("관리자검수");
        puzzle.setAnswerFormat("TEXT");
        puzzle.setRewardClue("새 단서");
        puzzle.setRewardPayload("{\"rewards\":[{\"type\":\"STORY_CLUE\",\"value\":\"새 단서\"}]}");
        puzzle.setDifficulty("NORMAL");
        adminEpisodeRepository.insertPuzzle(puzzle);
        adminEpisodeRepository.insertHint(puzzle.getId(), 1, "현장에서 확인 가능한 요소를 먼저 찾으세요.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 2, "관리자 메모에 입력된 관찰 요소를 기준으로 좁히세요.");
        adminEpisodeRepository.insertHint(puzzle.getId(), 3, "운영 공개 전 실제 현장 검수 후 힌트를 수정하세요.");
        return getEpisode(episodeId);
    }

    @Transactional
    public AdminEpisodeDetailResponse deleteSpot(Long episodeId, Long spotId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findSpots(episodeId).stream().anyMatch(spot -> spotId.equals(spot.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "장소를 찾을 수 없습니다.");
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
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "퍼즐을 찾을 수 없습니다.");
        }
        boolean belongsToEpisode = adminEpisodeRepository.findSpots(episodeId).stream()
                .anyMatch(spot -> spot.getId().equals(puzzle.getMissionSpotId()));
        if (!belongsToEpisode) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "해당 에피소드의 퍼즐이 아닙니다.");
        }
        puzzle.setPuzzleType(validateValue(text(request.getPuzzleType(), puzzle.getPuzzleType()), PUZZLE_TYPES, "INVALID_PUZZLE_TYPE", "지원하지 않는 puzzleType입니다."));
        puzzle.setQuestionText(text(request.getQuestionText(), puzzle.getQuestionText()));
        puzzle.setAnswer(text(request.getAnswer(), puzzle.getAnswer()));
        puzzle.setAnswerFormat(validateValue(text(request.getAnswerFormat(), puzzle.getAnswerFormat()), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "지원하지 않는 answerFormat입니다."));
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUSPECT_NOT_FOUND", "용의자 카드를 찾을 수 없습니다."));
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
        suspect.setAlias(text(request.getAlias(), "용의자 " + nextOrder));
        suspect.setDisplayName(text(request.getDisplayName(), "새 용의자"));
        suspect.setShortDescription(text(request.getShortDescription(), "관리자 검수용 용의자 카드입니다."));
        suspect.setPortraitImageUrl(text(request.getPortraitImageUrl(), null));
        suspect.setRelationToVictim(text(request.getRelationToVictim(), "관리자 입력 필요"));
        suspect.setSuspiciousPoint(text(request.getSuspiciousPoint(), "의심 포인트를 입력하세요."));
        suspect.setAlibiSummary(text(request.getAlibiSummary(), "알리바이를 입력하세요."));
        suspect.setUnlockedByDefault(request.getUnlockedByDefault() == null ? false : request.getUnlockedByDefault());
        suspect.setDisplayOrder(request.getDisplayOrder() == null ? nextOrder : request.getDisplayOrder());
        adminEpisodeRepository.insertSuspect(suspect);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse deleteSuspect(Long episodeId, Long suspectId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findSuspects(episodeId).stream().anyMatch(suspect -> suspectId.equals(suspect.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUSPECT_NOT_FOUND", "용의자 카드를 찾을 수 없습니다.");
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거 카드를 찾을 수 없습니다."));
        evidence.setTitle(text(request.getTitle(), evidence.getTitle()));
        evidence.setType(validateValue(text(request.getType(), evidence.getType()), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "지원하지 않는 증거 타입입니다."));
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
        evidence.setTitle(text(request.getTitle(), "새 사건 자료"));
        evidence.setType(validateValue(text(request.getType(), "NOTE"), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "지원하지 않는 증거 타입입니다."));
        evidence.setImageUrl(text(request.getImageUrl(), null));
        evidence.setTextSummary(text(request.getTextSummary(), "관리자 검수용 사건 자료입니다."));
        evidence.setSourceSpotId(validateOptionalSpot(episodeId, request.getSourceSpotId(), null));
        evidence.setRelatedSuspectId(validateOptionalSuspect(episodeId, request.getRelatedSuspectId(), null));
        evidence.setRelatedClueType(text(request.getRelatedClueType(), evidence.getType()));
        evidence.setUnlockedByDefault(request.getUnlockedByDefault() == null ? false : request.getUnlockedByDefault());
        evidence.setDisplayOrder(request.getDisplayOrder() == null ? nextOrder : request.getDisplayOrder());
        adminEpisodeRepository.insertEvidence(evidence);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse deleteEvidence(Long episodeId, Long evidenceId) {
        requireEditableEpisode(episodeId);
        boolean exists = adminEpisodeRepository.findEvidences(episodeId).stream().anyMatch(evidence -> evidenceId.equals(evidence.getId()));
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거 카드를 찾을 수 없습니다.");
        }
        adminEpisodeRepository.deleteEvidence(evidenceId);
        return getEpisode(episodeId);
    }

    public AdminEpisodeDetailResponse updatePartnerReward(Long episodeId, Long rewardId, AdminPartnerRewardUpdateRequest request) {
        requireEpisode(episodeId);
        EpisodePartnerReward reward = adminEpisodeRepository.findPartnerRewards(episodeId).stream()
                .filter(item -> rewardId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTNER_REWARD_NOT_FOUND", "리워드 placeholder를 찾을 수 없습니다."));
        reward.setTitle(text(request.getTitle(), reward.getTitle()));
        reward.setDescription(text(request.getDescription(), reward.getDescription()));
        reward.setRewardType(validateValue(text(request.getRewardType(), reward.getRewardType()), PARTNER_REWARD_TYPES, "INVALID_REWARD_TYPE", "지원하지 않는 리워드 타입입니다."));
        reward.setPartnerName(text(request.getPartnerName(), reward.getPartnerName()));
        reward.setLocationName(text(request.getLocationName(), reward.getLocationName()));
        reward.setLatitude(request.getLatitude() == null ? reward.getLatitude() : request.getLatitude());
        reward.setLongitude(request.getLongitude() == null ? reward.getLongitude() : request.getLongitude());
        reward.setStatus(validateValue(text(request.getStatus(), reward.getStatus()), PARTNER_REWARD_STATUSES, "INVALID_REWARD_STATUS", "리워드 상태는 DISABLED, PLANNED, ACTIVE, ENDED만 사용할 수 있습니다."));
        adminEpisodeRepository.updatePartnerReward(reward);
        return getEpisode(episodeId);
    }

    private AdminRewardPayloadValidationResponse validateRewardPayload(Long episodeId, AdminRewardPayloadValidationRequestWrapper request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<AdminRewardPayloadValidationResponse.RewardItem> rewardItems = new ArrayList<>();
        String payload = request.rewardPayload();
        if (payload == null || payload.isBlank()) {
            warnings.add("reward_payload가 비어 있습니다. 기존 reward_clue 기반 단서 저장으로만 동작합니다.");
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
                warnings.add("rewards 배열이 비어 있습니다.");
            } else {
                for (int i = 0; i < rewards.size(); i++) {
                    JsonNode reward = rewards.get(i);
                    String type = reward.path("type").asText("");
                    String value = reward.path("value").asText("");
                    Long targetId = reward.hasNonNull("targetId") ? reward.path("targetId").asLong() : null;
                    if (!REWARD_TYPES.contains(type)) {
                        errors.add("rewards[" + i + "].type이 지원되지 않습니다: " + type);
                    }
                    String targetLabel = null;
                    if (Set.of("ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE").contains(type) && value.isBlank()) {
                        errors.add("rewards[" + i + "] " + type + "에는 value가 필요합니다.");
                    }
                    if ("MEMO_UNLOCK".equals(type) && targetId == null && value.isBlank()) {
                        errors.add("rewards[" + i + "] MEMO_UNLOCK에는 targetId 또는 value가 필요합니다.");
                    }
                    if (Set.of("EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "MEMO_UNLOCK").contains(type) && targetId != null) {
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
            errors.add("reward_payload는 유효한 JSON이어야 합니다.");
        }
        return AdminRewardPayloadValidationResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .rewards(rewardItems)
                .build();
    }

    public AiEpisodeDraftResponse createAiDraft(AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request.getPlaces() == null ? List.of() : request.getPlaces();
        if (places.size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_PLACES", "사건파일형 에피소드는 최소 6개 이상의 장소가 필요합니다.");
        }

        List<String> warnings = new ArrayList<>();
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = places.get(i);
            String role = normalizeRole(place.getRole(), i, places.size());
            if (place.getVisibleElements() == null || place.getVisibleElements().isEmpty()) {
                warnings.add(place.getName() + ": visibleElements가 없어 관찰형 문제 검수가 필요합니다.");
            }
            if ("NUMBER_LOCK".equals(recommendPuzzleType(place)) && (place.getNumbers() == null || place.getNumbers().isEmpty())) {
                warnings.add(place.getName() + ": 숫자형 문제는 관리자 입력 숫자 없이는 운영 공개할 수 없습니다.");
            }
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(blank(place.getName(), "조사 장소 " + (i + 1)))
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType("FINAL".equals(role) ? "FINAL_CANDIDATE" : role)
                    .clueRole(toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription(), "현장 자료를 확인하고 사건 단서를 대조한다."))
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendPuzzleType(place))
                    .questionText(buildQuestion(place))
                    .answer(buildAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(buildRewardClue(role, i))
                    .hints(List.of("관리자 입력 자료 안에서만 단서를 찾으세요.", "visibleElements, numbers, keywords 중 문제 근거를 좁혀 보세요.", "운영 공개 전 현장 검수로 정답 근거를 확인하세요."))
                    .groundRule("관리자 입력값 기반 초안입니다. 실제 간판, 숫자, 조형물은 현장 검수 전 단정하지 않습니다.")
                    .build());
        }

        AiEpisodeDraftResponse.EpisodeDraft draft = AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("EP.NEW " + blank(request.getTheme(), "사라진 기록") + " 사건")
                .genre(blank(request.getTheme(), "야외 방탈출 / 역사 미스터리"))
                .era(blank(request.getEra(), "시대 미정"))
                .fictionSynopsis(blank(request.getArea(), "선택 지역") + "에 남겨진 기록을 따라 사라진 증거의 정체를 추적한다.")
                .finalAnswerType("EVIDENCE")
                .finalAnswer("봉인된 기록 조각")
                .finalAnswerAliases(List.of("봉인된기록조각", "기록 조각"))
                .finalQuestion("사건의 핵심 증거는 무엇인가?")
                .finalTruthSummary("수집한 단서는 하나의 조작된 기록을 가리킨다. 최종 답은 실제 역사 인물이 아니라 픽션 사건 안의 증거다.")
                .actualHistorySummary("실제 역사 해설은 관리자 검수 후 공개되어야 합니다. 게임 중에는 긴 역사 설명을 노출하지 않습니다.")
                .deductionSecretFacts(List.of("최종 정답은 실제 장소명이나 실제 인물명이 아니다.", "정답은 수집 단서 4개 이상을 조합해야 특정된다."))
                .deductionForbiddenReveals(List.of("finalAnswer", "actualFinalPlace", "realPersonAsCulprit"))
                .maxDeductionQuestions(20)
                .missions(missions)
                .suspects(List.of(
                        AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 A").displayName("붉은 장갑의 목격자").suspiciousPoint("현장 인근에서 마지막으로 목격됨").build(),
                        AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 B").displayName("사라진 기록 담당자").suspiciousPoint("증거 보관 경로를 알고 있음").build(),
                        AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 C").displayName("검은 외투의 전달자").suspiciousPoint("목적지 힌트와 연결된 이동 기록이 있음").build()))
                .evidences(missions.stream()
                        .limit(8)
                        .map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                                .title(mission.getRewardClue() + " 단서 카드")
                                .type("ANSWER_HINT".equals(mission.getClueRole()) ? "ANSWER_CLUE" : "NOTE")
                                .textSummary("퍼즐 성공 시 해금되는 사건 자료 초안입니다.")
                                .sourceMissionOrder(mission.getOrder())
                                .build())
                        .toList())
                .build();

        warnings.add("MVP 초안은 Gemini 호출 결과가 아닙니다. 관리자 입력값 기반 규칙형 초안입니다.");
        warnings.add("DB 저장 전 퍼즐 정답, reward_payload, finalPlace, forbidden reveals를 반드시 검수하세요.");
        return AiEpisodeDraftResponse.builder()
                .generatorType("MVP_RULE_BASED_DRAFT")
                .message("관리자 입력값 기반 사건파일 초안이 생성되었습니다. 아직 DB에 저장되지 않았습니다.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of("장소별 실제 관찰 요소 검수", "최종 정답이 장소명/실제 인물명과 동일하지 않은지 확인", "reward_payload로 용의자/증거 해금 연결", "검증 후 저장 API에서 DB 반영"))
                .build();
    }

    @Transactional
    public AdminEpisodeDetailResponse saveAiDraft(AiEpisodeDraftSaveRequest request) {
        AiEpisodeDraftResponse.EpisodeDraft draft = request == null ? null : request.getDraft();
        if (draft == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", "저장할 초안이 없습니다.");
        }
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null ? List.of() : draft.getMissions();
        if (missions.size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_MISSIONS", "저장하려면 최소 6개 이상의 장소가 필요합니다.");
        }
        long finalCount = missions.stream().filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType())).count();
        if (finalCount < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINAL_PLACE_REQUIRED", "실제 최종 장소가 최소 1개 필요합니다.");
        }
        String title = blank(draft.getEpisodeTitle(), "EP.NEW 사건파일");
        boolean duplicateTitle = adminEpisodeRepository.findAllEpisodes().stream().anyMatch(episode -> title.equals(episode.getTitle()));
        if (duplicateTitle) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EPISODE_TITLE", "이미 같은 제목의 에피소드가 있습니다.");
        }

        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle("AI 초안 저장본");
        episode.setEra(draft.getEra());
        episode.setGenre(draft.getGenre());
        episode.setDifficulty("NORMAL");
        episode.setEstimatedTime("90~120분");
        episode.setEstimatedDistance("현장 검수 필요");
        episode.setFictionSynopsis(draft.getFictionSynopsis());
        episode.setFinalAnswerType(blank(draft.getFinalAnswerType(), "EVIDENCE"));
        episode.setFinalAnswer(blank(draft.getFinalAnswer(), "검수필요"));
        episode.setFinalAnswerAliases(join(draft.getFinalAnswerAliases()));
        episode.setFinalQuestion(blank(draft.getFinalQuestion(), "사건의 핵심 진실은 무엇인가?"));
        episode.setFinalTruthSummary(draft.getFinalTruthSummary());
        episode.setActualHistorySummary(draft.getActualHistorySummary());
        episode.setDeductionSecretFacts(joinLines(draft.getDeductionSecretFacts()));
        episode.setDeductionForbiddenReveals(joinLines(draft.getDeductionForbiddenReveals()));
        episode.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : draft.getMaxDeductionQuestions());
        episode.setRecommendedPlayers("2~4명");
        episode.setTeamRoleGuide("지도 담당, 사건파일 담당, 문제 풀이 담당, 기록 담당으로 역할을 나누면 편합니다.");
        episode.setNoticeText("AI 초안 저장본입니다.\n운영 공개 전 현장 관찰 요소, 숫자, 표지판, 운영시간을 반드시 검수하세요.\n최종 장소와 정답이 공개 API에 노출되지 않는지 확인하세요.");
        episode.setStatus(validateValue(text(request.getStatus(), "DRAFT"), EPISODE_STATUSES, "INVALID_EPISODE_STATUS", "에피소드 상태는 DRAFT, PUBLISHED, ARCHIVED만 사용할 수 있습니다."));
        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DRAFT_REVIEW_REQUIRED", "AI 초안 저장본은 먼저 DRAFT로 저장한 뒤 검수 후 공개하세요.");
        }
        adminEpisodeRepository.insertEpisode(episode);

        Map<Integer, MissionSpot> spotByOrder = new HashMap<>();
        Map<Integer, Puzzle> puzzleByOrder = new HashMap<>();
        for (int i = 0; i < missions.size(); i++) {
            AiEpisodeDraftResponse.MissionDraft mission = missions.get(i);
            MissionSpot spot = new MissionSpot();
            spot.setEpisodeId(episode.getId());
            spot.setPlaceName(blank(mission.getPlaceName(), "조사 장소 " + (i + 1)));
            spot.setAddress(mission.getAddress());
            spot.setLatitude(mission.getLatitude() == null ? 37.5665 + (i * 0.001) : mission.getLatitude());
            spot.setLongitude(mission.getLongitude() == null ? 126.9780 + (i * 0.001) : mission.getLongitude());
            boolean finalPlace = Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType());
            String markerType = finalPlace ? "FINAL" : validateValue(blank(mission.getMarkerType(), normalizeRole(null, i, missions.size())), MARKER_TYPES, "INVALID_MARKER_TYPE", "지원하지 않는 markerType입니다.");
            spot.setMarkerType(markerType);
            spot.setFinalPlace(finalPlace);
            spot.setClueRole(finalPlace ? "FINAL_PLACE" : validateValue(blank(mission.getClueRole(), toClueRole(markerType)), CLUE_ROLES, "INVALID_CLUE_ROLE", "지원하지 않는 clueRole입니다."));
            spot.setPublicMarkerType(finalPlace ? "FINAL_CANDIDATE" : validateValue(blank(mission.getPublicMarkerType(), markerType), PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType에는 FINAL을 사용할 수 없습니다."));
            spot.setStoryText(mission.getStoryText());
            spot.setArrivalRadius(mission.getArrivalRadius() == null ? 50.0 : Math.max(10.0, mission.getArrivalRadius()));
            adminEpisodeRepository.insertSpot(spot);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            spotByOrder.put(order, spot);

            Puzzle puzzle = new Puzzle();
            puzzle.setMissionSpotId(spot.getId());
            puzzle.setPuzzleType(validateValue(blank(mission.getPuzzleType(), "OBSERVATION"), PUZZLE_TYPES, "INVALID_PUZZLE_TYPE", "지원하지 않는 puzzleType입니다."));
            puzzle.setQuestionText(blank(mission.getQuestionText(), "현장 검수 후 문제를 입력하세요."));
            puzzle.setAnswer(blank(mission.getAnswer(), "검수필요"));
            puzzle.setAnswerFormat(validateValue(blank(mission.getAnswerFormat(), "TEXT"), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "지원하지 않는 answerFormat입니다."));
            puzzle.setRewardClue(blank(mission.getRewardClue(), "검수필요"));
            puzzle.setRewardPayload(null);
            puzzle.setDifficulty("NORMAL");
            adminEpisodeRepository.insertPuzzle(puzzle);
            puzzleByOrder.put(order, puzzle);
            List<String> hints = mission.getHints() == null ? List.of() : mission.getHints();
            for (int hintIndex = 0; hintIndex < Math.min(3, hints.size()); hintIndex++) {
                adminEpisodeRepository.insertHint(puzzle.getId(), hintIndex + 1, hints.get(hintIndex));
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
                .relationToVictim(suspect.getRelationToVictim())
                .suspiciousPoint(suspect.getSuspiciousPoint())
                .unlockedByDefault(suspect.getUnlockedByDefault())
                .build();
    }

    private AdminEpisodeDetailResponse.Evidence toEvidence(CaseEvidence evidence) {
        return AdminEpisodeDetailResponse.Evidence.builder()
                .evidenceId(evidence.getId())
                .title(evidence.getTitle())
                .type(evidence.getType())
                .sourceSpotId(evidence.getSourceSpotId())
                .relatedSuspectId(evidence.getRelatedSuspectId())
                .relatedClueType(evidence.getRelatedClueType())
                .unlockedByDefault(evidence.getUnlockedByDefault())
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
        List<AiEpisodeDraftResponse.SuspectDraft> source = drafts == null || drafts.isEmpty()
                ? List.of(
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 A").displayName("붉은 장갑의 목격자").suspiciousPoint("현장 인근에서 목격됨").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 B").displayName("사라진 조수").suspiciousPoint("기록 보관 경로를 알고 있음").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 C").displayName("검은 외투의 전달자").suspiciousPoint("목적지 힌트와 연결됨").build())
                : drafts;
        List<CaseSuspect> saved = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft draft = source.get(i);
            CaseSuspect suspect = new CaseSuspect();
            suspect.setEpisodeId(episodeId);
            suspect.setAlias(blank(draft.getAlias(), "용의자 " + (i + 1)));
            suspect.setDisplayName(blank(draft.getDisplayName(), "검수 필요 인물"));
            suspect.setShortDescription("AI 초안 기반 용의자 카드입니다. 운영 공개 전 실제 인물과 혼동되지 않게 검수하세요.");
            suspect.setRelationToVictim("픽션 사건 관계");
            suspect.setSuspiciousPoint(draft.getSuspiciousPoint());
            suspect.setAlibiSummary("관리자 검수 후 입력");
            suspect.setUnlockedByDefault(i == 0);
            suspect.setDisplayOrder(i + 1);
            adminEpisodeRepository.insertSuspect(suspect);
            saved.add(suspect);
        }
        return saved;
    }

    private Map<Integer, CaseEvidence> saveDraftEvidences(Long episodeId, List<AiEpisodeDraftResponse.EvidenceDraft> drafts, Map<Integer, MissionSpot> spotByOrder, List<CaseSuspect> suspects) {
        List<AiEpisodeDraftResponse.EvidenceDraft> source = drafts == null ? List.of() : drafts;
        Map<Integer, CaseEvidence> saved = new HashMap<>();
        for (int i = 0; i < source.size(); i++) {
            AiEpisodeDraftResponse.EvidenceDraft draft = source.get(i);
            Integer missionOrder = draft.getSourceMissionOrder() == null ? i + 1 : draft.getSourceMissionOrder();
            MissionSpot spot = spotByOrder.get(missionOrder);
            CaseEvidence evidence = new CaseEvidence();
            evidence.setEpisodeId(episodeId);
            evidence.setTitle(blank(draft.getTitle(), "검수 필요 자료 " + (i + 1)));
            evidence.setType(validateValue(blank(draft.getType(), "NOTE"), EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "지원하지 않는 증거 타입입니다."));
            evidence.setTextSummary(blank(draft.getTextSummary(), "AI 초안 기반 사건자료입니다. 운영 공개 전 현장 검수 필요."));
            evidence.setSourceSpotId(spot == null ? null : spot.getId());
            evidence.setRelatedSuspectId(suspects.isEmpty() ? null : suspects.get(Math.min(i, suspects.size() - 1)).getId());
            evidence.setRelatedClueType(evidence.getType());
            evidence.setUnlockedByDefault(i < 2);
            evidence.setDisplayOrder(i + 1);
            adminEpisodeRepository.insertEvidence(evidence);
            saved.put(missionOrder, evidence);
        }
        return saved;
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
        reward.setDescription("AI 초안 저장본의 제휴/쿠폰 기능은 아직 비활성 placeholder입니다.");
        reward.setRewardType("STAMP");
        reward.setPartnerName("Operation Korea");
        reward.setLocationName("검수 후 지정");
        reward.setStatus("PLANNED");
        adminEpisodeRepository.insertPartnerReward(reward);
    }

    private String validateEvidenceTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] EVIDENCE_UNLOCK에는 targetId가 필요합니다.");
            return null;
        }
        return adminEpisodeRepository.findEvidences(episodeId).stream()
                .filter(evidence -> targetId.equals(evidence.getId()))
                .findFirst()
                .map(CaseEvidence::getTitle)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + " 증거 카드를 찾을 수 없습니다.");
                    return null;
                });
    }

    private String validateSuspectTarget(Long episodeId, Long targetId, int index, List<String> errors) {
        if (targetId == null) {
            errors.add("rewards[" + index + "] SUSPECT_UNLOCK에는 targetId가 필요합니다.");
            return null;
        }
        return adminEpisodeRepository.findSuspects(episodeId).stream()
                .filter(suspect -> targetId.equals(suspect.getId()))
                .findFirst()
                .map(CaseSuspect::getDisplayName)
                .orElseGet(() -> {
                    errors.add("rewards[" + index + "] targetId=" + targetId + " 용의자 카드를 찾을 수 없습니다.");
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_SPOT", "sourceSpotId는 해당 에피소드의 장소 ID여야 합니다.");
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RELATED_SUSPECT", "relatedSuspectId는 해당 에피소드의 용의자 ID여야 합니다.");
        }
        return id;
    }

    private void validatePublishReadiness(Episode episode) {
        List<String> errors = new ArrayList<>();
        Long episodeId = episode.getId();
        if (missing(episode.getTitle())) errors.add("제목이 필요합니다.");
        if (missing(episode.getFictionSynopsis())) errors.add("사건 시놉시스가 필요합니다.");
        if (missing(episode.getFinalAnswerType())) errors.add("최종 정답 유형이 필요합니다.");
        if (missing(episode.getFinalAnswer())) errors.add("최종 정답이 필요합니다.");
        if (missing(episode.getFinalQuestion())) errors.add("최종 질문이 필요합니다.");
        if (missing(episode.getFinalTruthSummary())) errors.add("클리어 후 진실 요약이 필요합니다.");
        if (missing(episode.getActualHistorySummary())) errors.add("클리어 후 실제 역사 해설이 필요합니다.");
        if (missing(episode.getDeductionSecretFacts())) errors.add("최종 추리 secret facts가 필요합니다.");
        if (missing(episode.getDeductionForbiddenReveals())) {
            errors.add("최종 정답 노출 금지어가 필요합니다.");
        } else if (!containsCompact(episode.getDeductionForbiddenReveals(), episode.getFinalAnswer())) {
            errors.add("정답 노출 금지어에 최종 정답을 포함해야 합니다.");
        }
        if (episode.getMaxDeductionQuestions() == null || episode.getMaxDeductionQuestions() < 1) {
            errors.add("최종 추리 질문 제한은 1 이상이어야 합니다.");
        }

        List<MissionSpot> spots = adminEpisodeRepository.findSpots(episodeId);
        if (spots.size() < 7 || spots.size() > 9) {
            errors.add("공개 에피소드는 장소 7~9개가 필요합니다.");
        }
        long startCount = spots.stream().filter(spot -> "START".equals(spot.getMarkerType())).count();
        long answerHintCount = spots.stream().filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType())).count();
        long destinationHintCount = spots.stream().filter(spot -> "DESTINATION_HINT".equals(spot.getMarkerType())).count();
        long finalPlaceCount = spots.stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count();
        long publicFinalCandidateCount = spots.stream().filter(spot -> "FINAL_CANDIDATE".equals(spot.getPublicMarkerType())).count();
        if (startCount != 1) errors.add("START 장소는 정확히 1개여야 합니다.");
        if (answerHintCount < 4) errors.add("ANSWER_HINT 장소는 최소 4개가 필요합니다.");
        if (destinationHintCount < 2) errors.add("DESTINATION_HINT 장소는 최소 2개가 필요합니다.");
        if (finalPlaceCount != 1) errors.add("실제 최종 장소는 정확히 1개여야 합니다.");
        if (publicFinalCandidateCount < 2) errors.add("최종 장소 노출 방지를 위해 FINAL_CANDIDATE 공개 마커가 최소 2개 필요합니다.");

        for (MissionSpot spot : spots) {
            if ("FINAL".equals(spot.getPublicMarkerType())) {
                errors.add("publicMarkerType에는 FINAL을 사용할 수 없습니다: " + spot.getPlaceName());
            }
            if (Boolean.TRUE.equals(spot.getFinalPlace()) && !"FINAL_CANDIDATE".equals(spot.getPublicMarkerType())) {
                errors.add("실제 최종 장소는 공개 마커가 FINAL_CANDIDATE여야 합니다: " + spot.getPlaceName());
            }
            if (missing(spot.getPlaceName()) || spot.getLatitude() == null || spot.getLongitude() == null) {
                errors.add("장소명/좌표가 누락된 장소가 있습니다.");
            }
            if (spot.getArrivalRadius() == null || spot.getArrivalRadius() < 10) {
                errors.add("도착 반경은 10m 이상이어야 합니다: " + spot.getPlaceName());
            }
            if (!missing(episode.getFinalAnswer()) && sameCompact(spot.getPlaceName(), episode.getFinalAnswer())) {
                errors.add("최종 정답은 실제 장소명과 같을 수 없습니다: " + spot.getPlaceName());
            }
            Puzzle puzzle = adminEpisodeRepository.findPuzzleBySpotId(spot.getId());
            if (puzzle == null) {
                errors.add("퍼즐이 없는 장소가 있습니다: " + spot.getPlaceName());
                continue;
            }
            if (missing(puzzle.getQuestionText())) errors.add("퍼즐 질문이 누락되었습니다: " + spot.getPlaceName());
            if (missing(puzzle.getAnswer())) errors.add("관리자용 퍼즐 정답이 누락되었습니다: " + spot.getPlaceName());
            if (missing(puzzle.getRewardClue())) errors.add("보상 단서가 누락되었습니다: " + spot.getPlaceName());
            AdminRewardPayloadValidationResponse rewardValidation = validateRewardPayload(episodeId, AdminRewardPayloadValidationRequestWrapper.of(puzzle.getRewardPayload()));
            if (!rewardValidation.isValid()) {
                errors.add("reward_payload 오류(" + spot.getPlaceName() + "): " + String.join(" / ", rewardValidation.getErrors()));
            }
            List<PuzzleHint> hints = adminEpisodeRepository.findHints(puzzle.getId());
            if (hints.size() < 3) {
                errors.add("퍼즐 힌트 3개가 필요합니다: " + spot.getPlaceName());
            }
            if (!missing(episode.getFinalAnswer()) && containsCompact(puzzle.getQuestionText(), episode.getFinalAnswer())) {
                errors.add("퍼즐 질문에 최종 정답이 직접 노출됩니다: " + spot.getPlaceName());
            }
        }

        if (adminEpisodeRepository.countSuspects(episodeId) < 3) {
            errors.add("용의자 카드는 최소 3개가 필요합니다.");
        }
        if (adminEpisodeRepository.countEvidences(episodeId) < spots.size()) {
            errors.add("증거/메모/사진 카드는 장소 수 이상 필요합니다.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_PUBLISH_NOT_READY", "공개할 수 없습니다: " + String.join(" / ", errors));
        }
    }
    private Episode requireEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);
        if (episode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "에피소드를 찾을 수 없습니다.");
        }
        return episode;
    }

    private Episode requireEditableEpisode(Long episodeId) {
        Episode episode = requireEpisode(episodeId);
        if ("PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHED_EPISODE_LOCKED", "공개 중인 에피소드는 장소/자료 개수를 변경할 수 없습니다. DRAFT 또는 ARCHIVED 상태에서 수정하세요.");
        }
        return episode;
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

    private AdminEpisodeProgressStats safeStats(Long episodeId) {
        AdminEpisodeProgressStats stats = adminEpisodeRepository.findProgressStats(episodeId);
        return stats == null ? new AdminEpisodeProgressStats() : stats;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeRole(String role, int index, int total) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (List.of("START", "ANSWER_HINT", "DESTINATION_HINT", "STORY", "FINAL_CANDIDATE", "FINAL").contains(normalized)) {
            return normalized;
        }
        if (index == 0) return "START";
        if (index >= total - 1) return "FINAL";
        if (index >= total - 3) return "DESTINATION_HINT";
        return "ANSWER_HINT";
    }

    private String toClueRole(String markerType) {
        return switch (markerType) {
            case "START" -> "START";
            case "ANSWER_HINT" -> "ANSWER_HINT";
            case "DESTINATION_HINT", "FINAL_CANDIDATE", "FINAL" -> "DESTINATION_HINT";
            default -> "STORY_CONTEXT";
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
            return "관리자가 입력한 현장 숫자 중 사건 기록과 연결되는 값을 확인하라.";
        }
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) {
            return "현장에서 " + place.getVisibleElements().get(0) + "을(를) 확인하고 사건 메모와 대조하라.";
        }
        return "관리자 메모에 적힌 현장 단서를 확인하고 키워드를 입력하라.";
    }

    private String buildAnswer(AiEpisodeDraftRequest.PlaceInput place) {
        if (place.getNumbers() != null && !place.getNumbers().isEmpty()) return place.getNumbers().get(0);
        if (place.getKeywords() != null && !place.getKeywords().isEmpty()) return place.getKeywords().get(0);
        if (place.getVisibleElements() != null && !place.getVisibleElements().isEmpty()) return place.getVisibleElements().get(0);
        return "검수필요";
    }

    private String answerFormat(AiEpisodeDraftRequest.PlaceInput place) {
        return place.getNumbers() != null && !place.getNumbers().isEmpty() ? "NUMBER" : "TEXT";
    }

    private String buildRewardClue(String role, int index) {
        return switch (role) {
            case "ANSWER_HINT" -> List.of("깨", "ㄹㅈ", "유리", "반사").get(Math.min(index - 1, 3));
            case "DESTINATION_HINT" -> index % 2 == 0 ? "붉은 벽" : "마지막 문";
            case "STORY" -> "마지막 사진";
            default -> "조사 시작";
        };
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

    private boolean sameCompact(String a, String b) {
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
                "현장 좌표와 도착 반경을 실제 모바일 GPS로 확인",
                "퍼즐에 사용된 숫자, 안내판, 표지, 조형물의 실제 존재 여부 확인",
                "최종 장소가 publicMarkerType만으로 직접 노출되지 않는지 확인",
                "최종 정답이 실제 장소명, 실제 인물명, 실제 사건명이 아닌지 확인",
                "클리어 후 실제 역사 해설이 게임 중간에 과도하게 노출되지 않는지 확인",
                "리워드/쿠폰은 실제 혜택처럼 오해되지 않도록 PLANNED 또는 DISABLED 유지"
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
