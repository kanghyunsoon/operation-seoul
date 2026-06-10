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
import java.util.Locale;
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
        List<String> answerClues = readStringList(progress == null ? null : progress.getCollectedAnswerClues()).stream().map(this::localizeClueValue).toList();
        List<String> destinationClues = readStringList(progress == null ? null : progress.getCollectedDestinationClues()).stream().map(this::localizeClueValue).toList();
        List<String> storyClues = readStringList(progress == null ? null : progress.getCollectedStoryClues()).stream().map(this::localizeClueValue).toList();
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
                .estimatedTime(localizeEstimatedTime(episode.getEstimatedTime()))
                .estimatedDistance(localizeEstimatedDistance(episode.getEstimatedDistance()))
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
                .title(unlocked ? localizeEvidenceTitle(evidence.getTitle()) : "\uC7A0\uAE34 \uC0AC\uAC74\uC790\uB8CC")
                .type(evidence.getType())
                .imageUrl(unlocked ? evidence.getImageUrl() : null)
                .textSummary(unlocked ? localizeEvidenceSummary(evidence.getTextSummary(), evidence.getTitle()) : "\uD604\uC7A5 \uD37C\uC990\uC744 \uD574\uACB0\uD558\uBA74 \uC774 \uC790\uB8CC\uAC00 \uC0AC\uAC74\uD30C\uC77C\uC5D0 \uCD94\uAC00\uB429\uB2C8\uB2E4.")
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

    private String localizeEstimatedTime(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        return normalized.replaceAll("(?i)\\bmin\\b", "\uBD84").replaceAll("(?i)\\bhours?\\b", "\uC2DC\uAC04").replaceAll("(?i)\\babout\\b\\s*", "\uC57D ");
    }

    private String localizeEstimatedDistance(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("walking route review required") || normalized.equalsIgnoreCase("review required")) {
            return "\uB3C4\uBCF4 \uB3D9\uC120 \uD655\uC778 \uD544\uC694";
        }
        return normalized.replaceAll("(?i)\\babout\\b\\s*", "\uC57D ");
    }

    private String localizeClueValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.trim();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ");
        return switch (normalized) {
            case "red wall" -> "\uBD89\uC740 \uB2F4\uC7A5";
            case "last door" -> "\uB9C8\uC9C0\uB9C9 \uBB38";
            case "north" -> "\uBD81\uCABD";
            case "waterfront", "water side", "waterside" -> "\uBB3C\uAC00";
            case "bell sound", "bell", "bell ring" -> "\uC885\uC18C\uB9AC";
            case "seal" -> "\uBC00\uB78D \uC778\uC7A5";
            case "photo" -> "\uD750\uB9B0 \uC0AC\uC9C4";
            case "document" -> "\uC811\uD78C \uBB38\uC11C";
            case "shadow" -> "\uAE34 \uADF8\uB9BC\uC790";
            case "case start" -> "\uC0AC\uAC74 \uC2DC\uC791 \uB2E8\uC11C";
            case "case clue" -> "\uC0AC\uAC74 \uB2E8\uC11C";
            case "final place confirmation" -> "\uCD5C\uC885 \uC7A5\uC18C \uD655\uC778 \uB2E8\uC11C";
            case "nearby verification focus", "site verification focus" -> "\uC8FC\uBCC0 \uD655\uC778 \uC9C0\uC810";
            case "nearby famous place signal" -> "\uC8FC\uBCC0 \uBA85\uC18C \uB2E8\uC11C";
            default -> isEnglishOnly(text) ? "\uD574\uB3C5 \uD544\uC694 \uB2E8\uC11C" : text;
        };
    }

    private String localizeEvidenceTitle(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.trim();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ");
        return switch (normalized) {
            case "first field photo envelope" -> "\uCCAB \uD604\uC7A5 \uC0AC\uC9C4 \uBD09\uD22C";
            case "torn route memo" -> "\uCC22\uAE34 \uB3D9\uC120 \uBA54\uBAA8";
            case "conflicting witness note" -> "\uC5C7\uAC08\uB9B0 \uBAA9\uACA9 \uAE30\uB85D";
            case "lens fragment record" -> "\uB80C\uC988 \uD30C\uD3B8 \uAE30\uB85D";
            case "red seal sketch" -> "\uBD89\uC740 \uC778\uC7A5 \uC2A4\uCF00\uCE58";
            case "destination cipher memo" -> "\uBAA9\uC801\uC9C0 \uC554\uD638 \uBA54\uBAA8";
            case "final route log" -> "\uCD5C\uC885 \uB3D9\uC120 \uAE30\uB85D";
            case "sealed name card" -> "\uBD09\uC778\uB41C \uBA85\uD568";
            case "final deduction support file" -> "\uCD5C\uC885 \uCD94\uB9AC \uBCF4\uC870 \uD30C\uC77C";
            default -> {
                String clueTitle = normalized.endsWith(" clue card")
                        ? localizeClueValue(text.substring(0, text.length() - " clue card".length())) + " \uB2E8\uC11C \uCE74\uB4DC"
                        : text;
                yield isEnglishOnly(clueTitle) ? "\uD574\uB3C5 \uD544\uC694 \uC0AC\uAC74\uC790\uB8CC" : clueTitle;
            }
        };
    }

    private String localizeEvidenceSummary(String value, String title) {
        if (value == null || value.isBlank()) {
            return "\uD604\uC7A5 \uB2E8\uC11C\uB97C \uCD94\uB9AC\uC5D0 \uC5F0\uACB0\uD558\uB294 \uC0AC\uAC74\uC790\uB8CC\uC785\uB2C8\uB2E4.";
        }
        String text = value.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("marks the opening point of the case")) {
            return "\uC0AC\uAC74\uC758 \uC2DC\uC791 \uC9C0\uC810\uC744 \uD45C\uC2DC\uD558\uB294 \uC790\uB8CC\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("links route movement with a missing trace")) {
            return "\uB3D9\uC120\uACFC \uC0AC\uB77C\uC9C4 \uD754\uC801\uC744 \uC5F0\uACB0\uD558\uB294 \uBA54\uBAA8\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("witness record") && lower.contains("contradiction")) {
            return "\uC11C\uB85C \uB9DE\uC9C0 \uC54A\uB294 \uC9C4\uC220\uC744 \uB4DC\uB7EC\uB0B4\uB294 \uBAA9\uACA9 \uAE30\uB85D\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("narrows the nature of the final object")) {
            return "\uCD5C\uC885 \uC99D\uAC70\uBB3C\uC758 \uC815\uCCB4\uB97C \uC881\uD600\uC8FC\uB294 \uC790\uB8CC\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("connecting suspect motive to the case")) {
            return "\uC6A9\uC758\uC790\uC758 \uB3D9\uAE30\uC640 \uC0AC\uAC74\uC744 \uC5F0\uACB0\uD558\uB294 \uB2E8\uC11C\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("narrows the destination without naming it")) {
            return "\uC7A5\uC18C\uBA85\uC744 \uC9C1\uC811 \uB9D0\uD558\uC9C0 \uC54A\uACE0 \uBAA9\uC801\uC9C0\uB97C \uC881\uD600\uC8FC\uB294 \uBA54\uBAA8\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("reconstructing the final movement path")) {
            return "\uCD5C\uC885 \uB3D9\uC120\uC744 \uB2E4\uC2DC \uAD6C\uC131\uD558\uB294 \uB370 \uD544\uC694\uD55C \uAE30\uB85D\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("sealed file") && lower.contains("final deduction")) {
            return "\uCD5C\uC885 \uCD94\uB9AC \uC804\uC5D0 \uD655\uC778\uD574\uC57C \uD560 \uBD09\uC778\uB41C \uD30C\uC77C\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("support material for combining collected clues")) {
            return "\uBAA8\uC740 \uB2E8\uC11C\uB97C \uC870\uD569\uD558\uB294 \uB370 \uD544\uC694\uD55C \uBCF4\uC870 \uC790\uB8CC\uC785\uB2C8\uB2E4.";
        }
        if (lower.contains("case material unlocked after solving this mission")) {
            return "\uC774 \uBBF8\uC158\uC744 \uD480\uBA74 \uD574\uAE08\uB418\uB294 \uC0AC\uAC74\uC790\uB8CC\uC785\uB2C8\uB2E4.";
        }
        return isEnglishOnly(text) ? localizeEvidenceTitle(title) + "\uC5D0 \uC5F0\uACB0\uB41C \uC0AC\uAC74\uC790\uB8CC\uC785\uB2C8\uB2E4." : text;
    }
    private boolean isEnglishOnly(String text) {
        if (text == null || text.isBlank() || text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3)) {
            return false;
        }
        return text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count() >= 3;
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
