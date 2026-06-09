package com.operation.seoul.casefile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.dto.CaseFileResponse;
import com.operation.seoul.casefile.repository.CaseFileRepository;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseFileService {
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CaseFileRepository caseFileRepository;
    private final ObjectMapper objectMapper;

    public CaseFileResponse getCaseFile(Long episodeId, User user) {
        Episode episode = caseFileRepository.findEpisode(episodeId);
        if (episode == null || !"PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "에피소드를 찾을 수 없습니다.");
        }

        UserEpisodeProgress progress = caseFileRepository.findProgress(user.getId(), episodeId);
        List<Long> visitedSpotIds = readLongList(progress == null ? null : progress.getVisitedSpotIds());
        List<Long> completedSpotIds = readLongList(progress == null ? null : progress.getCompletedSpotIds());
        List<String> answerClues = readStringList(progress == null ? null : progress.getCollectedAnswerClues());
        List<String> destinationClues = readStringList(progress == null ? null : progress.getCollectedDestinationClues());
        List<String> storyClues = readStringList(progress == null ? null : progress.getCollectedStoryClues());
        Set<Long> unlockedSuspectIds = readLongList(progress == null ? null : progress.getUnlockedSuspectIds()).stream().collect(Collectors.toSet());
        Set<Long> clearedSuspectIds = readLongList(progress == null ? null : progress.getClearedSuspectIds()).stream().collect(Collectors.toSet());
        Set<Long> unlockedEvidenceIds = readLongList(progress == null ? null : progress.getUnlockedEvidenceIds()).stream().collect(Collectors.toSet());

        List<CaseSuspect> suspects = caseFileRepository.findSuspects(episodeId);
        List<CaseEvidence> evidences = caseFileRepository.findEvidences(episodeId);
        int totalSpotCount = caseFileRepository.countSpots(episodeId);
        Long startSpotId = caseFileRepository.findStartSpotId(episodeId);
        boolean storyUnlocked = (startSpotId != null && completedSpotIds.contains(startSpotId)) || !storyClues.isEmpty();

        return CaseFileResponse.builder()
                .episodeId(episode.getId())
                .title(episode.getTitle())
                .subtitle(episode.getSubtitle())
                .genre(episode.getGenre())
                .difficulty(episode.getDifficulty())
                .estimatedTime(episode.getEstimatedTime())
                .estimatedDistance(episode.getEstimatedDistance())
                .recommendedPlayers(episode.getRecommendedPlayers())
                .startRegion("서울 정동길")
                .progressStatus(progress == null ? "NOT_STARTED" : progress.getStatus())
                .finalQuestion(episode.getFinalQuestion())
                .overview(CaseFileResponse.Overview.builder()
                        .briefingTitle(briefingTitle(episode))
                        .summary(storyUnlocked ? detailedStorySummary(episode, storyClues) : lockedStorySummary(episode))
                        .lockedSummary(lockedStorySummary(episode))
                        .detailedSummary(detailedStorySummary(episode, storyClues))
                        .goal(caseGoal(episode))
                        .fictionSynopsis(episode.getFictionSynopsis())
                        .storyUnlocked(storyUnlocked)
                        .unlockedStoryClues(storyClues)
                        .build())
                .suspects(suspects.stream().map(suspect -> toSuspect(suspect, unlockedSuspectIds, clearedSuspectIds, evidences)).toList())
                .evidences(evidences.stream().map(evidence -> toEvidence(evidence, unlockedEvidenceIds)).toList())
                .clueSummary(CaseFileResponse.ClueSummary.builder()
                        .answerClues(answerClues)
                        .destinationClues(destinationClues)
                        .storyClues(storyClues)
                        .build())
                .progressSummary(CaseFileResponse.ProgressSummary.builder()
                        .visitedSpotCount(visitedSpotIds.size())
                        .completedSpotCount(completedSpotIds.size())
                        .totalSpotCount(totalSpotCount)
                        .unlockedEvidenceCount(countUnlockedEvidences(evidences, unlockedEvidenceIds))
                        .totalEvidenceCount(evidences.size())
                        .unlockedSuspectCount(countUnlockedSuspects(suspects, unlockedSuspectIds))
                        .totalSuspectCount(suspects.size())
                        .hintUsedCount(progress == null || progress.getHintUsedCount() == null ? 0 : progress.getHintUsedCount())
                        .wrongAnswerCount(progress == null || progress.getWrongAnswerCount() == null ? 0 : progress.getWrongAnswerCount())
                        .deductionQuestionCount(progress == null || progress.getDeductionQuestionCount() == null ? 0 : progress.getDeductionQuestionCount())
                        .score(progress == null ? null : progress.getScore())
                        .build())
                .notices(splitLines(episode.getNoticeText()))
                .teamRoleGuide(episode.getTeamRoleGuide())
                .partnerRewards(caseFileRepository.findPartnerRewards(episodeId).stream()
                        .map(reward -> CaseFileResponse.PartnerReward.builder()
                                .title(reward.getTitle())
                                .description(reward.getDescription())
                                .rewardType(reward.getRewardType())
                                .partnerName(reward.getPartnerName())
                                .locationName(reward.getLocationName())
                                .status(reward.getStatus())
                                .build())
                        .toList())
                .answerLog(CaseFileResponse.AnswerLog.builder()
                        .visitedSpotIds(visitedSpotIds)
                        .completedSpotIds(completedSpotIds)
                        .finalArrivedSpotId(progress == null ? null : progress.getFinalArrivedSpotId())
                        .startedAt(progress == null || progress.getStartedAt() == null ? null : DATE_TIME.format(progress.getStartedAt()))
                        .lastPlayedAt(progress == null || progress.getLastPlayedAt() == null ? null : DATE_TIME.format(progress.getLastPlayedAt()))
                        .clearedAt(progress == null || progress.getClearedAt() == null ? null : DATE_TIME.format(progress.getClearedAt()))
                        .build())
                .build();
    }

    private CaseFileResponse.Suspect toSuspect(CaseSuspect suspect, Set<Long> unlockedIds, Set<Long> clearedIds, List<CaseEvidence> evidences) {
        boolean unlocked = Boolean.TRUE.equals(suspect.getUnlockedByDefault()) || unlockedIds.contains(suspect.getId());
        int relatedCount = (int) evidences.stream().filter(evidence -> suspect.getId().equals(evidence.getRelatedSuspectId())).count();
        return CaseFileResponse.Suspect.builder()
                .suspectId(suspect.getId())
                .displayName(unlocked ? suspect.getDisplayName() : "잠긴 용의자")
                .alias(suspect.getAlias())
                .shortDescription(unlocked ? suspect.getShortDescription() : "관련 증거를 획득하면 공개됩니다.")
                .portraitImageUrl(unlocked ? suspect.getPortraitImageUrl() : null)
                .relationToVictim(unlocked ? suspect.getRelationToVictim() : null)
                .suspiciousPoint(unlocked ? suspect.getSuspiciousPoint() : null)
                .alibiSummary(unlocked ? suspect.getAlibiSummary() : null)
                .unlocked(unlocked)
                .cleared(clearedIds.contains(suspect.getId()))
                .relatedClueCount(relatedCount)
                .build();
    }

    private CaseFileResponse.Evidence toEvidence(CaseEvidence evidence, Set<Long> unlockedIds) {
        boolean unlocked = Boolean.TRUE.equals(evidence.getUnlockedByDefault()) || unlockedIds.contains(evidence.getId());
        return CaseFileResponse.Evidence.builder()
                .evidenceId(evidence.getId())
                .title(unlocked ? evidence.getTitle() : "잠긴 사건 자료")
                .type(evidence.getType())
                .imageUrl(unlocked ? evidence.getImageUrl() : null)
                .textSummary(unlocked ? evidence.getTextSummary() : "현장 퍼즐을 해결하면 자료가 해금됩니다.")
                .sourceSpotId(unlocked ? evidence.getSourceSpotId() : null)
                .relatedSuspectIds(unlocked && evidence.getRelatedSuspectId() != null ? List.of(evidence.getRelatedSuspectId()) : List.of())
                .relatedClueType(evidence.getRelatedClueType())
                .unlocked(unlocked)
                .build();
    }

    private int countUnlockedSuspects(List<CaseSuspect> suspects, Set<Long> unlockedIds) {
        return (int) suspects.stream().filter(suspect -> Boolean.TRUE.equals(suspect.getUnlockedByDefault()) || unlockedIds.contains(suspect.getId())).count();
    }

    private int countUnlockedEvidences(List<CaseEvidence> evidences, Set<Long> unlockedIds) {
        return (int) evidences.stream().filter(evidence -> Boolean.TRUE.equals(evidence.getUnlockedByDefault()) || unlockedIds.contains(evidence.getId())).count();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private String briefingTitle(Episode episode) {
        String title = episode == null ? null : episode.getTitle();
        return title == null || title.isBlank() ? "사건파일" : title.trim() + " 사건파일";
    }

    private String caseSummary(Episode episode) {
        String synopsis = episode == null ? null : episode.getFictionSynopsis();
        if (synopsis != null && !synopsis.isBlank()) {
            return synopsis.trim();
        }
        String title = episode == null || episode.getTitle() == null || episode.getTitle().isBlank() ? "이 사건" : episode.getTitle().trim();
        return title + "의 현장 기록이 서로 맞지 않습니다. 플레이어는 조사 지점을 돌며 단서와 사건자료를 대조해야 합니다.";
    }

    private String lockedStorySummary(Episode episode) {
        String title = episode == null || episode.getTitle() == null || episode.getTitle().isBlank() ? "이 사건" : episode.getTitle().trim();
        return title + "의 핵심 기록은 아직 봉인되어 있습니다. 시작 장소의 스토리 미션을 해결하면 사건의 첫 목격 기록과 더 자세한 배경이 이 카드에 추가됩니다.";
    }

    private String detailedStorySummary(Episode episode, List<String> storyClues) {
        String base = caseSummary(episode);
        List<String> safeClues = storyClues == null ? List.of() : storyClues.stream()
                .filter(clue -> clue != null && !clue.isBlank())
                .map(String::trim)
                .toList();
        if (safeClues.isEmpty()) {
            return base + "\n\n시작 기록이 해금되었습니다. 첫 현장 단서와 사건자료를 대조해 정답 단서와 목적지 단서를 분리하세요.";
        }
        return base + "\n\n해금된 시작 기록: " + String.join(" · ", safeClues)
                + "\n이 기록은 사건의 배경을 보강하는 단서입니다. 최종 정답이나 장소를 직접 말하지 않으므로 다른 증거 카드와 함께 대조해야 합니다.";
    }

    private String caseGoal(Episode episode) {
        String target = finalAnswerTypeLabel(episode == null ? null : episode.getFinalAnswerType());
        return "현장 단서와 사건 자료를 대조해 사건의 " + target + "를 밝혀라.";
    }

    private String finalAnswerTypeLabel(String type) {
        return switch (type == null ? "" : type.trim().toUpperCase()) {
            case "CULPRIT" -> "가상 용의자";
            case "WEAPON" -> "흉기";
            case "HIDDEN_DOCUMENT" -> "숨겨진 문서";
            case "SECRET_KEYWORD" -> "비밀 키워드";
            case "HIDDEN_TRUTH" -> "숨겨진 진실";
            default -> "핵심 증거";
        };
    }

    private List<Long> readLongList(String json) {
        return readJsonList(json, LONG_LIST);
    }

    private List<String> readStringList(String json) {
        return readJsonList(json, STRING_LIST);
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
