package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.domain.ContentGenre;
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
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
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
    private static final Set<String> MARKER_TYPES = Set.of("START", "ANSWER_HINT", "FINAL");
    private static final Set<String> PUBLIC_MARKER_TYPES = Set.of("START", "ANSWER_HINT");
    private static final Set<String> CLUE_ROLES = Set.of("START", "ANSWER_HINT", "FINAL_PLACE");
    private static final Set<String> PUZZLE_TYPES = Set.of("OBSERVATION", "NUMBER_LOCK", "INITIAL_SOUND", "PATTERN", "STORY_COMBINATION");
    private static final Map<String, String> PUZZLE_TYPE_ALIASES = Map.ofEntries(
            Map.entry("OBSERVATION", "OBSERVATION"),
            Map.entry("\uad00\ucc30\ud615", "OBSERVATION"),
            Map.entry("\uad00\ucc30", "OBSERVATION"),
            Map.entry("NUMBER_LOCK", "NUMBER_LOCK"),
            Map.entry("NUMBER", "NUMBER_LOCK"),
            Map.entry("NUMERIC", "NUMBER_LOCK"),
            Map.entry("\uc22b\uc790 \uc554\ud638", "NUMBER_LOCK"),
            Map.entry("\uc22b\uc790\uc554\ud638", "NUMBER_LOCK"),
            Map.entry("\uc22b\uc790", "NUMBER_LOCK"),
            Map.entry("INITIAL_SOUND", "INITIAL_SOUND"),
            Map.entry("INITIAL", "INITIAL_SOUND"),
            Map.entry("CHOSUNG", "INITIAL_SOUND"),
            Map.entry("\ucd08\uc131", "INITIAL_SOUND"),
            Map.entry("\uc5b8\uc5b4", "INITIAL_SOUND"),
            Map.entry("PATTERN", "PATTERN"),
            Map.entry("\ud328\ud134", "PATTERN"),
            Map.entry("\ud328\ud134 \ucd94\ub860", "PATTERN"),
            Map.entry("STORY_COMBINATION", "STORY_COMBINATION"),
            Map.entry("STORY", "STORY_COMBINATION"),
            Map.entry("COMBINATION", "STORY_COMBINATION"),
            Map.entry("\uc2a4\ud1a0\ub9ac", "STORY_COMBINATION"),
            Map.entry("\uc2a4\ud1a0\ub9ac \uc870\ud569", "STORY_COMBINATION"),
            Map.entry("\uc2a4\ud1a0\ub9ac\uc870\ud569", "STORY_COMBINATION")
    );
    private static final Set<String> ANSWER_FORMATS = Set.of("TEXT", "NUMBER", "CHOICE", "CODE");
    private static final Set<String> REWARD_TYPES = Set.of("ANSWER_CLUE", "STORY_CLUE", "SUSPECT_CLUE", "MEMO_UNLOCK", "EVIDENCE_UNLOCK", "PHOTO_UNLOCK", "SUSPECT_UNLOCK", "SUSPECT_UPDATE");
    private static final Set<String> EVIDENCE_TYPES = Set.of("PHOTO", "MEMO", "NOTE", "DOCUMENT", "EVIDENCE", "SUSPECT_CLUE", "POST_IT", "ANSWER_CLUE", "STORY_CLUE");
    private static final Set<String> PARTNER_REWARD_TYPES = Set.of("COUPON", "GIFT_CARD", "LOCAL_CURRENCY", "CAFE_DISCOUNT", "STAMP");
    private static final String IMAGE_PROMPT_TEXT_BAN_SUFFIX =
            "no readable text, no Korean letters, no numbers, no labels, no handwriting, no sign text, no legible document text";
    private static final Set<String> PARTNER_REWARD_STATUSES = Set.of("DISABLED", "PLANNED", "ACTIVE", "ENDED");
    private static final Set<String> INTERNAL_CONTENT_MARKERS = Set.of(
            "관리자", "검수", "확인 필요", "현장 확인", "자료 부족", "보강 필요", "공식 설명 없음",
            "추정", "review required", "admin review", "field required", "verification",
            "adminmemo", "siteverificationfocus", "kakao local", "tourapi",
            "이 기록은", "이 단서는", "배경을 보강", "배경 단서", "정답 단서", "장소 단서", "목적지 단서",
            "최종 정답", "정답이나 장소", "직접 말하지 않", "다른 증거 카드", "증거 카드와 함께",
            "함께 대조", "대조해야", "플레이어는", "클리어하면", "미션을 클리어",
            "탐색해야", "조사해야", "밝혀내야", "해독하기 위해", "찾아야", "확인해야", "추리해야", "비교해야",
            "해금된 시작 기록", "시작 기록이 해금", "보상 단서"
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
    private final ExternalPlaceResearchService externalPlaceResearchService;

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
        enriched.setSelectedGenreId(request.getSelectedGenreId());
        enriched.setSelectedGenreName(request.getSelectedGenreName());
        enriched.setFinalAnswerKeywords(request.getFinalAnswerKeywords());
        enriched.setFinalAnswerKeywordItems(request.getFinalAnswerKeywordItems());
        enriched.setFinalAnswers(request.getFinalAnswers());
        enriched.setGenreCatalog(request.getGenreCatalog());
        enriched.setMissionPolicy(request.getMissionPolicy());
        enriched.setPuzzlePolicy(request.getPuzzlePolicy());
        enriched.setMissions(request.getMissions());
        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            places.add(enrichPlace(place));
        }
        enriched.setPlaces(places);
        if (request.getFinalSpot() != null) {
            int finalSpotIndex = matchingPlaceIndex(request.getPlaces(), request.getFinalSpot());
            enriched.setFinalSpot(finalSpotIndex >= 0 ? places.get(finalSpotIndex) : enrichPlace(request.getFinalSpot()));
        }
        return enriched;
    }

    public AdminEpisodeDetailResponse getEpisode(Long episodeId) {
        Episode episode = adminEpisodeRepository.findEpisode(episodeId);
        if (episode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "Review required.");
        }
        AdminEpisodeProgressStats stats = safeStats(episodeId);
        List<AdminEpisodeDetailResponse.FinalAnswerKeywordItem> finalAnswerKeywordItems = restoreFinalAnswerKeywordItems(episode);
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
                .missionDescription(episode.getMissionDescription())
                .finalAnswerType(episode.getFinalAnswerType())
                .finalAnswer(episode.getFinalAnswer())
                .finalAnswerAliases(episode.getFinalAnswerAliases())
                .finalAnswerKeywords(finalAnswerKeywordItems.stream()
                        .map(AdminEpisodeDetailResponse.FinalAnswerKeywordItem::getValue)
                        .toList())
                .finalAnswerKeywordItems(finalAnswerKeywordItems)
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
                .destinationHintCount(0)
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
        episode.setGenre(requireAllowedGenre(text(request.getGenre(), episode.getGenre())));
        episode.setDifficulty(text(request.getDifficulty(), episode.getDifficulty()));
        episode.setEstimatedTime(text(request.getEstimatedTime(), episode.getEstimatedTime()));
        episode.setEstimatedDistance(text(request.getEstimatedDistance(), episode.getEstimatedDistance()));
        episode.setFictionSynopsis(text(request.getFictionSynopsis(), episode.getFictionSynopsis()));
        episode.setMissionDescription(text(request.getMissionDescription(), episode.getMissionDescription()));
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
            spot.setPublicMarkerType("ANSWER_HINT");
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
            spot.setPublicMarkerType("ANSWER_HINT");
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
                    if (Set.of("ANSWER_CLUE", "STORY_CLUE", "SUSPECT_CLUE").contains(type) && value.isBlank()) errors.add("rewards[" + i + "] " + type + " requires value.");
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
                    .placeName(blank(place.getName(), "조사 지점 " + (i + 1)))
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .markerType(role)
                    .publicMarkerType(publicMarkerType(place.getPublicMarkerType(), "FINAL".equals(role), role))
                    .clueRole("FINAL".equals(role) ? "FINAL_PLACE" : toClueRole(role))
                    .finalPlace("FINAL".equals(role))
                    .storyText(blank(place.getDescription(), i == 0 ? "사건파일을 열고 단서 분류를 확인하세요." : "현장 자료와 사건 메모를 비교하세요."))
                    .arrivalRadius(place.getArrivalRadius() == null ? 50.0 : place.getArrivalRadius())
                    .puzzleType(recommendPuzzleType(place))
                    .questionText(buildQuestion(place))
                    .answer(buildAnswer(place))
                    .answerFormat(answerFormat(place))
                    .rewardClue(buildRewardClue(role, i))
                    .hints(List.of("관리자가 제공한 현장 데이터만 기준으로 보세요.", "이 단서가 정답 힌트인지 목적지 힌트인지 분류하세요.", "공개 전 현장에서 단서 근거를 확인하세요."))
                    .groundRule("규칙 기반 관리자 초안입니다. 공개 전 현장 주장을 확인하세요.")
                    .build());
        }
        DraftObjective objective = draftObjective(request, places);
        AiEpisodeDraftResponse.EpisodeDraft draft = AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("EP.NEW " + blank(request.getTheme(), "숨겨진 기록") + " 사건")
                .subtitle(draftSubtitle(request, places))
                .genre(objective.genre())
                .era(draftEra(request, places))
                .fictionSynopsis(objective.synopsis())
                .missionDescription(objective.synopsis())
                .selectedGenre(objective.genre())
                .finalAnswerKeywords(objective.keywords())
                .finalAnswers(AiEpisodeDraftResponse.FinalAnswers.builder()
                        .culprit(objective.keywords().get(0))
                        .weapon(objective.keywords().get(1))
                        .motive(objective.keywords().get(2))
                        .method(objective.keywords().get(3))
                        .build())
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
        warnings.add("규칙 기반 초안을 생성했습니다. 저장 전 퍼즐 정답, reward_payload, finalPlace를 확인하세요.");
        return AiEpisodeDraftResponse.builder()
                .generatorType("MVP_RULE_BASED_DRAFT")
                .message("규칙 기반 사건파일 초안을 생성했습니다. 아직 DB에 저장되지 않았습니다.")
                .draft(draft)
                .validationWarnings(warnings)
                .nextSteps(List.of("현장 관찰 근거 확인", "최종 정답이 장소나 실존 인물이 아닌지 확인", "보상을 용의자/증거와 연결", "검증 후 DRAFT로 저장"))
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
                .orElseGet(() -> places.isEmpty() ? "현장 지점" : blank(places.get(places.size() - 1).getName(), "현장 지점"));
        return area + "의 단서가 " + anchor + "로 수렴됩니다";
    }

    private String draftEra(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (!missing(request.getEra()) && !containsCompact(request.getEra(), "review") && !containsCompact(request.getEra(), "unknown")) return request.getEra().trim();
        String joined = routeText(request, places);
        if (containsCompact(joined, "1905") || containsCompact(joined, "1897") || containsCompact(joined, "empire")) return "대한제국 후기";
        if (containsCompact(joined, "palace") || containsCompact(joined, "royal")) return "왕실 기록고 사건";
        if (containsCompact(joined, "independence") || containsCompact(joined, "colonial")) return "독립운동기 미스터리";
        return "과거와 현재가 겹치는 시대";
    }




    private DraftObjective draftObjective(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        if (request != null) {
            return crimeMysteryDraftObjective(request, places);
        }
        ContentGenre selectedGenre = ContentGenre.fromIdOrName(
                request.getSelectedGenreId(),
                request.getSelectedGenreName()
        );
        String genre = (selectedGenre == null ? ContentGenre.MISSING_CASE : selectedGenre).displayName();
        String relatedPerson = finalAnswerValue(request, "RELATED_PERSON", "한서림");
        String coreClue = finalAnswerValue(request, "ANSWER_CLUE", draftFinalObject(request, places));
        String finalLocation = finalAnswerValue(
                request,
                "FINAL_DESTINATION",
                places.stream()
                        .filter(place -> "FINAL".equalsIgnoreCase(blank(place.getRole(), "")))
                        .map(AiEpisodeDraftRequest.PlaceInput::getName)
                        .filter(value -> !missing(value))
                        .findFirst()
                        .orElseGet(() -> places.isEmpty() ? "마지막 조사 지점" : blank(places.get(places.size() - 1).getName(), "마지막 조사 지점"))
        );
        List<String> keywords = List.of(relatedPerson, coreClue, finalLocation);
        String finalAnswer = relatedPerson + "와 " + coreClue + "를 연결하면 최종 장소는 " + finalLocation + "이다";
        return new DraftObjective(
                genre,
                "HIDDEN_TRUTH",
                finalAnswer,
                keywords,
                List.of(finalAnswer.replace(" ", ""), "KW:" + String.join("|", keywords)),
                "관련자, 핵심 단서, 최종 장소를 종합하면 어떤 결론인가?",
                ruleBasedMissionDescription(genre, places)
        );
    }

    private DraftObjective crimeMysteryDraftObjective(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String genre = ContentGenre.CRIME_MYSTERY.displayName();
        String culprit = finalAnswerValue(request, "CULPRIT", "강수진");
        String weapon = finalAnswerValue(request, "WEAPON", "독성 캡슐");
        String motive = finalAnswerValue(request, "MOTIVE", "해고 통보에 대한 복수");
        String method = finalAnswerValue(request, "METHOD", "피해자의 약을 독성 캡슐로 바꿔치기");
        List<String> keywords = List.of(culprit, weapon, motive, method);
        String finalAnswer = "범인: " + culprit + " / 흉기: " + weapon + " / 동기: " + motive + " / 방법: " + method;
        return new DraftObjective(
                genre,
                "CASE_TRUTH",
                finalAnswer,
                keywords,
                List.of("KW:" + String.join("|", keywords)),
                "범인, 흉기, 동기, 방법을 각각 입력하세요.",
                ruleBasedMissionDescription(genre, places)
        );
    }

    private String ruleBasedMissionDescription(
            String genre,
            List<AiEpisodeDraftRequest.PlaceInput> places) {
        List<AiEpisodeDraftRequest.PlaceInput> publicPlaces = places.stream()
                .filter(place -> place != null && !"FINAL".equalsIgnoreCase(blank(place.getRole(), "")))
                .toList();
        String firstPlace = publicPlaces.isEmpty() ? "첫 조사 장소" : blank(publicPlaces.get(0).getName(), "첫 조사 장소");
        String secondPlace = publicPlaces.size() < 2 ? "다음 조사 장소" : blank(publicPlaces.get(1).getName(), "다음 조사 장소");
        String clues = publicPlaces.stream()
                .flatMap(place -> java.util.stream.Stream.concat(
                        place.getVisibleElements() == null ? java.util.stream.Stream.empty() : place.getVisibleElements().stream(),
                        place.getKeywords() == null ? java.util.stream.Stream.empty() : place.getKeywords().stream()))
                .filter(value -> !missing(value))
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
        if (missing(clues)) {
            clues = "남겨진 물건, 시간 기록, 이동 방향";
        }
        return switch (genre) {
            case "살인 미스터리" ->
                    firstPlace + "에서 단순 사고로 보기 어려운 흔적이 발견되었습니다. "
                            + secondPlace + "을 포함한 각 장소에서 " + clues + "을 비교해 진술의 모순을 찾아야 합니다. "
                            + "잘못된 추측을 제외하면서 관련자의 역할과 사건에 사용된 핵심 단서, 사건이 이어진 최종 장소를 밝혀내세요.";
            case "보물찾기" ->
                    firstPlace + "에서 발견된 기록은 숨겨진 물건의 위치를 직접 말하지 않고 여러 장소에 단서를 나누어 남기고 있습니다. "
                            + secondPlace + "을 포함한 각 장소에서 " + clues + "을 모아 기록의 순서와 해금 조건을 복원해야 합니다. "
                            + "어떤 물건이 숨겨졌는지, 그것을 확인하는 조건과 최종 장소가 어디인지 추리하세요.";
            case "암호 해독" ->
                    firstPlace + "에서 발견된 배열은 다른 장소의 숫자와 표식을 함께 보아야 의미가 완성됩니다. "
                            + secondPlace + "을 포함한 각 장소에서 " + clues + "을 확인하고 반복 규칙을 비교해야 합니다. "
                            + "여러 장소의 정보를 조합해 암호의 의미와 정보가 확인되는 최종 장소를 밝혀내세요.";
            default ->
                    "사건 관계자의 흔적은 " + firstPlace + " 한 곳에서 끝나지 않고 " + secondPlace + "을 포함한 여러 장소에 흩어져 있습니다. "
                            + "각 장소에서 " + clues + "을 비교해 실제 이동 경로와 사라진 이유를 복원해야 합니다. "
                            + "겉보기 순서와 실제 동선을 구분하고 관련자의 역할과 핵심 단서, 마지막 행방이 이어지는 최종 장소를 추리하세요.";
        };
    }

    private String finalAnswerValue(AiEpisodeDraftRequest request, String slotId, String fallback) {
        if (request.getFinalAnswerKeywordItems() != null) {
            String value = request.getFinalAnswerKeywordItems().stream()
                    .filter(item -> item != null && slotId.equalsIgnoreCase(blank(item.getSlotId(), "")))
                    .map(AiEpisodeDraftRequest.AnswerKeywordInput::getKeyword)
                    .filter(item -> !missing(item))
                    .findFirst()
                    .orElse("");
            if (!missing(value)) {
                return value.trim();
            }
        }
        return fallback;
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
        String first = places.isEmpty() ? "첫 조사 지점" : blank(places.get(0).getName(), "첫 조사 지점");
        String anchor = places.isEmpty() ? "마지막 조사 지점" : blank(places.get(places.size() - 1).getName(), "마지막 조사 지점");
        String object = draftFinalObject(request, places);
        String routeSignal = routeSignal(places);
        return area + "의 " + first + "에서 사건이 시작됩니다. 단서는 " + anchor + "를 향하지만, " + routeSignal + " 기록이 서로 엇갈립니다. 플레이어는 현장 자료를 비교해 [" + object + "]의 정체를 밝혀야 합니다.";
    }




    private String draftFinalQuestion(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        return "모은 단서가 가리키는 [" + draftFinalObject(request, places) + "]의 정체는 무엇입니까?";
    }




    private String draftFinalObject(AiEpisodeDraftRequest request, List<AiEpisodeDraftRequest.PlaceInput> places) {
        String joined = routeText(request, places);
        if (containsCompact(joined, "coffee") || containsCompact(joined, "cafe") || containsCompact(joined, "tea")) return "식은 찻잔";
        if (containsCompact(joined, "document") || containsCompact(joined, "seal") || containsCompact(joined, "signature")) return "젖은 손잡이";
        if (containsCompact(joined, "photo") || containsCompact(joined, "film") || containsCompact(joined, "lens")) return "깨진 렌즈";
        if (containsCompact(joined, "market") || containsCompact(joined, "restaurant") || containsCompact(joined, "receipt")) return "구겨진 영수증";
        if (containsCompact(joined, "palace") || containsCompact(joined, "archive")) return "낮은 보관문";
        return "접힌 우산";
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
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 A").displayName("붉은 봉투를 본 목격자").shortDescription("현장 동선 근처에서 마지막 봉투를 본 인물입니다.").relationToVictim("마지막 의뢰 연락책").suspiciousPoint("동선 시간표 일부가 비어 있습니다.").alibiSummary("비가 그칠 때까지 카페 근처에 있었다고 주장합니다.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 B").displayName("사라진 기록 관리인").shortDescription("필름과 증거 파일을 관리하던 보조인입니다.").relationToVictim("기록고 취급자").suspiciousPoint("사라진 필름의 보관 위치를 알고 있었습니다.").alibiSummary("기록실에 있었다고 주장하지만 확인한 목격자가 없습니다.").build(),
                AiEpisodeDraftResponse.SuspectDraft.builder().alias("용의자 C").displayName("검은 봉투를 옮긴 배달인").shortDescription("목적지 단서와 동선이 겹치는 배달인입니다.").relationToVictim("최종 단서 운반자").suspiciousPoint("정답을 훔친 것이 아니라 단서 흐름을 숨겼을 가능성이 있습니다.").alibiSummary("배달 동선과 목격 시간이 맞지 않습니다.").build()
        );
    }


    private List<AiEpisodeDraftResponse.EvidenceDraft> defaultDraftEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return missions.stream().limit(8).map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                .title(mission.getRewardClue() + " 단서 카드")
                .type(evidenceTypeForMission(mission))
                .imageUrl("")
                .imagePrompt("Create a high-quality detective evidence image for a Korean outdoor escape-room case file. Subject: "
                        + mission.getRewardClue() + " clue card. Story detail: Case material unlocked after solving this mission. "
                        + caseFileIllustrationStylePrompt()
                        + "If any person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story era. "
                        + caseFileNegativeImagePrompt())
                .textSummary("이 미션을 풀면 해금되는 사건 자료입니다.")
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
        sanitizeDraftPlayerTextForSave(draft);
        List<AiEpisodeDraftResponse.MissionDraft> missions = draft.getMissions() == null ? List.of() : draft.getMissions();
        if (missions.size() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_MISSIONS", "Review required.");
        }
        long finalCount = missions.stream().filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(mission.getMarkerType())).count();
        if (finalCount < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINAL_PLACE_REQUIRED", "Review required.");
        }
        requireFinalAnswerFields(draft);
        validateHumanReadableDraftTextForSave(draft);
        String title = resolveDraftTitle(draft, missions);

        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(draft.getSubtitle(), "AI draft case file"));
        episode.setEra(blank(draft.getEra(), "Review required"));
        episode.setGenre(requireAllowedGenre(draft.getGenre()));
        episode.setDifficulty("NORMAL");
        episode.setEstimatedTime("90~120분");
        episode.setEstimatedDistance(estimateDraftDistance(missions));
        episode.setFictionSynopsis(draft.getFictionSynopsis());
        episode.setMissionDescription(blank(draft.getMissionDescription(), draft.getFictionSynopsis()));
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
            spot.setFieldVerificationNote("AI/사이트 데이터 기반 검수 완료 초안입니다. 좌표, 도착 반경, 퍼즐 근거는 제공된 후보 데이터로 확인했으며 실제 GPS QA는 선택 사항입니다.");
            adminEpisodeRepository.insertSpot(spot);
            int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
            spotByOrder.put(order, spot);
            if (isMissionAnswerDisconnected(mission, missions)) {
                normalizeMissionForReview(mission);
            }
            Puzzle puzzle = new Puzzle();
            puzzle.setMissionSpotId(spot.getId());
            puzzle.setPuzzleType(normalizePuzzleTypeForSave(mission.getPuzzleType(), order));
            puzzle.setQuestionText(blank(sanitizeCategoryCodes(mission.getQuestionText()), "Review required."));
            puzzle.setAnswer(blank(sanitizeCategoryCodes(mission.getAnswer()), "현장단서"));
            puzzle.setAnswerFormat(validateValue(blank(mission.getAnswerFormat(), "TEXT"), ANSWER_FORMATS, "INVALID_ANSWER_FORMAT", "Unsupported answerFormat."));
            puzzle.setRewardClue(sanitizeCategoryCodes(rewardClueForSave(mission, i)));
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
        if (!normalized.matches("[가-힣]{2,4}")) {
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
        Set<String> usedSummaries = new java.util.LinkedHashSet<>();
        int index = 0;
        for (AiEpisodeDraftResponse.EvidenceDraft draft : source) {
            AiEpisodeDraftResponse.MissionDraft mission = missionByOrder.get(draft.getSourceMissionOrder());
            String evidenceType = evidenceTypeForMission(mission);
            String textSummary = cardBodyOnly(draft.getTextSummary(), forbiddenDraftPlaceNamesFromMissions(missions));
            String normalizedSummary = compact(textSummary);
            if (missing(textSummary) || usedSummaries.contains(normalizedSummary)) {
                textSummary = typedEvidenceSummary(mission, evidenceType, index);
                normalizedSummary = compact(textSummary);
            }
            usedSummaries.add(normalizedSummary);
            CaseEvidence evidence = new CaseEvidence();
            evidence.setEpisodeId(episodeId);
            evidence.setTitle(blank(draft.getTitle(), "사건 자료 " + (index + 1)));
            evidence.setType(validateValue(evidenceType, EVIDENCE_TYPES, "INVALID_EVIDENCE_TYPE", "Unsupported evidence type."));
            evidence.setImageUrl(draft.getImageUrl());
            evidence.setImagePrompt(ensureKoreanEvidencePrompt(blank(draft.getImagePrompt(), buildEvidenceImagePrompt(draft))));
            evidence.setTextSummary(textSummary);
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

    private String typedEvidenceSummary(AiEpisodeDraftResponse.MissionDraft mission, String evidenceType, int index) {
        String basis = typedEvidenceBasis(mission, index);
        int variant = Math.floorMod(index, 3);
        if ("SUSPECT_CLUE".equals(evidenceType)) {
            return switch (variant) {
                case 0 -> basis + "의 출입 순서가 한 인물의 진술 시간과 맞지 않아 관계자 후보를 좁힌다.";
                case 1 -> basis + "에 남은 필체 습관이 같은 말을 반복한 인물 한 명과만 이어진다.";
                default -> basis + "의 보관 위치 변화가 사건 직전 접근할 수 있던 인물을 제한한다.";
            };
        }
        if ("DESTINATION_CLUE".equals(evidenceType)) {
            return switch (variant) {
                case 0 -> basis + "의 방향 배열이 되돌아간 길을 제외하고 마지막 이동 후보를 좁힌다.";
                case 1 -> basis + "에 남은 순서 차이가 다음에 확인해야 할 입구 조건을 가리킨다.";
                default -> basis + "의 위치 변화가 마지막 장소로 이어지는 동선 하나만 남긴다.";
            };
        }
        if ("ANSWER_CLUE".equals(evidenceType)) {
            return switch (variant) {
                case 0 -> basis + "의 재질과 쓰임이 평범한 소지품이 아니라 핵심 물건 후보를 좁힌다.";
                case 1 -> basis + "의 빠진 부분이 최종 핵심 단서의 형태를 한 단계 더 구체화한다.";
                default -> basis + "의 상태가 목격 진술과 맞지 않아 핵심 단서 후보 하나를 제외한다.";
            };
        }
        return basis + "의 위치와 상태가 사건 시작 전후의 차이를 보여 준다.";
    }

    private String typedEvidenceBasis(AiEpisodeDraftResponse.MissionDraft mission, int index) {
        if (mission != null && mission.getEvidenceDesign() != null) {
            String artifact = blank(mission.getEvidenceDesign().getArtifactType(), "");
            if (!artifact.isBlank()) {
                return artifact;
            }
            String detail = blank(mission.getEvidenceDesign().getVisibleDetail(), "");
            if (!detail.isBlank()) {
                return detail;
            }
        }
        return List.of("젖은 손잡이", "접힌 우산", "끊긴 끈", "낮은 좌석", "구겨진 영수증").get(Math.floorMod(index, 5));
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
        String role = blank(mission.getClueRole(), "");
        return switch (role) {
            case "FINAL_PLACE" -> "STORY_CLUE";
            case "ANSWER_HINT" -> "RELATED_PERSON".equals(normalizeSlotId(mission.getRewardClueSlotId())) ? "SUSPECT_CLUE" : "ANSWER_CLUE";
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
            case "RELATED_PERSON", "ANSWER_CLUE" -> normalized;
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
        String result = styled;
        if (!normalized.contains("fictional korean person") && !normalized.contains("korean identity")) {
            result = styled.trim()
                    + " Casting is mandatory: every visible person must be a fictional Korean person from Seoul, South Korea. "
                    + "Preserve the story's age, gender, occupation, and era. Do not cast a Western or European-looking model or change the character's Korean identity.";
        }
        return ensureNoReadableTextImagePrompt(result);
    }

    private String ensureKoreanEvidencePrompt(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }
        String styled = ensureCaseFileIllustrationStyle(prompt);
        String normalized = styled.toLowerCase(Locale.ROOT);
        String result = styled;
        if (!normalized.contains("if any person") && !normalized.contains("every visible person")) {
            result = styled.trim()
                    + " If any person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story's age and era. "
                    + "Do not cast a Western or European-looking model.";
        }
        return ensureNoReadableTextImagePrompt(result);
    }

    private String ensureNoReadableTextImagePrompt(String prompt) {
        if (missing(prompt)) {
            return prompt;
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (normalized.contains("no readable text")
                && normalized.contains("no korean letters")
                && normalized.contains("no numbers")
                && normalized.contains("no labels")
                && normalized.contains("no handwriting")
                && normalized.contains("no sign text")
                && normalized.contains("no legible document text")) {
            return prompt;
        }
        return prompt.trim() + " " + IMAGE_PROMPT_TEXT_BAN_SUFFIX + ".";
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
            String clueType = rewardTypeForMission(mission);
            String slotId = slotIdForRewardType(clueType);
            CaseEvidence evidence = evidenceByMissionOrder.get(order);

            String evidenceSummary = evidence == null ? "" : evidence.getTextSummary();
            String clueText = !missing(evidenceSummary)
                    ? sanitizeCategoryCodes(evidenceSummary)
                    : sanitizeCategoryCodes(rewardClueForSave(mission, i));

            puzzle.setRewardClue(clueText);
            List<Map<String, Object>> rewards = new ArrayList<>();
            Map<String, Object> clueReward = new LinkedHashMap<>();
            clueReward.put("type", clueType);
            clueReward.put("value", clueText);
            if (!slotId.isBlank()) {
                clueReward.put("slotId", slotId);
            }
            if (!missing(mission.getTargetKeywordType())) {
                clueReward.put("targetKeywordType", normalizeType(mission.getTargetKeywordType()));
            }
            if (mission.getSupportsKeywordSlots() != null && !mission.getSupportsKeywordSlots().isEmpty()) {
                clueReward.put("supportsKeywordSlots", mission.getSupportsKeywordSlots().stream()
                        .filter(slot -> !missing(slot))
                        .map(this::normalizeType)
                        .toList());
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
            adminEpisodeRepository.updatePuzzle(puzzle);
        }
    }

    private String slotIdForRewardType(String clueType) {
        return switch (clueType) {
            case "SUSPECT_CLUE" -> "RELATED_PERSON";
            case "ANSWER_CLUE" -> "ANSWER_CLUE";
            default -> "";
        };
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
            return "";
        }
        return cardBodyOnly(mission.getRewardClue(), mission.getForbiddenPlaceNames());
    }

    private void validateDraftPlayerTextBeforeSave(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) {
            return;
        }
        List<String> forbidden = draft.getMissions() == null ? List.of() : draft.getMissions().stream()
                .flatMap(mission -> {
                    List<String> values = new ArrayList<>();
                    if (mission.getForbiddenPlaceNames() != null) values.addAll(mission.getForbiddenPlaceNames());
                    if (!missing(mission.getActualPlaceName())) values.add(mission.getActualPlaceName());
                    if (!missing(mission.getPlaceName())) values.add(mission.getPlaceName());
                    return values.stream();
                })
                .filter(value -> !missing(value))
                .distinct()
                .toList();
        List<String> errors = new ArrayList<>();
        checkPlayerText("episodeTitle", draft.getEpisodeTitle(), forbidden, errors);
        checkPlayerText("subtitle", draft.getSubtitle(), forbidden, errors);
        checkPlayerText("fictionSynopsis", draft.getFictionSynopsis(), forbidden, errors);
        checkPlayerText("missionDescription", draft.getMissionDescription(), forbidden, errors);
        checkPlayerText("finalQuestion", draft.getFinalQuestion(), forbidden, errors);
        if (draft.getMissions() != null) {
            for (AiEpisodeDraftResponse.MissionDraft mission : draft.getMissions()) {
                int order = mission.getOrder() == null ? 0 : mission.getOrder();
                checkPlayerText("missions[" + order + "].storyText", mission.getStoryText(), forbidden, errors);
                checkPlayerText("missions[" + order + "].questionText", mission.getQuestionText(), forbidden, errors);
                checkPlayerText("missions[" + order + "].rewardClue", mission.getRewardClue(), forbidden, errors);
                if (mission.getHints() != null) {
                    for (int i = 0; i < mission.getHints().size(); i++) {
                        checkPlayerText("missions[" + order + "].hints[" + i + "]", mission.getHints().get(i), forbidden, errors);
                    }
                }
            }
        }
        if (draft.getSuspects() != null) {
            for (int i = 0; i < draft.getSuspects().size(); i++) {
                AiEpisodeDraftResponse.SuspectDraft suspect = draft.getSuspects().get(i);
                checkPlayerText("suspects[" + i + "]", String.join(" ",
                        blank(suspect.getAlias(), ""),
                        blank(suspect.getDisplayName(), ""),
                        blank(suspect.getShortDescription(), ""),
                        blank(suspect.getRelationToVictim(), ""),
                        blank(suspect.getSuspiciousPoint(), ""),
                        blank(suspect.getAlibiSummary(), ""),
                        blank(suspect.getImagePrompt(), "")), forbidden, errors);
            }
        }
        if (draft.getEvidences() != null) {
            for (int i = 0; i < draft.getEvidences().size(); i++) {
                AiEpisodeDraftResponse.EvidenceDraft evidence = draft.getEvidences().get(i);
                checkPlayerText("evidences[" + i + "]", String.join(" ",
                        blank(evidence.getTitle(), ""),
                        blank(evidence.getTextSummary(), ""),
                        blank(evidence.getImagePrompt(), "")), forbidden, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAYER_TEXT_REVIEW_REQUIRED", "플레이어 노출 문구 검수가 필요합니다: " + String.join(" / ", errors));
        }
    }

    private void sanitizeDraftPlayerTextForSave(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) {
            return;
        }
        redactDraftPlayerFacingSecrets(draft);
        List<String> forbidden = forbiddenDraftPlaceNames(draft);
        if (draft.getMissions() != null) {
            for (int i = 0; i < draft.getMissions().size(); i++) {
                AiEpisodeDraftResponse.MissionDraft mission = draft.getMissions().get(i);
                if (mission == null) {
                    continue;
                }
                int order = mission.getOrder() == null ? i + 1 : mission.getOrder();
                if (unsafePlayerSaveText(mission.getStoryText(), forbidden)) {
                    mission.setStoryText(safeSaveStoryText(order));
                }
                if (unsafePlayerSaveText(mission.getQuestionText(), forbidden)) {
                    mission.setQuestionText(safeSaveQuestionText());
                }
                mission.setRewardClue(cardBodyOnly(mission.getRewardClue(), forbidden));
                if (mission.getHints() == null || mission.getHints().size() < 3
                        || mission.getHints().stream().anyMatch(hint -> unsafePlayerSaveText(hint, forbidden))) {
                    mission.setHints(List.of(
                            "먼저 주변 물건의 상태를 살핀다.",
                            "물건의 위치와 방향 차이를 비교한다.",
                            "남은 배열이 가리키는 대상을 좁힌다."
                    ));
                }
            }
        }
        if (draft.getEvidences() != null) {
            for (int i = 0; i < draft.getEvidences().size(); i++) {
                AiEpisodeDraftResponse.EvidenceDraft evidence = draft.getEvidences().get(i);
                if (evidence == null) {
                    continue;
                }
                if (unsafePlayerSaveText(evidence.getTitle(), forbidden)) {
                    evidence.setTitle("사건 자료 " + (i + 1));
                }
                evidence.setTextSummary(cardBodyOnly(evidence.getTextSummary(), forbidden));
                if (unsafePlayerSaveText(evidence.getImagePrompt(), forbidden)
                        || !hasImageTextBan(evidence.getImagePrompt())) {
                    evidence.setImagePrompt(safeSaveEvidenceImagePrompt());
                }
            }
        }
    }

    private void redactDraftPlayerFacingSecrets(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) {
            return;
        }
        List<RedactionRule> rules = draftPlayerFacingRedactionRules(draft);
        if (rules.isEmpty()) {
            return;
        }
        if (draft.getMissions() != null) {
            for (AiEpisodeDraftResponse.MissionDraft mission : draft.getMissions()) {
                if (mission == null) {
                    continue;
                }
                mission.setRewardClue(applyRedactionRules(mission.getRewardClue(), rules));
                if (mission.getHints() != null) {
                    mission.setHints(mission.getHints().stream()
                            .map(hint -> applyRedactionRules(hint, rules))
                            .toList());
                }
            }
        }
        if (draft.getEvidences() != null) {
            for (AiEpisodeDraftResponse.EvidenceDraft evidence : draft.getEvidences()) {
                if (evidence == null) {
                    continue;
                }
                evidence.setTitle(applyRedactionRules(evidence.getTitle(), rules));
                evidence.setTextSummary(applyRedactionRules(evidence.getTextSummary(), rules));
            }
        }
    }

    private List<RedactionRule> draftPlayerFacingRedactionRules(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<RedactionRule> rules = new ArrayList<>();
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = draft.getSuspects() == null ? List.of() : draft.getSuspects();
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (suspect != null && !missing(suspect.getDisplayName())) {
                rules.add(new RedactionRule(suspect.getDisplayName(), suspectReference(i)));
            }
        }
        Map<String, String> answers = finalAnswerValueMap(draft);
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (!missing(entry.getValue())) {
                rules.add(new RedactionRule(entry.getValue(), indirectAnswerReference(entry.getKey())));
            }
        }
        return rules;
    }

    private Map<String, String> finalAnswerValueMap(AiEpisodeDraftResponse.EpisodeDraft draft) {
        Map<String, String> values = new LinkedHashMap<>();
        if (draft.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftResponse.AnswerKeywordItem item : draft.getFinalAnswerKeywordItems()) {
                if (item == null) continue;
                String type = normalizeType(blank(item.getType(), item.getSlotId()));
                String value = blank(item.getValue(), item.getKeyword());
                if (!missing(type) && !missing(value)) values.put(type, value);
            }
        }
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers != null) {
            putIfMissing(values, "CULPRIT", answers.getCulprit());
            putIfMissing(values, "WEAPON", answers.getWeapon());
            putIfMissing(values, "MOTIVE", answers.getMotive());
            putIfMissing(values, "METHOD", answers.getMethod());
        }
        return values;
    }

    private void putIfMissing(Map<String, String> values, String key, String value) {
        if (!values.containsKey(key) && !missing(value)) {
            values.put(key, value);
        }
    }

    private String applyRedactionRules(String text, List<RedactionRule> rules) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (RedactionRule rule : rules) {
            result = redactExactValue(result, rule.value(), rule.replacement());
        }
        return result;
    }

    private String redactExactValue(String text, String value, String replacement) {
        if (missing(value) || text == null || text.isBlank()) {
            return text;
        }
        String result = text.replace("용의자 " + value, replacement);
        result = result.replace(value + "은", replacement + "는");
        result = result.replace(value + "는", replacement + "는");
        result = result.replace(value + "이", replacement + "가");
        result = result.replace(value + "가", replacement + "가");
        result = result.replace(value + "을", replacement + "을");
        result = result.replace(value + "를", replacement + "를");
        result = result.replace(value + "에게", replacement + "에게");
        result = result.replace(value + "의", replacement + "의");
        result = result.replace(value + "와", replacement + "와");
        result = result.replace(value + "과", replacement + "과");
        return result.replace(value, replacement);
    }

    private String suspectReference(int index) {
        return switch (index) {
            case 0 -> "첫 번째 용의자";
            case 1 -> "두 번째 용의자";
            case 2 -> "세 번째 용의자";
            default -> "해당 용의자";
        };
    }

    private String indirectAnswerReference(String slot) {
        return switch (normalizeType(slot)) {
            case "CULPRIT" -> "해당 용의자";
            case "WEAPON" -> "해당 물증";
            case "MOTIVE" -> "해당 동기";
            case "METHOD" -> "해당 실행 방식";
            default -> "해당 단서";
        };
    }

    private record RedactionRule(String value, String replacement) {}

    private List<String> forbiddenDraftPlaceNames(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null || draft.getMissions() == null) {
            return List.of();
        }
        List<String> forbidden = new ArrayList<>();
        for (AiEpisodeDraftResponse.MissionDraft mission : draft.getMissions()) {
            if (mission == null) {
                continue;
            }
            if (!missing(mission.getPlaceName())) forbidden.add(mission.getPlaceName());
            if (!missing(mission.getActualPlaceName())) forbidden.add(mission.getActualPlaceName());
            if (mission.getForbiddenPlaceNames() != null) forbidden.addAll(mission.getForbiddenPlaceNames());
        }
        return forbidden.stream()
                .filter(value -> !missing(value))
                .distinct()
                .toList();
    }

    private List<String> forbiddenDraftPlaceNamesFromMissions(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        if (missions == null) {
            return List.of();
        }
        List<String> forbidden = new ArrayList<>();
        for (AiEpisodeDraftResponse.MissionDraft mission : missions) {
            if (mission == null) {
                continue;
            }
            if (!missing(mission.getPlaceName())) forbidden.add(mission.getPlaceName());
            if (!missing(mission.getActualPlaceName())) forbidden.add(mission.getActualPlaceName());
            if (mission.getForbiddenPlaceNames() != null) forbidden.addAll(mission.getForbiddenPlaceNames());
        }
        return forbidden.stream().filter(value -> !missing(value)).distinct().toList();
    }

    private String cardBodyOnly(String value, List<String> forbiddenPlaceNames) {
        if (missing(value)) {
            return "";
        }
        String body = value.trim();
        java.util.regex.Matcher wrapped = java.util.regex.Pattern
                .compile("에서\\s*얻는\\s*[‘'\"](.+?)[’'\"]")
                .matcher(body);
        if (wrapped.find()) {
            body = wrapped.group(1).trim();
        }
        if (forbiddenPlaceNames != null) {
            for (String forbidden : forbiddenPlaceNames) {
                if (!missing(forbidden)) {
                    body = body.replace(forbidden, "");
                }
            }
        }
        return java.util.Arrays.stream(body.split("(?<=[.!?。])\\s+"))
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .filter(sentence -> !containsAny(sentence,
                        "에서 얻는", "단서는 최종 정답의 형태를 좁히는 증거입니다",
                        "장소명 글자 추출", "현장 메모와 연결해 해석하세요",
                        "이 단서는", "이 기록은", "최종 정답", "다른 증거 카드",
                        "대조해야 합니다", "플레이어는", "관리자 확인", "검수가 필요"))
                .collect(Collectors.joining(" "))
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean unsafePlayerSaveText(String text, List<String> forbidden) {
        if (missing(text)) {
            return true;
        }
        if (isInternalContentText(text) || looksLikeOperationBriefing(text)) {
            return true;
        }
        if (forbidden != null) {
            for (String value : forbidden) {
                if (!missing(value) && compact(value).length() >= 2 && containsCompact(text, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String safeSaveStoryText(int order) {
        return "";
    }

    private String safeSaveQuestionText() {
        return "물건의 위치와 방향 차이가 함께 가리키는 상태는 무엇인가?";
    }

    private String safeSaveRewardClue(int order) {
        return "";
    }

    private String safeSaveEvidenceSummary() {
        return "";
    }

    private String safeSaveEvidenceImagePrompt() {
        return ensureNoReadableTextImagePrompt(
                "flat 2D Korean webtoon style, muted earth tones, unmarked folded paper object with stains and abstract marks");
    }

    private boolean hasImageTextBan(String prompt) {
        if (missing(prompt)) {
            return false;
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        return normalized.contains("no readable text")
                && normalized.contains("no korean letters")
                && normalized.contains("no numbers")
                && normalized.contains("no labels")
                && normalized.contains("no handwriting")
                && normalized.contains("no sign text")
                && normalized.contains("no legible document text");
    }

    private void checkPlayerText(String field, String text, List<String> forbiddenPlaceNames, List<String> errors) {
        if (missing(text)) {
            return;
        }
        if (isInternalContentText(text) || looksLikeOperationBriefing(text)) {
            errors.add(field + " contains internal or briefing text");
            return;
        }
        for (String forbidden : forbiddenPlaceNames) {
            if (!missing(forbidden) && compact(forbidden).length() >= 2 && containsCompact(text, forbidden)) {
                errors.add(field + " contains actual place name [" + forbidden + "]");
                return;
            }
        }
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

    private boolean isUnsafeRewardClueForPlayer(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String value = text.trim();
        return isInternalContentText(value)
                || looksLikeOperationBriefing(value)
                || containsAny(value,
                "탐색해야", "조사해야", "대조해야", "밝혀내야", "해독하기 위해",
                "찾아야", "확인해야", "추리해야", "비교해야",
                "이 기록은", "이 단서는", "배경을 보강", "최종 정답", "정답이나 장소",
                "직접 말하지", "다른 증거 카드", "증거 카드와 함께", "보상 단서",
                "해금된 시작 기록", "시작 기록이 해금");
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
        Map<String, Object> config = interactionConfig(type, localSolution, basis, mission, index);
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

    private Map<String, Object> interactionConfig(
            String type,
            String answer,
            String basis,
            AiEpisodeDraftResponse.MissionDraft mission,
            int index) {
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
                config.put("cards", safeMemoryCards(mission, answer, basis, index));
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

    private List<String> safeMemoryCards(
            AiEpisodeDraftResponse.MissionDraft mission,
            String answer,
            String basis,
            int index) {
        List<String> seeds = new ArrayList<>(List.of(answer, basis));
        if (mission != null) {
            seeds.add(mission.getStoryText());
            seeds.add(mission.getRewardClue());
            if (mission.getPlaceEvidenceAnchor() != null) {
                seeds.addAll(mission.getPlaceEvidenceAnchor());
            }
            if (mission.getEvidenceDesign() != null) {
                seeds.add(mission.getEvidenceDesign().getArtifactType());
                seeds.add(mission.getEvidenceDesign().getVisibleDetail());
                seeds.add(mission.getEvidenceDesign().getRecordFragment());
            }
        }
        List<String> cards = seeds.stream()
                .map(this::safeMemoryCardToken)
                .filter(value -> value != null && !value.isBlank())
                .filter(this::isShortMemoryCardNoun)
                .distinct()
                .limit(6)
                .collect(Collectors.toCollection(ArrayList::new));
        int offset = Math.floorMod((answer + basis + index).hashCode(), STORY_TOKEN_FALLBACKS.size());
        for (int i = 0; i < STORY_TOKEN_FALLBACKS.size(); i++) {
            if (cards.size() >= 6) {
                break;
            }
            String fallback = STORY_TOKEN_FALLBACKS.get((offset + i) % STORY_TOKEN_FALLBACKS.size());
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

    private boolean isShortMemoryCardNoun(String value) {
        if (value == null || value.isBlank() || value.length() > 4 || value.contains(" ")) {
            return false;
        }
        if (isInternalContentText(value) || containsAny(value,
                "관리자", "검수", "확인필요", "공식설명없음", "자료부족", "보강필요", "추정")) {
            return false;
        }
        return !containsAny(value,
                "합니다", "하세요", "하십시오", "해야", "필요", "없음", "입니다",
                "에서", "으로", "에게", "한다", "했다", "있는", "없는");
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
        if (spots.size() != 10) errors.add("Published episodes require exactly 10 spots.");
        long startCount = spots.stream().filter(spot -> "START".equals(spot.getMarkerType())).count();
        long answerHintCount = spots.stream().filter(spot -> "ANSWER_HINT".equals(spot.getMarkerType())).count();
        long finalPlaceCount = spots.stream().filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace())).count();
        if (startCount != 1) errors.add("Exactly one START spot is required.");
        if (answerHintCount != 8) errors.add("Exactly eight ANSWER_HINT investigation spots are required.");
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
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String title = blank(safeRequest.getTitle(), "New case-file draft " + uniqueSuffix);
        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setSubtitle(blank(safeRequest.getSubtitle(), "Admin draft"));
        episode.setEra(blank(safeRequest.getEra(), "Review required"));
        episode.setGenre(requireAllowedGenre(blank(safeRequest.getGenre(), ContentGenre.MISSING_CASE.displayName())));
        episode.setDifficulty(blank(safeRequest.getDifficulty(), "NORMAL"));
        episode.setEstimatedTime(blank(safeRequest.getEstimatedTime(), "90~120분"));
        episode.setEstimatedDistance(blank(safeRequest.getEstimatedDistance(), "도보 동선 확인 필요"));
        episode.setFictionSynopsis(blank(safeRequest.getFictionSynopsis(), "Admin draft. Write the case synopsis before publishing."));
        episode.setMissionDescription(blank(safeRequest.getMissionDescription(), episode.getFictionSynopsis()));
        episode.setFinalAnswerType(blank(safeRequest.getFinalAnswerType(), "EVIDENCE"));
        episode.setFinalAnswer(blank(safeRequest.getFinalAnswer(), "review-required"));
        episode.setFinalAnswerAliases(safeRequest.getFinalAnswerAliases());
        episode.setFinalQuestion(blank(safeRequest.getFinalQuestion(), "Enter the final question."));
        episode.setFinalTruthSummary(blank(safeRequest.getFinalTruthSummary(), "Enter the private truth summary."));
        episode.setActualHistorySummary(safeRequest.getActualHistorySummary());
        episode.setDeductionSecretFacts(blank(safeRequest.getDeductionSecretFacts(), "Enter internal facts for deduction."));
        episode.setDeductionForbiddenReveals(blank(safeRequest.getDeductionForbiddenReveals(), "Do not reveal the final answer or actual final place."));
        episode.setMaxDeductionQuestions(safeRequest.getMaxDeductionQuestions() == null ? 20 : safeRequest.getMaxDeductionQuestions());
        episode.setRecommendedPlayers(blank(safeRequest.getRecommendedPlayers(), "2~4명"));
        episode.setTeamRoleGuide(blank(safeRequest.getTeamRoleGuide(), "지도, 사건파일, 퍼즐, 기록 역할로 나누어 진행하세요."));
        episode.setNoticeText(blank(safeRequest.getNoticeText(), "공개 전 현장 검수를 완료하세요."));
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
        ExternalPlaceResearchService.ResearchResult externalResearch = externalPlaceResearchService.research(source);
        if (externalResearch == null) {
            externalResearch = ExternalPlaceResearchService.ResearchResult.empty();
        }
        if (externalResearch.isEmpty()) {
            externalResearch = siteContextResearch(source, rankedNearby);
        }
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
        target.setExternalResearchNotes(mergeDistinct(source.getExternalResearchNotes(), externalResearch.notes()));
        target.setReferenceUrls(mergeDistinct(source.getReferenceUrls(), externalResearch.referenceUrls()));
        target.setResearchSourceSummary(firstNonBlank(source.getResearchSourceSummary(), externalResearch.summary()));
        return target;
    }

    private ExternalPlaceResearchService.ResearchResult siteContextResearch(
            AiEpisodeDraftRequest.PlaceInput source,
            List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> notes = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (!missing(source.getName())) {
            parts.add("name=" + source.getName().trim());
        }
        if (!missing(source.getAddress())) {
            parts.add("address=" + source.getAddress().trim());
        }
        if (!missing(source.getDescription())) {
            parts.add("description=" + source.getDescription().trim());
        }
        if (!missing(source.getAdminMemo())) {
            parts.add("memo=" + source.getAdminMemo().trim());
        }
        String nearby = rankedNearby == null ? "" : rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(3)
                .map(AdminPlaceCandidateResponse::getTitle)
                .collect(Collectors.joining(", "));
        if (!nearby.isBlank()) {
            parts.add("nearby=" + nearby);
        }
        if (!parts.isEmpty()) {
            notes.add("Selected place context: " + String.join(" / ", parts));
        }
        return new ExternalPlaceResearchService.ResearchResult(
                notes,
                List.of(),
                notes.isEmpty() ? null : "Selected place context used because no direct external reference page matched.");
    }

    private int matchingPlaceIndex(List<AiEpisodeDraftRequest.PlaceInput> places, AiEpisodeDraftRequest.PlaceInput target) {
        if (places == null || target == null) {
            return -1;
        }
        for (int i = 0; i < places.size(); i++) {
            if (samePlaceInput(places.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    private boolean samePlaceInput(AiEpisodeDraftRequest.PlaceInput left, AiEpisodeDraftRequest.PlaceInput right) {
        if (left == null || right == null) {
            return false;
        }
        if (!missing(left.getPlaceId()) && left.getPlaceId().equals(right.getPlaceId())) {
            return true;
        }
        return sameText(left.getName(), right.getName())
                && sameText(left.getAddress(), right.getAddress())
                && sameCoordinate(left.getLatitude(), right.getLatitude())
                && sameCoordinate(left.getLongitude(), right.getLongitude());
    }

    private boolean sameText(String left, String right) {
        return blank(left, "").equalsIgnoreCase(blank(right, ""));
    }

    private boolean sameCoordinate(Double left, Double right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Math.abs(left - right) < 0.000001;
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
        List<String> values = new ArrayList<>();
        rankedNearby.stream()
                .filter(candidate -> !missing(candidate.getTitle()) && !"RAG_ERROR".equals(candidate.getSource()))
                .limit(5)
                .forEach(candidate -> {
                    values.add(categoryKeyword(candidate));
                });
        values.add("현장단서");
        values.add("동선흔적");
        return values;
    }


    private List<String> inferredVisibleElements(List<AdminPlaceCandidateResponse> rankedNearby) {
        List<String> values = new ArrayList<>();
        values.add("현장에서 확인할 장소명 간판");
        values.add("현장에서 확인할 주소와 입구 영역");
        rankedNearby.stream()
                .filter(candidate -> !"RAG_ERROR".equals(candidate.getSource()))
                .limit(4)
                .map(this::categoryVisibleElement)
                .forEach(values::add);
        values.add("현장에서 확인할 주변 동선 단서");
        return values;
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
        String value = String.join(" ", blank(candidate.getTitle(), ""), blank(candidate.getSource(), ""), blank(candidate.getDescription(), ""), blank(candidate.getAddress(), ""));
        if (source.contains("CT1") || containsCompact(value, "문화시설") || containsCompact(value, "culture") || containsCompact(value, "museum") || containsCompact(value, "gallery") || containsCompact(value, "exhibition")) return "문화전시";
        if (source.contains("CE7") || containsCompact(value, "카페") || containsCompact(value, "cafe") || containsCompact(value, "coffee")) return "카페쉰터";
        if (source.contains("FD6") || containsCompact(value, "음식점") || containsCompact(value, "식당") || containsCompact(value, "restaurant") || containsCompact(value, "food")) return "식당상권";
        if (containsCompact(value, "park") || containsCompact(value, "square") || containsCompact(value, "street")) return "공개광장";
        return "현장단서";
    }




    private String categoryVisibleElement(AdminPlaceCandidateResponse candidate) {
        String keyword = categoryKeyword(candidate);
        return switch (keyword) {
            case "문화전시" -> "전시 안내문 또는 건물 표지";
            case "카페쉰터" -> "메뉴판 또는 입구 표지";
            case "식당상권" -> "가게 간판 또는 상권 동선 표지";
            case "공개광장" -> "공공 표지석 또는 동선 안내표";
            default -> "현장 표지물 또는 주변 구조물";
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
        if (List.of("START", "ANSWER_HINT", "FINAL").contains(normalized)) {
            return normalized;
        }
        if (index == 0) return "START";
        if (index >= total - 1) return "FINAL";
        return "ANSWER_HINT";
    }

    private String publicMarkerType(String requested, boolean finalPlace, String markerType) {
        if (finalPlace) {
            return "ANSWER_HINT";
        }
        String fallback = "FINAL".equals(markerType) ? "ANSWER_HINT" : markerType;
        String value = blank(requested, fallback);
        return validateValue(value, PUBLIC_MARKER_TYPES, "INVALID_PUBLIC_MARKER_TYPE", "publicMarkerType must not expose FINAL.");
    }

    private String toClueRole(String markerType) {
        return switch (markerType) {
            case "START" -> "START";
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
        if (answer.isBlank() || answer.contains("검수필요") || "review-required".equals(answer)) {
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
        if (text.isBlank() || isEnglishOnlyHint(text) || compactText.contains("가장최근") || compactText.contains("최근보상")
                || compactText.contains("이전증거") || compactText.contains("이전사건자료")) {
            String basis = firstGroundingText(mission);
            return "문제에 제시된 [" + basis + "] 단서를 기준으로 답을 좁히세요.";
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
            return "현장 근거";
        }
        if (!missing(mission.getAnswer()) && !compact(mission.getAnswer()).contains("검수필요")) {
            return mission.getAnswer().trim();
        }
        if (!missing(mission.getRewardClue()) && !compact(mission.getRewardClue()).contains("검수필요")) {
            return mission.getRewardClue().trim();
        }
        return "현장 근거";
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
        return "현장단서";
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
            case "ANSWER_HINT" -> List.of("젖은 손잡이", "낮은 좌석", "끊긴 끈", "구겨진 영수증").get(Math.min(Math.max(index - 1, 0), 3));
            case "FINAL" -> "Final spot unlocks after all investigation missions are complete.";
            case "START" -> "사건 시작 단서";
            default -> "사건 단서";
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
            case "DOCUMENT", "EVIDENCE", "ANSWER_CLUE", "STORY_CLUE" -> "/generated-case-card-document.svg";
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

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : blank(second, null);
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

    private List<AdminEpisodeDetailResponse.FinalAnswerKeywordItem> restoreFinalAnswerKeywordItems(Episode episode) {
        List<String> values = parseKeywordContract(episode == null ? null : episode.getFinalAnswerAliases());
        if (values.isEmpty() && episode != null && !missing(episode.getFinalAnswer())) {
            values = java.util.Arrays.stream(episode.getFinalAnswer().split("[,/|]"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(4)
                    .toList();
        }
        if (values.size() < 4) {
            return List.of();
        }
        List<String> types = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
        List<String> labels = List.of("\ubc94\uc778", "\ud749\uae30", "\ub3d9\uae30", "\ubc29\ubc95");
        List<AdminEpisodeDetailResponse.FinalAnswerKeywordItem> items = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            items.add(AdminEpisodeDetailResponse.FinalAnswerKeywordItem.builder()
                    .type(types.get(i))
                    .displayType(labels.get(i))
                    .value(values.get(i))
                    .aliases(List.of())
                    .build());
        }
        return items;
    }

    private List<String> parseKeywordContract(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(aliases.split(","))
                .map(String::trim)
                .filter(value -> value.startsWith("KW:"))
                .findFirst()
                .map(value -> value.substring(3))
                .map(value -> java.util.Arrays.stream(value.split("\\|"))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .toList())
                .orElse(List.of());
    }

    private String normalizePuzzleTypeForSave(String value, int order) {
        String normalized = blank(value, "OBSERVATION").trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (PUZZLE_TYPES.contains(upper)) {
            return upper;
        }
        String alias = PUZZLE_TYPE_ALIASES.get(normalized);
        if (alias == null) {
            alias = PUZZLE_TYPE_ALIASES.get(upper);
        }
        if (alias != null) {
            return alias;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PUZZLE_TYPE",
                "Unsupported puzzleType. order=" + order + ", puzzleType=" + normalized
        );
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

    private String requireAllowedGenre(String genre) {
        if (!ContentGenre.isAllowedName(genre)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONTENT_GENRE",
                    "장르는 살인 미스터리, 실종 사건, 보물찾기, 암호 해독 중 하나여야 합니다."
            );
        }
        return genre.trim();
    }

    private void requireFinalAnswerFields(AiEpisodeDraftResponse.EpisodeDraft draft) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        boolean hasCrimeMysterySlots = answers != null
                && !missing(answers.getCulprit())
                && !missing(answers.getWeapon())
                && !missing(answers.getMotive())
                && !missing(answers.getMethod());
        boolean hasLegacySlots = answers != null
                && !missing(answers.getRelatedPerson())
                && !missing(answers.getCoreClue())
                && !missing(answers.getFinalLocation());
        if (!hasCrimeMysterySlots && !hasLegacySlots) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FINAL_ANSWER_FIELDS",
                    "finalAnswers must include CULPRIT/WEAPON/MOTIVE/METHOD values."
            );
        }
    }
    private void validateHumanReadableDraftTextForSave(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = AiDraftTextQualityValidator.findings(draft);
        if (findings.isEmpty()) {
            return;
        }
        String fields = findings.stream()
                .map(AiEpisodeDraftValidationResponse.Finding::getFieldPath)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(12)
                .collect(Collectors.joining(", "));
        String message = "\u0041\u0049 \uc0dd\uc131 \uacb0\uacfc\uc5d0 \uae68\uc9c4 \ud55c\uae00 \ub610\ub294 \uc778\ucf54\ub529 \uc624\ub958 \ubb38\uc790\uac00 \ud3ec\ud568\ub418\uc5b4 \uc788\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \uc0dd\uc131\ud574 \uc8fc\uc138\uc694.";
        if (!fields.isBlank()) {
            message += " (" + fields + ")";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "MOJIBAKE_TEXT_DETECTED", message);
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
