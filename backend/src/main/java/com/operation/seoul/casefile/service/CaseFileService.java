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
import java.util.stream.Stream;

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
        List<String> rawAnswerClues = readStringList(progress == null ? null : progress.getCollectedAnswerClues());
        List<String> culpritClues = typedClues(rawAnswerClues, "CULPRIT", false);
        List<String> weaponClues = typedClues(rawAnswerClues, "WEAPON", false);
        List<String> motiveClues = typedClues(rawAnswerClues, "MOTIVE", false);
        List<String> methodClues = typedClues(rawAnswerClues, "METHOD", false);
        List<String> relatedPersonClues = typedClues(rawAnswerClues, "RELATED_PERSON", true);
        List<String> coreClues = typedClues(rawAnswerClues, "ANSWER_CLUE", false);
        List<String> caseTruthClues = Stream.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD")
                .flatMap(slot -> typedClues(rawAnswerClues, slot, false).stream())
                .toList();
        if (!caseTruthClues.isEmpty()) {
            coreClues = Stream.concat(coreClues.stream(), caseTruthClues.stream()).distinct().toList();
        }
        List<String> answerClues = new ArrayList<>();
        answerClues.addAll(relatedPersonClues);
        answerClues.addAll(coreClues);
        List<String> destinationClues = readStringList(progress == null ? null : progress.getCollectedDestinationClues()).stream()
                .map(this::clueValueWithoutSlot)
                .map(this::localizeClueValue)
                .toList();
        List<String> storyClues = readStringList(progress == null ? null : progress.getCollectedStoryClues()).stream()
                .map(this::clueValueWithoutSlot)
                .map(this::localizeClueValue)
                .toList();
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
                        .missionDescription(episode.getMissionDescription() == null || episode.getMissionDescription().isBlank()
                                ? episode.getFictionSynopsis()
                                : episode.getMissionDescription())
                        .storyUnlocked(storyUnlocked)
                        .unlockedStoryClues(storyClues)
                        .build())
                .suspects(suspects.stream().map(suspect -> toSuspect(suspect, storyUnlocked, clearedSuspectIds, evidences)).toList())
                .evidences(evidences.stream().map(evidence -> toEvidence(evidence, unlockedEvidenceIds)).toList())
                .clueSummary(CaseFileResponse.ClueSummary.builder()
                        .culpritClues(culpritClues)
                        .weaponClues(weaponClues)
                        .motiveClues(motiveClues)
                        .methodClues(methodClues)
                        .relatedPersonClues(relatedPersonClues)
                        .coreClues(coreClues)
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
                        .unlockedSuspectCount(countUnlockedSuspects(suspects, storyUnlocked))
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

    private CaseFileResponse.Suspect toSuspect(CaseSuspect suspect, boolean suspectsUnlocked, Set<Long> clearedIds, List<CaseEvidence> evidences) {
        boolean unlocked = suspectsUnlocked;
        int relatedCount = (int) evidences.stream().filter(evidence -> suspect.getId().equals(evidence.getRelatedSuspectId())).count();
        return CaseFileResponse.Suspect.builder()
                .suspectId(suspect.getId())
                .displayName(unlocked ? suspect.getDisplayName() : "잠긴 용의자")
                .alias(unlocked ? suspect.getAlias() : "시작 미션 잠금")
                .shortDescription(unlocked ? suspect.getShortDescription() : "시작 미션을 해결하면 용의자 3명의 관계와 알리바이가 함께 공개됩니다.")
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
                .title(unlocked ? localizeEvidenceTitle(evidence.getTitle()) : "잠긴 사건자료")
                .type(evidence.getType())
                .imageUrl(unlocked ? evidence.getImageUrl() : null)
                .textSummary(unlocked ? localizeEvidenceSummary(evidence.getTextSummary(), evidence.getTitle()) : "현장 퍼즐을 해결하면 이 자료가 사건파일에 추가됩니다.")
                .sourceSpotId(unlocked ? evidence.getSourceSpotId() : null)
                .relatedSuspectIds(unlocked && evidence.getRelatedSuspectId() != null ? List.of(evidence.getRelatedSuspectId()) : List.of())
                .relatedClueType(evidence.getRelatedClueType())
                .unlocked(unlocked)
                .build();
    }
    private int countUnlockedSuspects(List<CaseSuspect> suspects, boolean suspectsUnlocked) {
        return suspectsUnlocked ? suspects.size() : 0;
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
        return normalized.replaceAll("(?i)\\bmin\\b", "분").replaceAll("(?i)\\bhours?\\b", "시간").replaceAll("(?i)\\babout\\b\\s*", "약 ");
    }

    private String localizeEstimatedDistance(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("walking route review required") || normalized.equalsIgnoreCase("review required")) {
            return "도보 동선 확인 필요";
        }
        return normalized.replaceAll("(?i)\\babout\\b\\s*", "약 ");
    }

    private String localizeClueValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.trim();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ");
        return switch (normalized) {
            case "red wall" -> "붉은 벽";
            case "last door" -> "마지막 문";
            case "north" -> "북쪽";
            case "waterfront", "water side", "waterside" -> "물가";
            case "bell sound", "bell", "bell ring" -> "종소리";
            case "seal" -> "봉인";
            case "photo" -> "사진";
            case "document" -> "문서";
            case "shadow" -> "그림자";
            case "case start" -> "사건 시작 단서";
            case "case clue" -> "사건 단서";
            case "final place confirmation" -> "결말 확인 장소";
            case "nearby verification focus", "site verification focus" -> "현장 확인 지점";
            case "nearby famous place signal" -> "주변 명소 단서";
            default -> isEnglishOnly(text) ? "해석 필요 단서" : text;
        };
    }
    private List<String> typedClues(List<String> values, String slotId, boolean includeLegacyRelated) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (hasClueSlot(value, slotId)) {
                result.add(localizeClueValue(clueValueWithoutSlot(value)));
            } else if (!hasAnyClueSlot(value) && isLegacyClueForSlot(value, i, slotId, includeLegacyRelated)) {
                result.add(localizeClueValue(value));
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
                || value.startsWith("FINAL_DESTINATION::"));
    }

    private String clueValueWithoutSlot(String value) {
        String text = value == null ? "" : value;
        int marker = text.indexOf("::");
        return marker < 0 ? text : text.substring(marker + 2);
    }

    private boolean containsAny(String source, String... keywords) {
        if (source == null) {
            return false;
        }
        String compact = source.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (compact.contains(keyword.replaceAll("\\s+", "").toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String localizeEvidenceTitle(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String text = value.trim();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ");
        return switch (normalized) {
            case "first field photo envelope" -> "첫 현장 사진 봉투";
            case "torn route memo" -> "찢긴 동선 메모";
            case "conflicting witness note" -> "엇갈린 목격 기록";
            case "lens fragment record" -> "렌즈 파편 기록";
            case "red seal sketch" -> "붉은 봉인 스케치";
            case "destination cipher memo" -> "목적지 암호 메모";
            case "final route log" -> "최종 동선 기록";
            case "sealed name card" -> "봉인된 명함";
            case "final deduction support file" -> "최종 추리 보조 파일";
            default -> {
                String clueTitle = normalized.endsWith(" clue card")
                        ? localizeClueValue(text.substring(0, text.length() - " clue card".length())) + " 단서 카드"
                        : text;
                yield isEnglishOnly(clueTitle) ? "해독 필요 사건자료" : clueTitle;
            }
        };
    }

    private String localizeEvidenceSummary(String value, String title) {
        if (value == null || value.isBlank()) {
            return "현장 단서를 추리와 연결하는 사건자료입니다.";
        }
        String text = value.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("marks the opening point of the case")) {
            return "사건의 시작 지점을 표시하는 자료입니다.";
        }
        if (lower.contains("links route movement with a missing trace")) {
            return "동선과 사라진 흔적을 연결하는 메모입니다.";
        }
        if (lower.contains("witness record") && lower.contains("contradiction")) {
            return "서로 맞지 않는 진술을 드러내는 목격 기록입니다.";
        }
        if (lower.contains("narrows the nature of the final object")) {
            return "최종 증거물의 정체를 좁혀 주는 자료입니다.";
        }
        if (lower.contains("connecting suspect motive to the case")) {
            return "용의자의 동기를 사건과 연결하는 단서입니다.";
        }
        if (lower.contains("narrows the destination without naming it")) {
            return "장소명을 직접 말하지 않고 목적지를 좁혀 주는 메모입니다.";
        }
        if (lower.contains("reconstructing the final movement path")) {
            return "최종 동선을 다시 구성하는 데 필요한 기록입니다.";
        }
        if (lower.contains("sealed file") && lower.contains("final deduction")) {
            return "최종 추리 전에 확인해야 할 봉인된 파일입니다.";
        }
        if (lower.contains("support material for combining collected clues")) {
            return "모은 단서를 조합하는 데 필요한 보조 자료입니다.";
        }
        if (lower.contains("case material unlocked after solving this mission")) {
            return "이 미션을 풀면 해금되는 사건자료입니다.";
        }
        return isEnglishOnly(text) ? localizeEvidenceTitle(title) + "와 연결된 사건자료입니다." : text;
    }
    private boolean isEnglishOnly(String text) {
        if (text == null || text.isBlank() || text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3)) {
            return false;
        }
        return text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count() >= 3;
    }

    private String briefingTitle(Episode episode) {
        String title = episode == null ? null : episode.getTitle();
        return title == null || title.isBlank() ? "미션 메모" : title.trim() + " 미션 메모";
    }

    private String caseSummary(Episode episode) {
        String fictionSynopsis = episode == null ? null : textOrNull(episode.getFictionSynopsis());
        String missionDescription = episode == null ? null : textOrNull(episode.getMissionDescription());
        String synopsis = longerText(fictionSynopsis, missionDescription);
        if (synopsis != null && synopsis.length() >= 80 && !isGenericStorySummary(synopsis)) {
            return synopsis;
        }
        String title = episode == null || episode.getTitle() == null || episode.getTitle().isBlank() ? "이 사건" : episode.getTitle().trim();
        String setting = synopsis == null ? "제한된 공간에서 한 인물이 쓰러진 채 발견되며 시작됩니다." : synopsis;
        return title + " 사건은 " + setting
                + " 현장에는 외부 침입 흔적이 뚜렷하지 않고, 사건 시간대 의미 있는 접근 권한을 가진 인물은 세 명으로 압축됩니다."
                + " 플레이어는 8개 조사 지점에서 알리바이, 물증, 동기 문서, 사인의 흔적을 모아 범인·흉기·동기·사인을 재구성해야 합니다.";
    }

    private String lockedStorySummary(Episode episode) {
        return caseSummary(episode)
                + "\n\n용의자 파일은 아직 봉인되어 있습니다. 시작 장소의 스토리 미션을 해결하면 용의자 3명의 관계, 알리바이, 의심 포인트가 함께 공개됩니다.";
    }

    private String detailedStorySummary(Episode episode, List<String> storyClues) {
        String base = caseSummary(episode);
        List<String> safeClues = storyClues == null ? List.of() : storyClues.stream()
                .filter(clue -> clue != null && !clue.isBlank())
                .map(String::trim)
                .toList();
        if (safeClues.isEmpty()) {
            return base + "\n\n시작 기록이 해금되었습니다. 첫 현장 단서와 사건자료를 대조해 스토리 단서와 장소 단서를 분리하세요.";
        }
        return base + "\n\n해금된 시작 기록: " + String.join(" · ", safeClues)
                + "\n이 기록은 사건의 배경을 보강하는 단서입니다. 최종 정답이나 장소를 직접 말하지 않으므로 다른 증거 카드와 함께 대조해야 합니다.";
    }

    private String caseGoal(Episode episode) {
        return "현장 단서와 사건 자료를 종합해 범인, 흉기, 동기, 사인을 밝혀라.";
    }

    private String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String longerText(String first, String second) {
        if (first == null) return second;
        if (second == null) return first;
        return second.length() > first.length() ? second : first;
    }

    private boolean isGenericStorySummary(String value) {
        return containsAny(value, "8개 조사 단서로 네 개 정답 슬롯", "정답 슬롯", "조사 단서는", "판단에 필요한 근거");
    }

    private String finalAnswerTypeLabel(String type) {
        return switch (type == null ? "" : type.trim().toUpperCase()) {
            case "CULPRIT" -> "범인";
            case "WEAPON" -> "흉기";
            case "MOTIVE" -> "동기";
            case "METHOD" -> "사인";
            default -> "사건의 진실";
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



