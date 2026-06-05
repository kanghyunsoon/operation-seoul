package com.operation.seoul.episode.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.episode.domain.*;
import com.operation.seoul.episode.dto.*;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.favorite.repository.EpisodeFavoriteRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EpisodePlayService {
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> ALLOWED_DEDUCTION_ANSWER_TYPES = Set.of(
            "YES", "NO", "RELATED", "NOT_RELATED", "PARTIAL", "AMBIGUOUS", "INSUFFICIENT_CLUE", "REFUSED_DIRECT_REVEAL"
    );

    private final EpisodeRepository episodeRepository;
    private final EpisodeFavoriteRepository favoriteRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.dev-mode.arrival-enabled:false}")
    private boolean arrivalDevModeEnabled;

    public List<EpisodeListItemResponse> getEpisodes(User user) {
        Set<Long> favoriteEpisodeIds = new LinkedHashSet<>(favoriteRepository.findEpisodeIdsByUserId(user.getId()));
        return episodeRepository.findPublishedEpisodes().stream()
                .map(episode -> EpisodeListItemResponse.builder()
                        .id(episode.getId())
                        .title(episode.getTitle())
                        .subtitle(episode.getSubtitle())
                        .era(episode.getEra())
                        .genre(episode.getGenre())
                        .difficulty(episode.getDifficulty())
                        .estimatedTime(episode.getEstimatedTime())
                        .estimatedDistance(episode.getEstimatedDistance())
                        .favorited(favoriteEpisodeIds.contains(episode.getId()))
                        .build())
                .toList();
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
                .estimatedTime(episode.getEstimatedTime())
                .estimatedDistance(episode.getEstimatedDistance())
                .fictionSynopsis(episode.getFictionSynopsis())
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

        return EpisodeMapResponse.builder()
                .episodeId(episode.getId())
                .title(episode.getTitle())
                .progressStatus(progress.getStatus())
                .hintUsedCount(value(progress.getHintUsedCount()))
                .wrongAnswerCount(value(progress.getWrongAnswerCount()))
                .deductionQuestionCount(value(progress.getDeductionQuestionCount()))
                .spots(episodeRepository.findSpotsByEpisodeId(episodeId).stream()
                        .map(spot -> SpotMarkerResponse.builder()
                                .spotId(spot.getId())
                                .placeName(spot.getPlaceName())
                                .address(spot.getAddress())
                                .latitude(spot.getLatitude())
                                .longitude(spot.getLongitude())
                                .publicMarkerType(spot.getPublicMarkerType())
                                .storyText(spot.getStoryText())
                                .visited(visited.contains(spot.getId()))
                                .completed(completed.contains(spot.getId()))
                                .rewardClueCollected(completed.contains(spot.getId()))
                                .canOpenPuzzle(visited.contains(spot.getId()))
                                .canNavigate(true)
                                .build())
                        .toList())
                .build();
    }

    public ArriveResponse arrive(Long episodeId, Long spotId, ArriveRequest request, User user) {
        requireEpisode(episodeId);
        MissionSpot spot = requireSpot(spotId, episodeId);
        UserEpisodeProgress progress = ensureProgress(user.getId(), episodeId);
        if (Boolean.TRUE.equals(request.getDevMode()) && !arrivalDevModeEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEV_ARRIVAL_DISABLED", "Dev arrival is disabled on this server.");
        }
        boolean devMode = Boolean.TRUE.equals(request.getDevMode());
        double distance = devMode ? 0.0 : calculateDistanceMeters(request.getUserLat(), request.getUserLng(), spot.getLatitude(), spot.getLongitude());
        boolean arrived = devMode || distance <= safeRadius(spot.getArrivalRadius());

        if (!arrived) {
            return ArriveResponse.builder()
                    .arrived(false)
                    .distance(distance)
                    .canOpenPuzzle(false)
                    .isActualFinalArrived(false)
                    .canStartDeduction(false)
                    .message("아직 도착 범위 밖입니다. 현장에 더 가까이 이동해 주세요.")
                    .build();
        }

        addLong(progress, "visited", spotId);
        boolean actualFinal = Boolean.TRUE.equals(spot.getFinalPlace());
        if (actualFinal) {
            progress.setFinalArrivedSpotId(spotId);
            progress.setStatus("FINAL_READY");
        }
        episodeRepository.updateProgress(progress);

        String message = actualFinal
                ? "실제 최종 장소에 도착했습니다. 단서가 부족해도 최종 추리를 시작할 수 있지만 점수 페널티가 적용될 수 있습니다."
                : ("FINAL_CANDIDATE".equals(spot.getPublicMarkerType())
                    ? "이 장소에서는 최종 추리를 시작할 수 없습니다. 목적지 힌트를 다시 확인해 주세요."
                    : "도착이 확인되었습니다. 현장 퍼즐을 열 수 있습니다.");

        return ArriveResponse.builder()
                .arrived(true)
                .distance(distance)
                .canOpenPuzzle(true)
                .isActualFinalArrived(actualFinal)
                .canStartDeduction(actualFinal)
                .message(message)
                .build();
    }

    public PuzzleResponse getPuzzle(Long spotId, User user) {
        MissionSpot spot = requireSpot(spotId, null);
        UserEpisodeProgress progress = requireProgress(user.getId(), spot.getEpisodeId());
        if (!readLongList(progress.getVisitedSpotIds()).contains(spotId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARRIVAL_REQUIRED", "장소에 도착해야 퍼즐을 열 수 있습니다.");
        }
        Puzzle puzzle = episodeRepository.findPuzzleBySpotId(spotId);
        if (puzzle == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "퍼즐을 찾을 수 없습니다.");
        }
        return PuzzleResponse.builder()
                .puzzleId(puzzle.getId())
                .spotId(spotId)
                .puzzleType(puzzle.getPuzzleType())
                .questionText(puzzle.getQuestionText())
                .answerFormat(puzzle.getAnswerFormat())
                .difficulty(puzzle.getDifficulty())
                .hints(episodeRepository.findHintsByPuzzleId(puzzle.getId()).stream().map(PuzzleHint::getHintText).toList())
                .build();
    }

    public PuzzleSubmitResponse submitPuzzle(Long puzzleId, PuzzleSubmitRequest request, User user) {
        Puzzle puzzle = episodeRepository.findPuzzleById(puzzleId);
        if (puzzle == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PUZZLE_NOT_FOUND", "퍼즐을 찾을 수 없습니다.");
        }
        MissionSpot spot = requireSpot(puzzle.getMissionSpotId(), null);
        UserEpisodeProgress progress = requireProgress(user.getId(), spot.getEpisodeId());
        if (!readLongList(progress.getVisitedSpotIds()).contains(spot.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARRIVAL_REQUIRED", "장소에 도착해야 퍼즐을 풀 수 있습니다.");
        }

        boolean correct = normalizeAnswer(puzzle.getAnswer()).equals(normalizeAnswer(request.getAnswer()));
        if (!correct) {
            progress.setWrongAnswerCount(value(progress.getWrongAnswerCount()) + 1);
            episodeRepository.updateProgress(progress);
            return PuzzleSubmitResponse.builder()
                    .correct(false)
                    .message("정답이 아닙니다. 현장 단서와 힌트를 다시 확인해 주세요.")
                    .clueBoard(buildClueBoard(progress))
                    .build();
        }

        addLong(progress, "completed", spot.getId());
        RewardApplyResult rewardResult = applyPuzzleRewards(progress, spot, puzzle);
        episodeRepository.updateProgress(progress);
        return PuzzleSubmitResponse.builder()
                .correct(true)
                .rewardClue(puzzle.getRewardClue())
                .caseFileUpdated(rewardResult.caseFileUpdated())
                .unlockedRewardTypes(rewardResult.rewardTypes())
                .unlockedEvidenceIds(rewardResult.evidenceIds())
                .unlockedSuspectIds(rewardResult.suspectIds())
                .updatedSuspectIds(rewardResult.updatedSuspectIds())
                .unlockedPhotoIds(rewardResult.photoIds())
                .unlockedMemoIds(rewardResult.memoIds())
                .newlyUnlockedItems(rewardResult.items())
                .message(rewardResult.caseFileUpdated()
                        ? "정답입니다. 단서와 새 사건 자료가 사건파일에 추가되었습니다."
                        : "정답입니다. 단서가 단서 보드에 저장되었습니다.")
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
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_PROGRESS", "최종 추리를 시작할 수 없는 진행 상태입니다.");
        }
        MissionSpot finalSpot = episodeRepository.findSpotsByEpisodeId(episodeId).stream()
                .filter(spot -> Boolean.TRUE.equals(spot.getFinalPlace()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FINAL_SPOT_NOT_FOUND", "최종 장소를 찾을 수 없습니다."));
        if (!finalSpot.getId().equals(progress.getFinalArrivedSpotId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_ARRIVAL_REQUIRED", "실제 최종 장소에 도착해야 최종 추리를 시작할 수 있습니다.");
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
                ? "단서가 부족합니다. 정답 힌트 장소를 더 조사하면 더 정확한 질문을 할 수 있습니다."
                : "최종 추리 채팅을 시작합니다.";
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
            throw new ApiException(HttpStatus.FORBIDDEN, "DEDUCTION_LIMIT_EXCEEDED", "질문 횟수를 모두 사용했습니다.");
        }
        UserEpisodeProgress progress = requireProgress(user.getId(), session.getEpisodeId());
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "질문을 입력해 주세요.");
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
                        .aiAnswerText(question.getAiAnswerText())
                        .createdAt(question.getCreatedAt())
                        .build())
                .toList();
    }

    public FinalAnswerResponse submitFinalAnswer(Long episodeId, FinalAnswerRequest request, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = requireProgress(user.getId(), episodeId);
        if (progress.getFinalArrivedSpotId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FINAL_ARRIVAL_REQUIRED", "실제 최종 장소에 도착해야 최종 정답을 제출할 수 있습니다.");
        }
        FinalDeductionSession session = request.getSessionId() == null ? null : requireSession(request.getSessionId(), user);
        boolean correct = acceptedFinalAnswers(episode).contains(normalizeAnswer(request.getFinalAnswer()));
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
                    .message("정답이 아닙니다. 단서 보드와 질문 기록을 다시 확인해 주세요.")
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
                .message("사건을 해결했습니다. 클리어 리포트가 열렸습니다.")
                .build();
    }
    public ClearReportResponse getClearReport(Long episodeId, User user) {
        Episode episode = requireEpisode(episodeId);
        UserEpisodeProgress progress = requireProgress(user.getId(), episodeId);
        if (!"CLEARED".equals(progress.getStatus()) || progress.getClearedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLEAR_REQUIRED", "클리어한 에피소드의 리포트만 확인할 수 있습니다.");
        }
        List<Long> visitedSpotIds = readLongList(progress.getVisitedSpotIds());
        List<Long> completedSpotIds = readLongList(progress.getCompletedSpotIds());
        List<String> answerClues = readStringList(progress.getCollectedAnswerClues());
        List<String> destinationClues = readStringList(progress.getCollectedDestinationClues());
        List<String> storyClues = readStringList(progress.getCollectedStoryClues());
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
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "에피소드를 찾을 수 없습니다.");
        }
        return episode;
    }

    private MissionSpot requireSpot(Long spotId, Long episodeId) {
        MissionSpot spot = episodeRepository.findSpotById(spotId);
        if (spot == null || (episodeId != null && !episodeId.equals(spot.getEpisodeId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SPOT_NOT_FOUND", "장소를 찾을 수 없습니다.");
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
            throw new ApiException(HttpStatus.FORBIDDEN, "EPISODE_NOT_STARTED", "에피소드를 먼저 시작해 주세요.");
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
            throw new ApiException(HttpStatus.NOT_FOUND, "DEDUCTION_SESSION_NOT_FOUND", "최종 추리 세션을 찾을 수 없습니다.");
        }
        if (!"OPEN".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEDUCTION_SESSION_CLOSED", "종료된 최종 추리 세션입니다.");
        }
        return session;
    }

    private ClueBoardResponse buildClueBoard(UserEpisodeProgress progress) {
        List<String> answer = readStringList(progress.getCollectedAnswerClues());
        List<String> destination = readStringList(progress.getCollectedDestinationClues());
        List<String> story = readStringList(progress.getCollectedStoryClues());
        return ClueBoardResponse.builder()
                .episodeId(progress.getEpisodeId())
                .answerClues(answer)
                .destinationClues(destination)
                .storyClues(story)
                .visitedSpotIds(readLongList(progress.getVisitedSpotIds()))
                .completedSpotIds(readLongList(progress.getCompletedSpotIds()))
                .unlockedSuspectIds(readLongList(progress.getUnlockedSuspectIds()))
                .clearedSuspectIds(readLongList(progress.getClearedSuspectIds()))
                .unlockedEvidenceIds(readLongList(progress.getUnlockedEvidenceIds()))
                .answerClueCount(answer.size())
                .destinationClueCount(destination.size())
                .storyClueCount(story.size())
                .build();
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
        if ("ANSWER_HINT".equals(spot.getClueRole())) {
            progress.setCollectedAnswerClues(addString(progress.getCollectedAnswerClues(), clue));
        } else if ("DESTINATION_HINT".equals(spot.getClueRole()) || "FINAL_PLACE".equals(spot.getClueRole())) {
            progress.setCollectedDestinationClues(addString(progress.getCollectedDestinationClues(), clue));
        } else {
            progress.setCollectedStoryClues(addString(progress.getCollectedStoryClues(), clue));
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
                        String type = reward.path("type").asText("");
                        String value = reward.path("value").asText("");
                        Long targetId = reward.hasNonNull("targetId") ? reward.path("targetId").asLong() : null;
                        boolean added = applyReward(progress, type, value, targetId);
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
            rewardTypes.add(spot.getClueRole());
        }

        return new RewardApplyResult(!rewardTypes.isEmpty(), rewardTypes, evidenceIds, suspectIds, updatedSuspectIds, photoIds, memoIds, items);
    }

    private boolean applyReward(UserEpisodeProgress progress, String type, String value, Long targetId) {
        return switch (type) {
            case "ANSWER_CLUE" -> addStringReward(progress.getCollectedAnswerClues(), value, progress::setCollectedAnswerClues);
            case "DESTINATION_CLUE" -> addStringReward(progress.getCollectedDestinationClues(), value, progress::setCollectedDestinationClues);
            case "STORY_CLUE" -> addStringReward(progress.getCollectedStoryClues(), value, progress::setCollectedStoryClues);
            case "EVIDENCE_UNLOCK", "PHOTO_UNLOCK" -> targetId != null && addLongReward(progress.getUnlockedEvidenceIds(), targetId, progress::setUnlockedEvidenceIds);
            case "MEMO_UNLOCK" -> applyMemoUnlock(progress, value, targetId);
            case "SUSPECT_UNLOCK" -> targetId != null && addLongReward(progress.getUnlockedSuspectIds(), targetId, progress::setUnlockedSuspectIds);
            case "SUSPECT_UPDATE" -> applySuspectUpdate(progress, targetId);
            default -> false;
        };
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

    private String itemType(String rewardType) {
        return switch (rewardType) {
            case "SUSPECT_UNLOCK" -> "SUSPECT";
            case "SUSPECT_UPDATE" -> "SUSPECT_UPDATE";
            case "PHOTO_UNLOCK" -> "PHOTO";
            case "MEMO_UNLOCK" -> "MEMO";
            case "EVIDENCE_UNLOCK" -> "EVIDENCE";
            case "ANSWER_CLUE", "DESTINATION_CLUE", "STORY_CLUE" -> "CLUE";
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
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "그 질문은 정답을 직접 노출할 수 있어 답할 수 없습니다.");
        }
        if (containsAny(normalizedQuestion, "최종장소", "실제장소", "중명전", "정답장소", "목적지어디", "어디로가", "어디야", "어디")) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "그 질문은 최종 장소를 직접 노출할 수 있어 답할 수 없습니다.");
        }
        if (containsAny(normalizedQuestion, "정답", "답뭐", "답이뭐", "답알려", "무엇", "뭐야", "뭐냐", "알려줘", "흉기뭐", "범인누구")) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "그 질문은 정답을 직접 노출할 수 있어 답할 수 없습니다.");
        }
        if (allClues(progress).size() < 2) {
            return new DeductionAnswer("INSUFFICIENT_CLUE", "단서가 부족합니다. 정답 힌트 장소를 더 조사해 보세요.");
        }
        if (containsAny(normalizedQuestion, "렌즈", "유리", "사진", "카메라", "반사", "조각")) {
            return new DeductionAnswer("RELATED", "관련 있음. 특히 정답 힌트 단서와 함께 봐야 합니다.");
        }
        if (containsAny(normalizedQuestion, "피해자", "사진사", "마지막필름", "붉은벽")) {
            return new DeductionAnswer("PARTIAL", "부분적으로 맞음. 방향은 맞지만 최종 정답은 더 구체적인 물건입니다.");
        }
        if (containsAny(normalizedQuestion, "금속", "남자", "총", "칼", "독")) {
            return new DeductionAnswer("NO", "아니오. 수집한 단서와는 직접 맞지 않습니다.");
        }
        if (question.length() < 6) {
            return new DeductionAnswer("AMBIGUOUS", "질문이 모호함. 범인, 흉기, 증거 중 무엇에 대한 질문인지 다시 물어보세요.");
        }
        return new DeductionAnswer("AMBIGUOUS", "질문이 모호함. 수집한 단서 중 어느 자료와 연결되는지 더 구체적으로 물어보세요.");
    }

    private DeductionAnswer sanitizeDeductionAnswer(Episode episode, DeductionAnswer answer) {
        String type = ALLOWED_DEDUCTION_ANSWER_TYPES.contains(answer.type()) ? answer.type() : "AMBIGUOUS";
        String text = answer.text() == null ? "" : answer.text();
        String normalizedText = normalizeAnswer(text);
        boolean revealsAnswer = acceptedFinalAnswers(episode).stream()
                .filter(value -> !value.isBlank())
                .anyMatch(normalizedText::contains);
        boolean revealsFinalPlace = containsAny(normalizedText, "중명전", "최종장소", "실제장소", "정답장소");
        if (revealsAnswer || revealsFinalPlace) {
            return new DeductionAnswer("REFUSED_DIRECT_REVEAL", "그 질문은 정답 또는 최종 장소를 직접 노출할 수 있어 답할 수 없습니다.");
        }
        return new DeductionAnswer(type, text);
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
        int destinationClues = readStringList(progress.getCollectedDestinationClues()).size();
        int storyClues = readStringList(progress.getCollectedStoryClues()).size();
        if (answerClues >= 4) score += 200;
        if (destinationClues >= 2) score += 150;
        if (storyClues > 0) score += 50;
        score -= value(progress.getDeductionQuestionCount()) * 10;
        score -= value(progress.getWrongAnswerCount()) * 50;
        if (answerClues + destinationClues + storyClues < 4) score -= 200;
        return Math.max(100, score);
    }

    private List<String> acceptedFinalAnswers(Episode episode) {
        Set<String> answers = new LinkedHashSet<>();
        answers.add(normalizeAnswer(episode.getFinalAnswer()));
        if (episode.getFinalAnswerAliases() != null) {
            Arrays.stream(episode.getFinalAnswerAliases().split(","))
                    .map(this::normalizeAnswer)
                    .filter(value -> !value.isBlank())
                    .forEach(answers::add);
        }
        return new ArrayList<>(answers);
    }

    private List<String> allClues(UserEpisodeProgress progress) {
        return Stream.of(readStringList(progress.getCollectedAnswerClues()), readStringList(progress.getCollectedDestinationClues()), readStringList(progress.getCollectedStoryClues()))
                .flatMap(List::stream)
                .toList();
    }

    private String normalizeAnswer(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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
