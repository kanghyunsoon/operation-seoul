package com.operation.seoul.episode.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.repository.CaseFileRepository;
import com.operation.seoul.episode.domain.*;
import com.operation.seoul.episode.dto.*;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.favorite.repository.EpisodeFavoriteRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.location.service.OperationAreaResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodePlayService {
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> ALLOWED_DEDUCTION_ANSWER_TYPES = Set.of(
            "YES", "NO", "RELATED", "NOT_RELATED", "PARTIAL", "AMBIGUOUS", "INSUFFICIENT_CLUE", "REFUSED_DIRECT_REVEAL"
    );
    private final EpisodeRepository episodeRepository;
    private final CaseFileRepository caseFileRepository;
    private final EpisodeFavoriteRepository favoriteRepository;
    private final ObjectMapper objectMapper;
    private final OperationAreaResolver operationAreaResolver;
    private final MinigameProofValidator minigameProofValidator;
    private final MinigameRetryVariantFactory minigameRetryVariantFactory;
    private final PuzzleAttemptGuard puzzleAttemptGuard;

    @Value("${app.dev-mode.arrival-enabled:false}")
    private boolean arrivalDevModeEnabled;

    public List<EpisodeListItemResponse> getEpisodes(User user, String areaCode) {
        Set<Long> favoriteEpisodeIds = new LinkedHashSet<>(favoriteRepository.findEpisodeIdsByUserId(user.getId()));
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : operationAreaResolver.normalizeAreaCode(areaCode);
        return episodeRepository.findPublishedEpisodes().stream()
                .filter(episode -> normalizedAreaCode == null || episodeMatchesArea(episode.getId(), normalizedAreaCode))
                .map(episode -> EpisodeListItemResponse.builder()
                        .id(episode.getId())
                        .title(episode.getTitle())
                        .subtitle(episode.getSubtitle())
                        .era(episode.getEra())
                        .genre(episode.getGenre())
                        .difficulty(episode.getDifficulty())
                        .estimatedTime(localizeEstimatedTime(episode.getEstimatedTime()))
                        .estimatedDistance(localizeEstimatedDistance(episode.getEstimatedDistance()))
                        .favorited(favoriteEpisodeIds.contains(episode.getId()))
                        .build())
                .toList();
    }

    private boolean episodeMatchesArea(Long episodeId, String areaCode) {
        return episodeRepository.findSpotsByEpisodeId(episodeId).stream()
                .filter(spot -> spot.getLatitude() != null && spot.getLongitude() != null)
                .anyMatch(spot -> operationAreaResolver.isInsideAreaCode(areaCode, spot.getLatitude(), spot.getLongitude()));
    }

    private String localizeEstimatedTime(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.trim()
                .replaceAll("(?i)\\bmin\\b", "분")
                .replaceAll("(?i)\\bhours?\\b", "시간")
                .replaceAll("(?i)\\babout\\b\\s*", "약 ");
    }

    private String localizeEstimatedDistance(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("walking route review required") || normalized.equalsIgnoreCase("review required")) {
            return "?꾨낫 ?숈꽑 ?뺤씤 ?꾩슂";
        }
        return normalized.replaceAll("(?i)\\babout\\b\\s*", "약 ");
    }

    public EpisodeDetailResponse getEpisode(Long episodeId, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = findProgress(user.getId(), episodeId);
        return EpisodeDetailResponse.builder()
                .id(episode.getId())
                .title(episode.getTitle())
                .subtitle(episode.getSubtitle())
                .era(episode.getEra())
                .genre(episode.getGenre())
                .difficulty(episode.getDifficulty())
                .estimatedTime(localizeEstimatedTime(episode.getEstimatedTime()))
                .estimatedDistance(localizeEstimatedDistance(episode.getEstimatedDistance()))
                .fictionSynopsis(episode.getFictionSynopsis())
                .missionDescription(episode.getMissionDescription() == null || episode.getMissionDescription().isBlank()
                        ? episode.getFictionSynopsis()
                        : episode.getMissionDescription())
                .finalAnswerType(episode.getFinalAnswerType())
                .finalQuestion(episode.getFinalQuestion())
                .progressStatus(progress == null ? "NOT_STARTED" : progress.getStatus())
                .favorited(favoriteRepository.findByUserIdAndEpisodeId(user.getId(), episodeId) != null)
                .build();
    }

    public EpisodeDetailResponse startEpisode(Long episodeId, User user) {
        requireEpisode(episodeId);
        UserEpisodeProgress progress = ensureProgress(user.getId(), episodeId);
        if ("NOT_STARTED".equals(progress.getStatus())) {
            progress.setStatus("IN_PROGRESS");
            episodeRepository.updateProgress(progress);
        }
        return getEpisode(episodeId, user);
    }

    public EpisodeMapResponse getMap(Long episodeId, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = ensureProgress(user.getId(), episodeId);
        List<Long> visited = readLongList(progress.getVisitedSpotIds());
        List<Long> completed = readLongList(progress.getCompletedSpotIds());

        List<MissionSpot> spots = episodeRepository.findSpotsByEpisodeId(episodeId);
        boolean finalDestinationUnlocked = isFinalDestinationUnlocked(spots, completed, progress);
        List<SpotMarkerResponse> visibleSpots = new ArrayList<>();
        int answerKeywordIndex = 0;
        for (MissionSpot spot : spots) {
            boolean finalPlace = Boolean.TRUE.equals(spot.getFinalPlace());
            if (finalPlace && !finalDestinationUnlocked) {
                continue;
            }
            String displayMarkerType;
            if (finalPlace) {
                displayMarkerType = "FINAL";
            } else if (isAnswerKeywordSpot(spot)) {
                displayMarkerType = answerKeywordIndex % 2 == 0 ? "KEYWORD_1" : "KEYWORD_2";
                answerKeywordIndex++;
            } else {
                displayMarkerType = displayMarkerType(spot);
            }
            visibleSpots.add(toSpotMarker(spot, displayMarkerType, visited, completed));
        }
        SpotMarkerResponse adminFinalSpot = user != null && user.isAdmin()
                ? spots.stream()
                        .filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace()))
                        .findFirst()
                        .map(spot -> toSpotMarker(spot, "FINAL", visited, completed))
                        .orElse(null)
                : null;

        return EpisodeMapResponse.builder()
                .episodeId(episode.getId())
                .title(episode.getTitle())
                .progressStatus(progress.getStatus())
                .hintUsedCount(value(progress.getHintUsedCount()))
                .wrongAnswerCount(value(progress.getWrongAnswerCount()))
                .deductionQuestionCount(value(progress.getDeductionQuestionCount()))
                .finalDestinationUnlocked(finalDestinationUnlocked)
                .spots(visibleSpots)
                .adminFinalSpot(adminFinalSpot)
                .build();
    }

    private SpotMarkerResponse toSpotMarker(MissionSpot spot, String publicMarkerType, List<Long> visited, List<Long> completed) {
        return SpotMarkerResponse.builder()
                .spotId(spot.getId())
                .placeName(spot.getPlaceName())
                .address(spot.getAddress())
                .latitude(spot.getLatitude())
                .longitude(spot.getLongitude())
                .publicMarkerType(publicMarkerType)
                .finalPlace(Boolean.TRUE.equals(spot.getFinalPlace()))
                .storyText(sanitizeCategoryCodes(spot.getStoryText()))
                .visited(visited.contains(spot.getId()))
                .completed(completed.contains(spot.getId()))
                .rewardClueCollected(completed.contains(spot.getId()))
                .canOpenPuzzle(visited.contains(spot.getId()))
                .canNavigate(true)
                .build();
    }

    private boolean isFinalDestinationUnlocked(List<MissionSpot> spots, List<Long> completed, UserEpisodeProgress progress) {
        if (progress.getFinalArrivedSpotId() != null || "FINAL_READY".equals(progress.getStatus()) || "CLEARED".equals(progress.getStatus())) {
            return true;
        }
        List<MissionSpot> investigationSpots = spots.stream()
                .filter(spot -> !Boolean.TRUE.equals(spot.getFinalPlace()))
                .filter(spot -> !"START".equals(normalize(spot.getMarkerType())))
                .toList();
        return !investigationSpots.isEmpty() && investigationSpots.stream().allMatch(spot -> completed.contains(spot.getId()));
    }

    private String displayMarkerType(MissionSpot spot) {
        String markerType = normalize(spot.getMarkerType());
        String clueRole = normalize(spot.getClueRole());
        if ("START".equals(markerType) || "START".equals(clueRole)) {
            return "START";
        }
        if (isLocationKeywordSpot(spot)) {
            return "KEYWORD_3";
        }
        return "KEYWORD_1";
    }

    private boolean isAnswerKeywordSpot(MissionSpot spot) {
        return "ANSWER_HINT".equals(normalize(spot.getClueRole())) || "ANSWER_HINT".equals(normalize(spot.getMarkerType()));
    }

    private boolean isLocationKeywordSpot(MissionSpot spot) {
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public ArriveResponse arrive(Long episodeId, Long spotId, ArriveRequest request, User user) {
        requireEpisode(episodeId);
        MissionSpot spot = requireSpot(spotId, episodeId);
        UserEpisodeProgress progress = ensureProgress(user.getId(), episodeId);
        boolean adminBypass = user != null && user.isAdmin();
        if (Boolean.TRUE.equals(request.getDevMode()) && !arrivalDevModeEnabled && !adminBypass) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEV_ARRIVAL_DISABLED", "???쒕쾭?먯꽌??媛쒕컻???꾩갑 ?먯젙??鍮꾪솢?깊솕?섏뼱 ?덉뒿?덈떎.");
        }
        boolean devMode = Boolean.TRUE.equals(request.getDevMode()) && (arrivalDevModeEnabled || adminBypass);
        requireCoordinatesUnlessDevMode(request, devMode);
        double distance = devMode ? 0.0 : calculateDistanceMeters(request.getUserLat(), request.getUserLng(), spot.getLatitude(), spot.getLongitude());
        boolean arrived = devMode || distance <= safeRadius(spot.getArrivalRadius());

        if (!arrived) {
            return ArriveResponse.builder()
                    .arrived(false)
                    .distance(distance)
                    .canOpenPuzzle(false)
                    .isActualFinalArrived(false)
                    .canStartDeduction(false)
                    .message("?꾩갑 諛섍꼍 諛뽰엯?덈떎. ?μ냼????媛源뚯씠 ?대룞??二쇱꽭??")
                    .build();
        }

        addLong(progress, "visited", spotId);
        boolean actualFinal = Boolean.TRUE.equals(spot.getFinalPlace());
        if (actualFinal && !adminBypass && !isFinalDestinationUnlocked(episodeRepository.findSpotsByEpisodeId(episodeId), readLongList(progress.getCompletedSpotIds()), progress)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_DESTINATION_LOCKED", "조사 미션 8개를 모두 완료한 뒤 최종 장소에 도착할 수 있습니다.");
        }
        if (actualFinal) {
            progress.setFinalArrivedSpotId(spotId);
            progress.setStatus("FINAL_READY");
        }
        episodeRepository.updateProgress(progress);

        String message = actualFinal
                ? "議곗궗 ?μ냼媛 ?뺤씤?섏뿀?듬땲?? 理쒖쥌 異붾━瑜??쒖옉?????덉?留? 遺議깊븳 ?⑥꽌???먯닔???곹뼢??以????덉뒿?덈떎."
                : ("DESTINATION_HINT".equals(spot.getPublicMarkerType())
                    ? "?μ냼 ?⑥꽌? ?議고븷 議곗궗 吏?먯엯?덈떎. 誘몄뀡 硫붾え怨??⑥꽌 蹂대뱶瑜??ㅼ떆 ?뺤씤??二쇱꽭??"
                    : "?꾩갑???뺤씤?섏뿀?듬땲?? ?꾩옣 ?쇱쫹???????덉뒿?덈떎.");

        return ArriveResponse.builder()
                .arrived(true)
                .distance(distance)
                .canOpenPuzzle(true)
                .isActualFinalArrived(actualFinal)
                .canStartDeduction(actualFinal)
                .message(message)
                .build();
    }

    public ArriveResponse arriveFinalPlace(Long episodeId, ArriveRequest request, User user) {
        requireEpisode(episodeId);
        MissionSpot finalSpot = episodeRepository.findSpotsByEpisodeId(episodeId).stream()
                .filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FINAL_SPOT_NOT_FOUND", "議곗궗 ?μ냼 ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));
        UserEpisodeProgress progress = ensureProgress(user.getId(), episodeId);
        boolean adminBypass = user != null && user.isAdmin();
        if (Boolean.TRUE.equals(request.getDevMode()) && !arrivalDevModeEnabled && !adminBypass) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEV_ARRIVAL_DISABLED", "???쒕쾭?먯꽌??媛쒕컻???꾩갑 ?먯젙??鍮꾪솢?깊솕?섏뼱 ?덉뒿?덈떎.");
        }
        boolean devMode = Boolean.TRUE.equals(request.getDevMode()) && (arrivalDevModeEnabled || adminBypass);
        requireCoordinatesUnlessDevMode(request, devMode);
        if (!adminBypass && !isFinalDestinationUnlocked(episodeRepository.findSpotsByEpisodeId(episodeId), readLongList(progress.getCompletedSpotIds()), progress)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_DESTINATION_LOCKED", "조사 미션 8개를 모두 완료한 뒤 최종 장소에 도착할 수 있습니다.");
        }
        double distance = devMode ? 0.0 : calculateDistanceMeters(request.getUserLat(), request.getUserLng(), finalSpot.getLatitude(), finalSpot.getLongitude());
        boolean arrived = devMode || distance <= safeRadius(finalSpot.getArrivalRadius());
        if (!arrived) {
            return ArriveResponse.builder()
                    .arrived(false)
                    .distance(distance)
                    .canOpenPuzzle(false)
                    .isActualFinalArrived(false)
                    .canStartDeduction(false)
                    .message("?꾩옱 ?꾩튂?먯꽌??理쒖쥌 異붾━瑜??쒖옉?????놁뒿?덈떎. ?⑥꽌 蹂대뱶? 誘몄뀡 硫붾え???ㅼ떆 ?뺤씤??二쇱꽭??")
                    .build();
        }
        progress.setFinalArrivedSpotId(finalSpot.getId());
        progress.setStatus("FINAL_READY");
        episodeRepository.updateProgress(progress);
        return ArriveResponse.builder()
                .arrived(true)
                .distance(distance)
                .canOpenPuzzle(false)
                .isActualFinalArrived(true)
                .canStartDeduction(true)
                .message("議곗궗 ?μ냼媛 ?뺤씤?섏뿀?듬땲?? 理쒖쥌 異붾━瑜??쒖옉?????덉?留? 遺議깊븳 ?⑥꽌???먯닔???곹뼢??以????덉뒿?덈떎.")
                .build();
    }

    public PuzzleResponse getPuzzle(Long spotId, User user) {
        MissionSpot spot = requireSpot(spotId, null);
        UserEpisodeProgress progress = requireProgress(user.getId(), spot.getEpisodeId());
        if (!readLongList(progress.getVisitedSpotIds()).contains(spotId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARRIVAL_REQUIRED", "?쇱쫹???닿린 ?꾩뿉 ?대떦 ?μ냼???꾩갑?댁빞 ?⑸땲??");
        }
        Puzzle puzzle = episodeRepository.findPuzzleBySpotId(spotId);
        if (puzzle == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "?쇱쫹??李얠쓣 ???놁뒿?덈떎.");
        }
        return PuzzleResponse.builder()
                .puzzleId(puzzle.getId())
                .spotId(spotId)
                .puzzleType(puzzle.getPuzzleType())
                .questionText(sanitizeCategoryCodes(puzzle.getQuestionText()))
                .answerFormat(puzzle.getAnswerFormat())
                .difficulty(puzzle.getDifficulty())
                .hints(episodeRepository.findHintsByPuzzleId(puzzle.getId()).stream().map(PuzzleHint::getHintText).map(this::sanitizeCategoryCodes).toList())
                .interaction(puzzleInteraction(puzzle, activeWrongCount(user.getId(), puzzle.getId())))
                .build();
    }

    private Map<String, Object> readPuzzleInteraction(String rewardPayload) {
        if (rewardPayload == null || rewardPayload.isBlank()) {
            return null;
        }
        try {
            JsonNode interaction = objectMapper.readTree(rewardPayload).path("interaction");
            if (!interaction.isObject()) {
                return null;
            }
            return objectMapper.convertValue(interaction, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> puzzleInteraction(Puzzle puzzle) {
        Map<String, Object> existing = readPuzzleInteraction(puzzle.getRewardPayload());
        return existing == null ? buildFallbackInteraction(puzzle) : sanitizeInteraction(existing);
    }

    private Map<String, Object> puzzleInteraction(Puzzle puzzle, int retryVariant) {
        Map<String, Object> interaction = puzzleInteraction(puzzle);
        return retryVariant <= 0
                ? interaction
                : sanitizeInteraction(minigameRetryVariantFactory.variantInteraction(interaction, retryVariant));
    }

    private int activeWrongCount(Long userId, Long puzzleId) {
        Integer value = episodeRepository.findActivePuzzleWrongCount(userId, puzzleId);
        return value == null ? 0 : Math.max(0, value);
    }

    private Map<String, Object> sanitizeInteraction(Map<String, Object> interaction) {
        Map<String, Object> copy = new LinkedHashMap<>(interaction);
        for (String key : List.of("title", "prompt", "storyHook", "basis", "missionDescription")) {
            Object value = copy.get(key);
            if (value instanceof String text) {
                copy.put(key, sanitizeCategoryCodes(text));
            }
        }
        return copy;
    }

    private String minigamePayload(Puzzle puzzle) {
        Map<String, Object> existing = readPuzzleInteraction(puzzle.getRewardPayload());
        if (existing != null) {
            return puzzle.getRewardPayload();
        }
        try {
            return objectMapper.writeValueAsString(Map.of("interaction", buildFallbackInteraction(puzzle)));
        } catch (Exception ignored) {
            return puzzle.getRewardPayload();
        }
    }

    private Map<String, Object> buildFallbackInteraction(Puzzle puzzle) {
        String localSolution = fallbackMinigameSolution(puzzle);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("nodes", fallbackPatternNodes(localSolution));

        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("version", 1);
        interaction.put("type", "PATTERN_LOCK");
        interaction.put("title", "?⑥꽌 ?μ튂 쨌 ?⑦꽩 ?좉툑");
        interaction.put("prompt", "?꾨옒 誘몄뀡???닿껐?섏뿬 ?⑥꽌瑜??살쑝?몄슂.");
        interaction.put("missionDescription", "?좉퉸 ?먮벑?섎뒗 ?몃뱶 ?쒖꽌瑜?湲곗뼲?섍퀬 洹몃?濡??낅젰????寃곌낵瑜??쒖텧?섏꽭??");
        interaction.put("storyHook", "?꾨옒 誘몄뀡???닿껐?섏뿬 ?⑥꽌瑜??살쑝?몄슂.");
        interaction.put("basis", sanitizeCategoryCodes(fallbackText(puzzle.getRewardClue(), "?ш굔 ?⑥꽌")));
        interaction.put("localSolution", localSolution);
        interaction.put("timeLimitSeconds", 60);
        interaction.put("config", config);
        return interaction;
    }

    private String fallbackMinigameSolution(Puzzle puzzle) {
        String source = sanitizeCategoryCodes(fallbackText(puzzle.getRewardClue(), fallbackText(puzzle.getQuestionText(), "?⑥꽌?μ튂")));
        String compact = source.replaceAll("\\s+", "");
        if (compact.length() > 8) {
            compact = compact.substring(0, 8);
        }
        return compact.isBlank() ? "?⑥꽌?μ튂" : compact;
    }

    private List<String> rotatedCharacters(String value) {
        List<String> characters = new ArrayList<>(value.codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(text -> !text.isBlank())
                .toList());
        if (characters.size() < 2) {
            characters.add("단서");
            characters.add("단서");
        }
        Collections.rotate(characters, Math.max(1, characters.size() / 2));
        return characters;
    }

    private List<Integer> fallbackPatternNodes(String value) {
        int seed = Math.abs(fallbackText(value, "단서배치").hashCode());
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

    private String fallbackText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sanitizeCategoryCodes(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("KakaoLocal:CE7", "카페/커피 휴식 지점")
                .replace("KakaoLocal:FD6", "음식점 지점")
                .replace("KakaoLocal:CT1", "문화시설/전시 지점")
                .replace("KakaoLocal:AT4", "관광명소 지점")
                .replace("CE7", "카페/커피 휴식 지점")
                .replace("FD6", "음식점 지점")
                .replace("CT1", "문화시설/전시 지점")
                .replace("AT4", "관광명소 지점");
    }

    private boolean isPuzzleAnswerAccepted(Puzzle puzzle, String submittedAnswer, int expectedRetryVariant) {
        Map<String, Object> interaction = readPuzzleInteraction(puzzle.getRewardPayload());
        if (interaction != null || (submittedAnswer != null && submittedAnswer.startsWith("MG|"))) {
            return minigameProofValidator.validate(minigamePayload(puzzle), submittedAnswer, expectedRetryVariant);
        }
        return normalizeAnswer(puzzle.getAnswer()).equals(normalizeAnswer(submittedAnswer));
    }

    @Transactional
    public PuzzleSubmitResponse submitPuzzle(Long puzzleId, PuzzleSubmitRequest request, User user) {
        Puzzle puzzle = episodeRepository.findPuzzleById(puzzleId);
        if (puzzle == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "?쇱쫹??李얠쓣 ???놁뒿?덈떎.");
        }
        MissionSpot spot = requireSpot(puzzle.getMissionSpotId(), null);
        UserEpisodeProgress progress = requireProgress(user.getId(), spot.getEpisodeId());
        if (!readLongList(progress.getVisitedSpotIds()).contains(spot.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARRIVAL_REQUIRED", "?쇱쫹???湲??꾩뿉 ?대떦 ?μ냼???꾩갑?댁빞 ?⑸땲??");
        }

        puzzleAttemptGuard.enforce(user.getId(), puzzle.getId());
        int expectedRetryVariant = activeWrongCount(user.getId(), puzzle.getId());
        boolean correct = isPuzzleAnswerAccepted(puzzle, request.getAnswer(), expectedRetryVariant);
        if (!correct) {
            puzzleAttemptGuard.recordWrong(user.getId(), puzzle.getId());
            int retryVariant = activeWrongCount(user.getId(), puzzle.getId());
            progress.setWrongAnswerCount(value(progress.getWrongAnswerCount()) + 1);
            episodeRepository.updateProgress(progress);
            log.info(
                    "puzzle_submission outcome=wrong userId={} episodeId={} puzzleId={} totalWrongCount={}",
                    user.getId(),
                    spot.getEpisodeId(),
                    puzzle.getId(),
                    progress.getWrongAnswerCount()
            );
            return PuzzleSubmitResponse.builder()
                    .correct(false)
                    .message("?뺣떟???꾨떃?덈떎. 媛숈? 誘몄뀡???ㅻⅨ 臾몄젣濡??ㅼ떆 ?쒕룄??二쇱꽭??")
                    .retryInteraction(puzzleInteraction(puzzle, retryVariant))
                    .clueBoard(buildClueBoard(progress))
                    .build();
        }

        addLong(progress, "completed", spot.getId());
        puzzleAttemptGuard.clear(user.getId(), puzzle.getId());
        RewardApplyResult rewardResult = applyPuzzleRewards(progress, spot, puzzle);
        episodeRepository.updateProgress(progress);
        log.info(
                "puzzle_submission outcome=correct userId={} episodeId={} puzzleId={} rewardTypes={}",
                user.getId(),
                spot.getEpisodeId(),
                puzzle.getId(),
                rewardResult.rewardTypes()
        );
        return PuzzleSubmitResponse.builder()
                .correct(true)
                .rewardClue(sanitizeCategoryCodes(puzzle.getRewardClue()))
                .caseFileUpdated(rewardResult.caseFileUpdated())
                .unlockedRewardTypes(rewardResult.rewardTypes())
                .unlockedEvidenceIds(rewardResult.evidenceIds())
                .unlockedSuspectIds(rewardResult.suspectIds())
                .updatedSuspectIds(rewardResult.updatedSuspectIds())
                .unlockedPhotoIds(rewardResult.photoIds())
                .unlockedMemoIds(rewardResult.memoIds())
                .newlyUnlockedItems(rewardResult.items())
                .message(rewardResult.caseFileUpdated()
                        ? "?뺣떟?낅땲?? ?⑥꽌? ?ш굔?먮즺媛 誘몄뀡 硫붾え??異붽??섏뿀?듬땲??"
                        : "?뺣떟?낅땲?? ?⑥꽌媛 ?⑥꽌 蹂대뱶????λ릺?덉뒿?덈떎.")
                .clueBoard(buildClueBoard(progress))
                .build();
    }

    public ClueBoardResponse getClueBoard(Long episodeId, User user) {
        requireEpisode(episodeId);
        return buildClueBoard(ensureProgress(user.getId(), episodeId));
    }

    public DeductionStartResponse startDeduction(Long episodeId, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = requireProgress(user.getId(), episodeId);
        if (!"IN_PROGRESS".equals(progress.getStatus()) && !"FINAL_READY".equals(progress.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_PROGRESS", "?꾩옱 吏꾪뻾 ?곹깭?먯꽌??理쒖쥌 異붾━瑜??쒖옉?????놁뒿?덈떎.");
        }
        MissionSpot finalSpot = episodeRepository.findSpotsByEpisodeId(episodeId).stream()
                .filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FINAL_SPOT_NOT_FOUND", "議곗궗 ?μ냼瑜?李얠쓣 ???놁뒿?덈떎."));
        if (!finalSpot.getId().equals(progress.getFinalArrivedSpotId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_ARRIVAL_REQUIRED", "理쒖쥌 異붾━瑜??쒖옉?섍린 ?꾩뿉 議곗궗 ?μ냼瑜??뺤씤?댁빞 ?⑸땲??");
        }

        FinalDeductionSession session = episodeRepository.findOpenDeductionSession(user.getId(), episodeId);
        if (session == null) {
            session = new FinalDeductionSession();
            session.setUserId(user.getId());
            session.setEpisodeId(episodeId);
            episodeRepository.insertDeductionSession(session);
            session = episodeRepository.findDeductionSession(session.getId());
        }
        List<String> clues = allClues(progress);
        String message = clues.size() < 3
                ? "?⑥꽌媛 遺議깊빀?덈떎. ???뺥솗??吏덈Ц???꾪빐 愿?⑥옄/?듭떖 ?⑥꽌 誘몄뀡????議곗궗??二쇱꽭??"
                : "理쒖쥌 異붾━ 梨꾪똿???쒖옉?섏뿀?듬땲??";
        return DeductionStartResponse.builder()
                .sessionId(session.getId())
                .maxQuestionCount(maxQuestions(episode))
                .currentQuestionCount(value(session.getQuestionCount()))
                .collectedClues(clues)
                .finalQuestion(episode.getFinalQuestion())
                .message(message)
                .build();
    }

    public DeductionAskResponse askDeduction(Long sessionId, DeductionAskRequest request, User user) {
        FinalDeductionSession session = requireSession(sessionId, user);
        Episode episode = requireEpisode(session.getEpisodeId());
        int maxQuestions = maxQuestions(episode);
        int current = value(session.getQuestionCount());
        if (current >= maxQuestions) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEDUCTION_LIMIT_EXCEEDED", "?ъ슜 媛?ν븳 吏덈Ц ?잛닔瑜?紐⑤몢 ?ъ슜?덉뒿?덈떎.");
        }
        UserEpisodeProgress progress = requireProgress(user.getId(), session.getEpisodeId());
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "吏덈Ц???낅젰??二쇱꽭??");
        }

        DeductionAnswer answer = sanitizeDeductionAnswer(episode, answerDeductionQuestion(episode, progress, question));
        FinalDeductionQuestion saved = new FinalDeductionQuestion();
        saved.setSessionId(sessionId);
        saved.setUserQuestion(question);
        saved.setAiAnswerType(answer.type());
        saved.setAiAnswerText(answer.text());
        episodeRepository.insertDeductionQuestion(saved);

        session.setQuestionCount(current + 1);
        episodeRepository.updateDeductionSession(session);
        progress.setDeductionQuestionCount(value(progress.getDeductionQuestionCount()) + 1);
        episodeRepository.updateProgress(progress);

        return DeductionAskResponse.builder()
                .answerType(answer.type())
                .answerText(answer.text())
                .questionCount(current + 1)
                .remainingQuestionCount(Math.max(0, maxQuestions - current - 1))
                .build();
    }

    public List<DeductionQuestionResponse> getDeductionQuestions(Long sessionId, User user) {
        FinalDeductionSession session = requireSession(sessionId, user);
        requireEpisode(session.getEpisodeId());
        return episodeRepository.findDeductionQuestions(sessionId).stream()
                .map(question -> DeductionQuestionResponse.builder()
                        .id(question.getId())
                        .userQuestion(question.getUserQuestion())
                        .aiAnswerType(question.getAiAnswerType())
                        .aiAnswerText(localizeDeductionAnswer(question.getAiAnswerType(), question.getAiAnswerText()))
                        .createdAt(question.getCreatedAt())
                        .build())
                .toList();
    }

    public FinalAnswerResponse submitFinalAnswer(Long episodeId, FinalAnswerRequest request, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = requireProgress(user.getId(), episodeId);
        if (progress.getFinalArrivedSpotId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_ARRIVAL_REQUIRED", "理쒖쥌 ?뺣떟???쒖텧?섍린 ?꾩뿉 議곗궗 ?μ냼瑜??뺤씤?댁빞 ?⑸땲??");
        }
        FinalDeductionSession session = request.getSessionId() == null ? null : requireSession(request.getSessionId(), user);
        boolean correct = isFinalAnswerCorrect(episode, request.getFinalAnswer());
        progress.setFinalGuessCount(value(progress.getFinalGuessCount()) + 1);
        if (!correct) {
            progress.setWrongAnswerCount(value(progress.getWrongAnswerCount()) + 1);
            if (session != null) {
                session.setFinalGuessCount(value(session.getFinalGuessCount()) + 1);
                episodeRepository.updateDeductionSession(session);
            }
            episodeRepository.updateProgress(progress);
            return FinalAnswerResponse.builder()
                    .correct(false)
                    .status(progress.getStatus())
                    .score(progress.getScore())
                    .message("?뺣떟???꾨떃?덈떎. ?⑥꽌 蹂대뱶? 吏덈Ц 湲곕줉???ㅼ떆 ?뺤씤??二쇱꽭??")
                    .build();
        }

        progress.setStatus("CLEARED");
        progress.setClearedAt(LocalDateTime.now());
        progress.setScore(calculateScore(progress));
        episodeRepository.updateProgress(progress);
        if (session != null) {
            session.setStatus("CLEARED");
            session.setCompletedAt(LocalDateTime.now());
            session.setFinalGuessCount(value(session.getFinalGuessCount()) + 1);
            episodeRepository.updateDeductionSession(session);
        }
        return FinalAnswerResponse.builder()
                .correct(true)
                .status("CLEARED")
                .score(progress.getScore())
                .message("?ш굔???닿껐?덉뒿?덈떎. ?대━??由ы룷?몃? ?뺤씤?????덉뒿?덈떎.")
                .build();
    }
    public ClearReportResponse getClearReport(Long episodeId, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = requireProgress(user.getId(), episodeId);
        if (!"CLEARED".equals(progress.getStatus()) || progress.getClearedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLEAR_REQUIRED", "?대━??由ы룷?몃뒗 ?먰뵾?뚮뱶 ?대━???꾩뿉留??뺤씤?????덉뒿?덈떎.");
        }
        List<Long> visitedSpotIds = readLongList(progress.getVisitedSpotIds());
        List<Long> completedSpotIds = readLongList(progress.getCompletedSpotIds());
        List<String> rawAnswerClues = readStringList(progress.getCollectedAnswerClues());
        List<String> culpritClues = typedClues(rawAnswerClues, "CULPRIT", false);
        List<String> weaponClues = typedClues(rawAnswerClues, "WEAPON", false);
        List<String> motiveClues = typedClues(rawAnswerClues, "MOTIVE", false);
        List<String> methodClues = typedClues(rawAnswerClues, "METHOD", false);
        List<String> answerClues = rawAnswerClues.stream().map(this::clueValueWithoutSlot).toList();
        List<String> destinationClues = readStringList(progress.getCollectedDestinationClues()).stream().map(this::clueValueWithoutSlot).toList();
        List<String> storyClues = readStringList(progress.getCollectedStoryClues()).stream().map(this::clueValueWithoutSlot).toList();
        MissionSpot finalArrivedSpot = progress.getFinalArrivedSpotId() == null ? null : episodeRepository.findSpotById(progress.getFinalArrivedSpotId());
        Long elapsedSeconds = progress.getStartedAt() == null ? null : Duration.between(progress.getStartedAt(), progress.getClearedAt()).getSeconds();

        return ClearReportResponse.builder()
                .episodeId(episodeId)
                .title(episode.getTitle())
                .finalQuestion(episode.getFinalQuestion())
                .finalAnswerType(episode.getFinalAnswerType())
                .score(progress.getScore())
                .status(progress.getStatus())
                .startedAt(progress.getStartedAt())
                .clearedAt(progress.getClearedAt())
                .elapsedSeconds(elapsedSeconds)
                .finalTruthSummary(episode.getFinalTruthSummary())
                .actualHistorySummary(episode.getActualHistorySummary())
                .visitedSpotCount(visitedSpotIds.size())
                .completedSpotCount(completedSpotIds.size())
                .totalSpotCount(episodeRepository.findSpotsByEpisodeId(episodeId).size())
                .answerClueCount(answerClues.size())
                .destinationClueCount(destinationClues.size())
                .storyClueCount(storyClues.size())
                .hintUsedCount(value(progress.getHintUsedCount()))
                .deductionQuestionCount(value(progress.getDeductionQuestionCount()))
                .wrongAnswerCount(value(progress.getWrongAnswerCount()))
                .finalGuessCount(value(progress.getFinalGuessCount()))
                .culpritClues(culpritClues)
                .weaponClues(weaponClues)
                .motiveClues(motiveClues)
                .methodClues(methodClues)
                .answerClues(answerClues)
                .destinationClues(destinationClues)
                .storyClues(storyClues)
                .unlockedSuspectIds(readLongList(progress.getUnlockedSuspectIds()))
                .unlockedEvidenceIds(readLongList(progress.getUnlockedEvidenceIds()))
                .finalArrivedSpotName(finalArrivedSpot == null ? null : finalArrivedSpot.getPlaceName())
                .canReview(true)
                .build();
    }

    private Episode requireEpisode(Long episodeId) {
        Episode episode = episodeRepository.findEpisodeById(episodeId);
        if (episode == null || !"PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "?먰뵾?뚮뱶瑜?李얠쓣 ???놁뒿?덈떎.");
        }
        return episode;
    }

    private MissionSpot requireSpot(Long spotId, Long episodeId) {
        MissionSpot spot = episodeRepository.findSpotById(spotId);
        if (spot == null || (episodeId != null && !episodeId.equals(spot.getEpisodeId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "議곗궗 吏?먯쓣 李얠쓣 ???놁뒿?덈떎.");
        }
        requireEpisode(spot.getEpisodeId());
        return spot;
    }

    private UserEpisodeProgress findProgress(Long userId, Long episodeId) {
        return episodeRepository.findProgress(userId, episodeId);
    }

    private UserEpisodeProgress requireProgress(Long userId, Long episodeId) {
        UserEpisodeProgress progress = findProgress(userId, episodeId);
        if (progress == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EPISODE_NOT_STARTED", "癒쇱? ?먰뵾?뚮뱶瑜??쒖옉??二쇱꽭??");
        }
        return progress;
    }

    private UserEpisodeProgress ensureProgress(Long userId, Long episodeId) {
        UserEpisodeProgress progress = findProgress(userId, episodeId);
        if (progress != null) {
            return progress;
        }
        UserEpisodeProgress created = new UserEpisodeProgress();
        created.setUserId(userId);
        created.setEpisodeId(episodeId);
        created.setVisitedSpotIds("[]");
        created.setCompletedSpotIds("[]");
        created.setCollectedAnswerClues("[]");
        created.setCollectedDestinationClues("[]");
        created.setCollectedStoryClues("[]");
        created.setUnlockedSuspectIds("[]");
        created.setClearedSuspectIds("[]");
        created.setUnlockedEvidenceIds("[]");
        created.setStatus("IN_PROGRESS");
        episodeRepository.insertProgress(created);
        return episodeRepository.findProgress(userId, episodeId);
    }

    private FinalDeductionSession requireSession(Long sessionId, User user) {
        FinalDeductionSession session = episodeRepository.findDeductionSession(sessionId);
        if (session == null || !user.getId().equals(session.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEDUCTION_SESSION_NOT_FOUND", "理쒖쥌 異붾━ ?몄뀡??李얠쓣 ???놁뒿?덈떎.");
        }
        if (!"OPEN".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEDUCTION_SESSION_CLOSED", "??理쒖쥌 異붾━ ?몄뀡? 醫낅즺?섏뿀?듬땲??");
        }
        return session;
    }

    private ClueBoardResponse buildClueBoard(UserEpisodeProgress progress) {
        Set<Long> unlockedEvidenceIds = new LinkedHashSet<>(readLongList(progress.getUnlockedEvidenceIds()));
        List<CaseEvidence> unlockedEvidences = caseFileRepository.findEvidences(progress.getEpisodeId()).stream()
                .filter(evidence -> unlockedEvidenceIds.contains(evidence.getId()))
                .toList();
        List<String> rawAnswer = readStringList(progress.getCollectedAnswerClues()).stream().map(this::sanitizeCategoryCodes).toList();
        List<String> culprit = typedClues(rawAnswer, "CULPRIT", false);
        List<String> weapon = typedClues(rawAnswer, "WEAPON", false);
        List<String> motive = typedClues(rawAnswer, "MOTIVE", false);
        List<String> method = typedClues(rawAnswer, "METHOD", false);
        List<String> relatedPerson = evidenceClues(unlockedEvidences, "SUSPECT_CLUE");
        if (relatedPerson.isEmpty()) {
            relatedPerson = typedClues(rawAnswer, "RELATED_PERSON", true);
        }
        List<String> core = evidenceClues(unlockedEvidences, "ANSWER_CLUE");
        if (core.isEmpty()) {
            core = typedClues(rawAnswer, "ANSWER_CLUE", false);
        }
        List<String> caseTruth = Stream.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD")
                .flatMap(slot -> typedClues(rawAnswer, slot, false).stream())
                .toList();
        if (!caseTruth.isEmpty()) {
            core = Stream.concat(core.stream(), caseTruth.stream()).distinct().toList();
        }
        List<String> answer = Stream.concat(relatedPerson.stream(), core.stream()).distinct().toList();
        List<String> destination = evidenceClues(unlockedEvidences, "DESTINATION_CLUE");
        if (destination.isEmpty()) {
            destination = readStringList(progress.getCollectedDestinationClues()).stream()
                    .map(this::sanitizeCategoryCodes)
                    .map(this::clueValueWithoutSlot)
                    .toList();
        }
        List<String> story = evidenceClues(unlockedEvidences, "STORY_CLUE");
        if (story.isEmpty()) {
            story = readStringList(progress.getCollectedStoryClues()).stream()
                    .map(this::sanitizeCategoryCodes)
                    .map(this::clueValueWithoutSlot)
                    .toList();
        }
        return ClueBoardResponse.builder()
                .episodeId(progress.getEpisodeId())
                .culpritClues(culprit)
                .weaponClues(weapon)
                .motiveClues(motive)
                .methodClues(method)
                .relatedPersonClues(relatedPerson)
                .coreClues(core)
                .answerClues(answer)
                .destinationClues(destination)
                .storyClues(story)
                .visitedSpotIds(readLongList(progress.getVisitedSpotIds()))
                .completedSpotIds(readLongList(progress.getCompletedSpotIds()))
                .unlockedSuspectIds(readLongList(progress.getUnlockedSuspectIds()))
                .clearedSuspectIds(readLongList(progress.getClearedSuspectIds()))
                .unlockedEvidenceIds(List.copyOf(unlockedEvidenceIds))
                .relatedPersonClueCount(relatedPerson.size())
                .coreClueCount(core.size())
                .answerClueCount(answer.size())
                .destinationClueCount(destination.size())
                .storyClueCount(story.size())
                .build();
    }

    private List<String> evidenceClues(List<CaseEvidence> evidences, String clueType) {
        return evidences.stream()
                .filter(evidence -> clueType.equalsIgnoreCase(evidence.getRelatedClueType()))
                .map(CaseEvidence::getTextSummary)
                .filter(text -> text != null && !text.isBlank())
                .map(this::sanitizeCategoryCodes)
                .distinct()
                .toList();
    }

    private void addLong(UserEpisodeProgress progress, String target, Long value) {
        String json = "completed".equals(target) ? progress.getCompletedSpotIds() : progress.getVisitedSpotIds();
        List<Long> ids = new ArrayList<>(readLongList(json));
        if (!ids.contains(value)) {
            ids.add(value);
        }
        if ("completed".equals(target)) {
            progress.setCompletedSpotIds(writeJson(ids));
        } else {
            progress.setVisitedSpotIds(writeJson(ids));
        }
    }

    private void addRewardClue(UserEpisodeProgress progress, MissionSpot spot, String clue) {
        if (clue == null || clue.isBlank()) {
            return;
        }
        String clean = sanitizeCategoryCodes(clue.trim());
        if ("ANSWER_HINT".equals(spot.getClueRole())) {
            progress.setCollectedAnswerClues(addString(progress.getCollectedAnswerClues(), typedClueValue(inferAnswerSlot(spot, clean), clean)));
        } else if ("DESTINATION_HINT".equals(spot.getClueRole()) || "FINAL_PLACE".equals(spot.getClueRole())) {
            progress.setCollectedAnswerClues(addString(progress.getCollectedAnswerClues(), typedClueValue(inferAnswerSlot(spot, clean), clean)));
        } else {
            progress.setCollectedStoryClues(addString(progress.getCollectedStoryClues(), clean));
        }
    }

    private RewardApplyResult applyPuzzleRewards(UserEpisodeProgress progress, MissionSpot spot, Puzzle puzzle) {
        List<String> rewardTypes = new ArrayList<>();
        List<Long> evidenceIds = new ArrayList<>();
        List<Long> suspectIds = new ArrayList<>();
        List<Long> updatedSuspectIds = new ArrayList<>();
        List<Long> photoIds = new ArrayList<>();
        List<Long> memoIds = new ArrayList<>();
        List<PuzzleSubmitResponse.UnlockedCaseFileItem> items = new ArrayList<>();
        boolean payloadApplied = false;

        if (puzzle.getRewardPayload() != null && !puzzle.getRewardPayload().isBlank()) {
            try {
                JsonNode rewards = objectMapper.readTree(puzzle.getRewardPayload()).path("rewards");
                if (rewards.isArray()) {
                    for (JsonNode reward : rewards) {
                        String slotId = normalizeRewardSlot(reward.path("targetKeywordType").asText(""));
                        if (slotId.isBlank()) {
                            slotId = normalizeRewardSlot(reward.path("slotId").asText(""));
                        }
                        String rawType = reward.path("type").asText("");
                        String type = isClueReward(rawType) ? rewardTypeForSpot(spot, rawType, slotId) : rawType;
                        if (isClueReward(type) && slotId.isBlank()) {
                            slotId = slotIdForRewardType(type);
                        }
                        String value = sanitizeCategoryCodes(reward.path("value").asText(""));
                        Long targetId = reward.hasNonNull("targetId") ? reward.path("targetId").asLong() : null;
                        value = rewardValueWithLetterReveal(value, reward, type);
                        boolean added = applyReward(progress, spot, type, value, slotId, targetId);
                        if (added) {
                            rewardTypes.add(type);
                            if (isEvidenceUnlock(type) && targetId != null) {
                                evidenceIds.add(targetId);
                            }
                            if ("SUSPECT_UNLOCK".equals(type) && targetId != null) {
                                suspectIds.add(targetId);
                            }
                            if ("SUSPECT_UPDATE".equals(type) && targetId != null) {
                                updatedSuspectIds.add(targetId);
                            }
                            if ("PHOTO_UNLOCK".equals(type) && targetId != null) {
                                photoIds.add(targetId);
                            }
                            if ("MEMO_UNLOCK".equals(type) && targetId != null) {
                                memoIds.add(targetId);
                            }
                            items.add(PuzzleSubmitResponse.UnlockedCaseFileItem.builder()
                                    .rewardType(type)
                                    .itemType(itemType(type))
                                    .targetId(targetId)
                                    .build());
                        }
                    }
                    payloadApplied = true;
                }
            } catch (Exception ignored) {
                payloadApplied = false;
            }
        }

        if (!payloadApplied) {
            addRewardClue(progress, spot, puzzle.getRewardClue());
            rewardTypes.add(rewardTypeForSpot(spot));
        }

        return new RewardApplyResult(!rewardTypes.isEmpty(), rewardTypes, evidenceIds, suspectIds, updatedSuspectIds, photoIds, memoIds, items);
    }

    private boolean applyReward(UserEpisodeProgress progress, MissionSpot spot, String type, String value, String slotId, Long targetId) {
        return switch (type) {
            case "ANSWER_CLUE" -> addStringReward(progress.getCollectedAnswerClues(), typedClueValue(slotId.isBlank() ? inferAnswerSlot(spot, value) : slotId, value), progress::setCollectedAnswerClues);
            case "SUSPECT_CLUE" -> addStringReward(progress.getCollectedAnswerClues(), typedClueValue("RELATED_PERSON", value), progress::setCollectedAnswerClues);
            case "DESTINATION_CLUE" -> addStringReward(progress.getCollectedAnswerClues(), typedClueValue(slotId.isBlank() ? inferAnswerSlot(spot, value) : slotId, value), progress::setCollectedAnswerClues);
            case "STORY_CLUE" -> addStringReward(progress.getCollectedStoryClues(), value, progress::setCollectedStoryClues);
            case "EVIDENCE_UNLOCK", "PHOTO_UNLOCK" -> targetId != null && addLongReward(progress.getUnlockedEvidenceIds(), targetId, progress::setUnlockedEvidenceIds);
            case "MEMO_UNLOCK" -> applyMemoUnlock(progress, value, targetId);
            case "SUSPECT_UNLOCK" -> targetId != null && addLongReward(progress.getUnlockedSuspectIds(), targetId, progress::setUnlockedSuspectIds);
            case "SUSPECT_UPDATE" -> applySuspectUpdate(progress, targetId);
            default -> false;
        };
    }

    private String rewardValueWithLetterReveal(String value, JsonNode reward, String type) {
        if (reward == null || !"ANSWER_CLUE".equals(type)) {
            return value;
        }
        JsonNode letterReveal = reward.path("letterReveal");
        if (!letterReveal.isObject()) {
            return value;
        }
        String revealed = sanitizeCategoryCodes(letterReveal.path("revealedText").asText(""));
        if (revealed.isBlank()) {
            return value;
        }
        String clean = sanitizeCategoryCodes(fallbackText(value, "").trim());
        return clean.isBlank() ? "怨듦컻 湲?? " + revealed : clean + "\n怨듦컻 湲?? " + revealed;
    }

    private String rewardTypeForSpot(MissionSpot spot) {
        return rewardTypeForSpot(spot, "", "");
    }

    private String rewardTypeForSpot(MissionSpot spot, String requestedType, String slotId) {
        String role = spot == null ? "" : spot.getClueRole();
        return switch (role) {
            case "ANSWER_HINT" -> "RELATED_PERSON".equals(normalizeRewardSlot(slotId)) || "SUSPECT_CLUE".equals(requestedType) ? "SUSPECT_CLUE" : "ANSWER_CLUE";
            case "DESTINATION_HINT" -> "ANSWER_CLUE";
            case "FINAL_PLACE" -> "STORY_CLUE";
            default -> "STORY_CLUE";
        };
    }

    private List<String> typedClues(List<String> values, String slotId, boolean includeLegacyRelated) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (hasClueSlot(value, slotId)) {
                result.add(clueValueWithoutSlot(value));
            } else if (!hasAnyClueSlot(value) && isLegacyClueForSlot(value, i, slotId, includeLegacyRelated)) {
                result.add(clueValueWithoutSlot(value));
            }
        }
        return result;
    }

    private boolean isLegacyClueForSlot(String value, int index, String slotId, boolean includeLegacyRelated) {
        if ("RELATED_PERSON".equals(slotId)) {
            return includeLegacyRelated && (index % 2 == 0 || containsAny(value, "관계자", "용의자", "진술", "알리바이", "목격"));
        }
        if ("ANSWER_CLUE".equals(slotId)) {
            return !isLegacyClueForSlot(value, index, "RELATED_PERSON", true);
        }
        return false;
    }

    private String inferAnswerSlot(MissionSpot spot, String clue) {
        String marker = normalize(spot == null ? null : spot.getPublicMarkerType());
        if ("KEYWORD_1".equals(marker)) return "CULPRIT";
        if ("KEYWORD_2".equals(marker)) return "WEAPON";
        int order = spot == null || spot.getId() == null ? normalize(clue).hashCode() : spot.getId().intValue();
        return switch (Math.floorMod(order, 4)) {
            case 0 -> "CULPRIT";
            case 1 -> "WEAPON";
            case 2 -> "MOTIVE";
            default -> "METHOD";
        };
    }
    private String normalizeRewardSlot(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "CULPRIT", "WEAPON", "MOTIVE", "METHOD", "RELATED_PERSON", "ANSWER_CLUE" -> normalized;
            default -> "";
        };
    }

    private String typedClueValue(String slotId, String value) {
        String clean = sanitizeCategoryCodes(fallbackText(value, "").trim());
        String normalized = normalizeRewardSlot(slotId);
        return normalized.isBlank() || clean.isBlank() ? clean : normalized + "::" + clean;
    }

    private boolean hasClueSlot(String value, String slotId) {
        return value != null && value.startsWith(slotId + "::");
    }

    private boolean hasAnyClueSlot(String value) {
        return value != null && (value.startsWith("CULPRIT::")
                || value.startsWith("WEAPON::")
                || value.startsWith("MOTIVE::")
                || value.startsWith("METHOD::")
                || value.startsWith("RELATED_PERSON::")
                || value.startsWith("ANSWER_CLUE::")
                );
    }

    private String clueValueWithoutSlot(String value) {
        String text = value == null ? "" : value;
        int marker = text.indexOf("::");
        return marker < 0 ? text : text.substring(marker + 2);
    }

    private boolean applyMemoUnlock(UserEpisodeProgress progress, String value, Long targetId) {
        boolean evidenceAdded = targetId != null && addLongReward(progress.getUnlockedEvidenceIds(), targetId, progress::setUnlockedEvidenceIds);
        boolean memoAdded = addStringReward(progress.getCollectedStoryClues(), value, progress::setCollectedStoryClues);
        return evidenceAdded || memoAdded;
    }

    private boolean applySuspectUpdate(UserEpisodeProgress progress, Long targetId) {
        if (targetId == null) {
            return false;
        }
        boolean unlocked = addLongReward(progress.getUnlockedSuspectIds(), targetId, progress::setUnlockedSuspectIds);
        boolean updated = addLongReward(progress.getClearedSuspectIds(), targetId, progress::setClearedSuspectIds);
        return unlocked || updated;
    }

    private boolean isEvidenceUnlock(String type) {
        return "EVIDENCE_UNLOCK".equals(type) || "PHOTO_UNLOCK".equals(type) || "MEMO_UNLOCK".equals(type);
    }

    private boolean isClueReward(String type) {
        return "SUSPECT_CLUE".equals(type) || "ANSWER_CLUE".equals(type) || "DESTINATION_CLUE".equals(type) || "STORY_CLUE".equals(type);
    }

    private String slotIdForRewardType(String type) {
        return switch (type) {
            case "SUSPECT_CLUE" -> "RELATED_PERSON";
            case "ANSWER_CLUE" -> "ANSWER_CLUE";
            case "DESTINATION_CLUE" -> "ANSWER_CLUE";
            default -> "";
        };
    }

    private String itemType(String rewardType) {
        return switch (rewardType) {
            case "SUSPECT_UNLOCK" -> "SUSPECT";
            case "SUSPECT_UPDATE" -> "SUSPECT_UPDATE";
            case "PHOTO_UNLOCK" -> "PHOTO";
            case "MEMO_UNLOCK" -> "MEMO";
            case "EVIDENCE_UNLOCK" -> "EVIDENCE";
            case "SUSPECT_CLUE", "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE" -> "CLUE";
            default -> "UNKNOWN";
        };
    }

    private boolean addStringReward(String json, String value, Consumer<String> setter) {
        if (value == null || value.isBlank()) {
            return false;
        }
        List<String> values = new ArrayList<>(readStringList(json));
        if (values.contains(value)) {
            return false;
        }
        values.add(value);
        setter.accept(writeJson(values));
        return true;
    }

    private boolean addLongReward(String json, Long value, Consumer<String> setter) {
        List<Long> values = new ArrayList<>(readLongList(json));
        if (values.contains(value)) {
            return false;
        }
        values.add(value);
        setter.accept(writeJson(values));
        return true;
    }

    private String addString(String json, String value) {
        List<String> values = new ArrayList<>(readStringList(json));
        if (!values.contains(value)) {
            values.add(value);
        }
        return writeJson(values);
    }

    private DeductionAnswer answerDeductionQuestion(Episode episode, UserEpisodeProgress progress, String question) {
        String normalizedQuestion = normalizeAnswer(question);
        if (acceptedFinalAnswers(episode).stream().anyMatch(normalizedQuestion::contains)) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "정답값을 직접 확인하는 질문에는 답할 수 없습니다. 수집한 단서를 조합해 추론하세요.");
        }
        if (containsAny(normalizedQuestion, "finalplace", "actualplace", "answerplace", "destination", "where", "정답장소", "장소정답")) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "최종 장소는 정답 추리 대상이 아닙니다. 조사 단서를 모두 완료하면 자동 공개됩니다.");
        }
        if (allClues(progress).size() < 2) {
            return new DeductionAnswer("INSUFFICIENT_CLUE", "단서가 부족합니다. 먼저 조사 장소의 사건 단서를 더 수집하세요.");
        }
        if (containsAny(normalizedQuestion, "culprit", "범인", "용의자", "알리바이", "지문")) {
            return new DeductionAnswer("RELATED", "범인 추론과 관련 있습니다. 알리바이, 접근 권한, 지문 단서를 함께 비교하세요.");
        }
        if (containsAny(normalizedQuestion, "weapon", "흉기", "독", "캡슐", "약")) {
            return new DeductionAnswer("PARTIAL", "흉기 추론과 관련 있습니다. 도구 자체와 사용 방법을 분리해 확인하세요.");
        }
        if (containsAny(normalizedQuestion, "motive", "동기", "해고", "분쟁", "상속")) {
            return new DeductionAnswer("RELATED", "동기 추론과 관련 있습니다. 이익을 얻는 사람과 실제 실행 가능성을 분리해 보세요.");
        }
        if (question.length() < 6) {
            return new DeductionAnswer("AMBIGUOUS", "질문이 모호합니다. 범인, 흉기, 동기, 방법 중 하나를 구체적으로 물어보세요.");
        }
        return new DeductionAnswer("AMBIGUOUS", "어떤 단서와 연결되는지 더 구체적으로 질문해 주세요.");
    }
    private DeductionAnswer sanitizeDeductionAnswer(Episode episode, DeductionAnswer answer) {
        String type = ALLOWED_DEDUCTION_ANSWER_TYPES.contains(answer.type()) ? answer.type() : "AMBIGUOUS";
        String text = localizeDeductionAnswer(type, answer.text());
        String normalizedText = normalizeAnswer(text);
        boolean revealsAnswer = acceptedFinalAnswers(episode).stream()
                .filter(value -> !value.isBlank())
                .anyMatch(normalizedText::contains);
        boolean revealsFinalPlace = containsAny(normalizedText, "finalplace", "actualplace", "answerplace", "理쒖쥌?μ냼", "?ㅼ젣?μ냼", "?뺣떟?μ냼");
        if (revealsAnswer || revealsFinalPlace) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "?뺣떟?대굹 議곗궗 吏?먯쓣 吏곸젒 ?몄텧?????덉뼱 ?듬??????놁뒿?덈떎.");
        }
        return new DeductionAnswer(type, text);
    }

    private String localizeDeductionAnswer(String type, String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return fallbackDeductionAnswer(type);
        }
        String normalized = value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "that question would reveal the answer directly, so i cannot answer it." ->
                    "洹?吏덈Ц? ?뺣떟??吏곸젒 ?몄텧?????덉뼱 ?듬??????놁뒿?덈떎.";
            case "that question would reveal the investigation site directly, so i cannot answer it." ->
                    "洹?吏덈Ц? 議곗궗 ?μ냼瑜?吏곸젒 ?몄텧?????덉뼱 ?듬??????놁뒿?덈떎.";
            case "that question would reveal the answer or investigation site directly, so i cannot answer it." ->
                    "?뺣떟?대굹 議곗궗 吏?먯쓣 吏곸젒 ?몄텧?????덉뼱 ?듬??????놁뒿?덈떎.";
            case "not enough clues. investigate more answer-hint sites first." ->
                    "?⑥꽌媛 遺議깊빀?덈떎. 癒쇱? 愿?⑥옄/?듭떖 ?⑥꽌 誘몄뀡????議곗궗??二쇱꽭??";
            case "related. compare it with the answer-hint clues." ->
                    "愿???덉뒿?덈떎. ?듭떖 ?⑥꽌 ?뚰듃?ㅺ낵 ?④퍡 鍮꾧탳??蹂댁꽭??";
            case "partially correct. the direction is right, but the final answer is a more specific object." ->
                    "遺遺꾩쟻?쇰줈 留욎뒿?덈떎. 諛⑺뼢? 留욎?留?理쒖쥌 ?뺣떟? ??援ъ껜?곸씤 臾쇨굔?낅땲??";
            case "no. it does not directly match the collected clues." ->
                    "?꾨떃?덈떎. ?섏쭛???⑥꽌? 吏곸젒 留욌Ъ由ъ????딆뒿?덈떎.";
            case "the question is ambiguous. ask again about a suspect, motive, or evidence." ->
                    "吏덈Ц??紐⑦샇?⑸땲?? ?⑹쓽?? ?숆린, 利앷굅 以??섎굹瑜???援ъ껜?곸쑝濡?臾쇱뼱遊?二쇱꽭??";
            case "the question is ambiguous. ask more specifically which collected clue it connects to." ->
                    "吏덈Ц??紐⑦샇?⑸땲?? ?대뼡 ?섏쭛 ?⑥꽌? ?곌껐?섎뒗吏 ??援ъ껜?곸쑝濡?臾쇱뼱遊?二쇱꽭??";
            default -> isEnglishSentence(value) ? fallbackDeductionAnswer(type) : value;
        };
    }

    private String fallbackDeductionAnswer(String type) {
        return switch (type) {
            case "YES", "RELATED" -> "愿???덉뒿?덈떎. ?섏쭛???⑥꽌? ?④퍡 鍮꾧탳??蹂댁꽭??";
            case "NO", "NOT_RELATED" -> "?꾨떃?덈떎. ?섏쭛???⑥꽌? 吏곸젒 留욌Ъ由ъ????딆뒿?덈떎.";
            case "PARTIAL" -> "遺遺꾩쟻?쇰줈 留욎뒿?덈떎. 諛⑺뼢? 留욎?留???援ъ껜?곸씤 ?⑥꽌媛 ?꾩슂?⑸땲??";
            case "INSUFFICIENT_CLUE" -> "?⑥꽌媛 遺議깊빀?덈떎. 癒쇱? 愿?⑥옄/?듭떖 ?⑥꽌 誘몄뀡????議곗궗??二쇱꽭??";
            case "REFUSED_DIRECT_REVEAL" -> "?뺣떟?대굹 議곗궗 吏?먯쓣 吏곸젒 ?몄텧?????덉뼱 ?듬??????놁뒿?덈떎.";
            default -> "吏덈Ц??紐⑦샇?⑸땲?? ?대뼡 ?섏쭛 ?⑥꽌? ?곌껐?섎뒗吏 ??援ъ껜?곸쑝濡?臾쇱뼱遊?二쇱꽭??";
        };
    }

    private boolean isEnglishSentence(String value) {
        long alphabetCount = value == null ? 0 : value.chars()
                .filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))
                .count();
        boolean hasHangul = value != null && value.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
        return alphabetCount >= 3 && !hasHangul;
    }

    private boolean containsAny(String source, String... keywords) {
        if (source == null) {
            return false;
        }
        return Arrays.stream(keywords).anyMatch(source::contains);
    }
    private int calculateScore(UserEpisodeProgress progress) {
        int score = 1000;
        int answerClues = readStringList(progress.getCollectedAnswerClues()).size();
        int storyClues = readStringList(progress.getCollectedStoryClues()).size();
        if (answerClues >= 4) score += 200;
        if (storyClues > 0) score += 50;
        score -= value(progress.getDeductionQuestionCount()) * 10;
        score -= value(progress.getWrongAnswerCount()) * 50;
        if (answerClues + storyClues < 4) score -= 200;
        return Math.max(100, score);
    }

    private List<String> acceptedFinalAnswers(Episode episode) {
        Set<String> answers = new LinkedHashSet<>();
        answers.add(normalizeAnswer(episode.getFinalAnswer()));
        if (episode.getFinalAnswerAliases() != null) {
            Arrays.stream(episode.getFinalAnswerAliases().split(","))
                    .filter(value -> !value.trim().startsWith("KW:"))
                    .map(this::normalizeAnswer)
                    .filter(value -> !value.isBlank())
                    .forEach(answers::add);
        }
        return new ArrayList<>(answers);
    }

    private boolean isFinalAnswerCorrect(Episode episode, String submittedAnswer) {
        String normalizedSubmitted = normalizeAnswer(submittedAnswer);
        if (acceptedFinalAnswers(episode).contains(normalizedSubmitted)) {
            return true;
        }
        List<String> requiredKeywords = requiredFinalAnswerKeywords(episode);
        return !requiredKeywords.isEmpty()
                && requiredKeywords.stream()
                .map(this::normalizeAnswer)
                .filter(value -> !value.isBlank())
                .allMatch(normalizedSubmitted::contains);
    }

    private List<String> requiredFinalAnswerKeywords(Episode episode) {
        if (episode.getFinalAnswerAliases() == null) {
            return List.of();
        }
        return Arrays.stream(episode.getFinalAnswerAliases().split(","))
                .map(String::trim)
                .filter(value -> value.startsWith("KW:"))
                .flatMap(value -> Arrays.stream(value.substring(3).split("\\|")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> allClues(UserEpisodeProgress progress) {
        return Stream.of(readStringList(progress.getCollectedAnswerClues()), readStringList(progress.getCollectedDestinationClues()), readStringList(progress.getCollectedStoryClues()))
                .flatMap(List::stream)
                .map(this::clueValueWithoutSlot)
                .toList();
    }

    private String normalizeAnswer(String value) {
        return AnswerNormalizer.normalize(value);
    }

    private int maxQuestions(Episode episode) {
        return episode.getMaxDeductionQuestions() == null ? 20 : episode.getMaxDeductionQuestions();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeRadius(Double radius) {
        return radius == null || radius <= 0 ? 50.0 : radius;
    }

    private void requireCoordinatesUnlessDevMode(ArriveRequest request, boolean devMode) {
        if (!devMode && (request == null || request.getUserLat() == null || request.getUserLng() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED", "?꾩옱 ?꾩튂 醫뚰몴媛 ?꾩슂?⑸땲??");
        }
    }

    private double calculateDistanceMeters(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.MAX_VALUE;
        }
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private List<Long> readLongList(String json) {
        return readJsonList(json, LONG_LIST);
    }

    private List<String> readStringList(String json) {
        return readJsonList(json, STRING_LIST);
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record DeductionAnswer(String type, String text) {
    }

    private record RewardApplyResult(boolean caseFileUpdated, List<String> rewardTypes, List<Long> evidenceIds,
                                     List<Long> suspectIds, List<Long> updatedSuspectIds, List<Long> photoIds,
                                     List<Long> memoIds, List<PuzzleSubmitResponse.UnlockedCaseFileItem> items) {
    }

}



