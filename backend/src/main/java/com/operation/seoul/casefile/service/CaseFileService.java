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
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "?먰뵾?뚮뱶瑜?李얠쓣 ???놁뒿?덈떎.");
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
                        .missionDescription(episode.getMissionDescription() == null || episode.getMissionDescription().isBlank()
                                ? episode.getFictionSynopsis()
                                : episode.getMissionDescription())
                        .storyUnlocked(storyUnlocked)
                        .unlockedStoryClues(storyClues)
                        .build())
                .suspects(suspects.stream().map(suspect -> toSuspect(suspect, unlockedSuspectIds, clearedSuspectIds, evidences)).toList())
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
        boolean unlocked = true;
        int relatedCount = (int) evidences.stream().filter(evidence -> suspect.getId().equals(evidence.getRelatedSuspectId())).count();
        return CaseFileResponse.Suspect.builder()
                .suspectId(suspect.getId())
                .displayName(unlocked ? suspect.getDisplayName() : "잠긴 용의자")
                .alias(suspect.getAlias())
                .shortDescription(unlocked ? suspect.getShortDescription() : "愿??利앷굅瑜??띾뱷?섎㈃ 怨듦컻?⑸땲??")
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
                .title(unlocked ? localizeEvidenceTitle(evidence.getTitle()) : "?좉릿 ?ш굔?먮즺")
                .type(evidence.getType())
                .imageUrl(unlocked ? evidence.getImageUrl() : null)
                .textSummary(unlocked ? localizeEvidenceSummary(evidence.getTextSummary(), evidence.getTitle()) : "?꾩옣 ?쇱쫹???닿껐?섎㈃ ???먮즺媛 ?ш굔?뚯씪??異붽??⑸땲??")
                .sourceSpotId(unlocked ? evidence.getSourceSpotId() : null)
                .relatedSuspectIds(unlocked && evidence.getRelatedSuspectId() != null ? List.of(evidence.getRelatedSuspectId()) : List.of())
                .relatedClueType(evidence.getRelatedClueType())
                .unlocked(unlocked)
                .build();
    }
    private int countUnlockedSuspects(List<CaseSuspect> suspects, Set<Long> unlockedIds) {
        return suspects.size();
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
            return "?꾨낫 ?숈꽑 ?뺤씤 ?꾩슂";
        }
        return normalized.replaceAll("(?i)\\babout\\b\\s*", "??");
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
            case "first field photo envelope" -> "泥??꾩옣 ?ъ쭊 遊됲닾";
            case "torn route memo" -> "李?릿 ?숈꽑 硫붾え";
            case "conflicting witness note" -> "?뉕컝由?紐⑷꺽 湲곕줉";
            case "lens fragment record" -> "?뚯쫰 ?뚰렪 湲곕줉";
            case "red seal sketch" -> "붉은 봉인 스케치";
            case "destination cipher memo" -> "紐⑹쟻吏 ?뷀샇 硫붾え";
            case "final route log" -> "理쒖쥌 ?숈꽑 湲곕줉";
            case "sealed name card" -> "遊됱씤??紐낇븿";
            case "final deduction support file" -> "理쒖쥌 異붾━ 蹂댁“ ?뚯씪";
            default -> {
                String clueTitle = normalized.endsWith(" clue card")
                        ? localizeClueValue(text.substring(0, text.length() - " clue card".length())) + " ?⑥꽌 移대뱶"
                        : text;
                yield isEnglishOnly(clueTitle) ? "?대룆 ?꾩슂 ?ш굔?먮즺" : clueTitle;
            }
        };
    }

    private String localizeEvidenceSummary(String value, String title) {
        if (value == null || value.isBlank()) {
            return "?꾩옣 ?⑥꽌瑜?異붾━???곌껐?섎뒗 ?ш굔?먮즺?낅땲??";
        }
        String text = value.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("marks the opening point of the case")) {
            return "?ш굔???쒖옉 吏?먯쓣 ?쒖떆?섎뒗 ?먮즺?낅땲??";
        }
        if (lower.contains("links route movement with a missing trace")) {
            return "?숈꽑怨??щ씪吏??붿쟻???곌껐?섎뒗 硫붾え?낅땲??";
        }
        if (lower.contains("witness record") && lower.contains("contradiction")) {
            return "?쒕줈 留욎? ?딅뒗 吏꾩닠???쒕윭?대뒗 紐⑷꺽 湲곕줉?낅땲??";
        }
        if (lower.contains("narrows the nature of the final object")) {
            return "理쒖쥌 利앷굅臾쇱쓽 ?뺤껜瑜?醫곹?二쇰뒗 ?먮즺?낅땲??";
        }
        if (lower.contains("connecting suspect motive to the case")) {
            return "?⑹쓽?먯쓽 ?숆린? ?ш굔???곌껐?섎뒗 ?⑥꽌?낅땲??";
        }
        if (lower.contains("narrows the destination without naming it")) {
            return "?μ냼紐낆쓣 吏곸젒 留먰븯吏 ?딄퀬 紐⑹쟻吏瑜?醫곹?二쇰뒗 硫붾え?낅땲??";
        }
        if (lower.contains("reconstructing the final movement path")) {
            return "理쒖쥌 ?숈꽑???ㅼ떆 援ъ꽦?섎뒗 ???꾩슂??湲곕줉?낅땲??";
        }
        if (lower.contains("sealed file") && lower.contains("final deduction")) {
            return "理쒖쥌 異붾━ ?꾩뿉 ?뺤씤?댁빞 ??遊됱씤???뚯씪?낅땲??";
        }
        if (lower.contains("support material for combining collected clues")) {
            return "紐⑥? ?⑥꽌瑜?議고빀?섎뒗 ???꾩슂??蹂댁“ ?먮즺?낅땲??";
        }
        if (lower.contains("case material unlocked after solving this mission")) {
            return "??誘몄뀡???硫??닿툑?섎뒗 ?ш굔?먮즺?낅땲??";
        }
        return isEnglishOnly(text) ? localizeEvidenceTitle(title) + "???곌껐???ш굔?먮즺?낅땲??" : text;
    }
    private boolean isEnglishOnly(String text) {
        if (text == null || text.isBlank() || text.chars().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3)) {
            return false;
        }
        return text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count() >= 3;
    }

    private String briefingTitle(Episode episode) {
        String title = episode == null ? null : episode.getTitle();
        return title == null || title.isBlank() ? "誘몄뀡 硫붾え" : title.trim() + " 誘몄뀡 硫붾え";
    }

    private String caseSummary(Episode episode) {
        String synopsis = episode == null
                ? null
                : episode.getMissionDescription() == null || episode.getMissionDescription().isBlank()
                        ? episode.getFictionSynopsis()
                        : episode.getMissionDescription();
        if (synopsis != null && !synopsis.isBlank()) {
            return synopsis.trim();
        }
        String title = episode == null || episode.getTitle() == null || episode.getTitle().isBlank() ? "???ш굔" : episode.getTitle().trim();
        return title + "???꾩옣 湲곕줉???쒕줈 留욎? ?딆뒿?덈떎. ?뚮젅?댁뼱??議곗궗 吏?먯쓣 ?뚮ŉ ?⑥꽌? ?ш굔?먮즺瑜??議고빐???⑸땲??";
    }

    private String lockedStorySummary(Episode episode) {
        String title = episode == null || episode.getTitle() == null || episode.getTitle().isBlank() ? "???ш굔" : episode.getTitle().trim();
        return title + "???듭떖 湲곕줉? ?꾩쭅 遊됱씤?섏뼱 ?덉뒿?덈떎. ?쒖옉 ?μ냼???ㅽ넗由?誘몄뀡???닿껐?섎㈃ ?ш굔??泥?紐⑷꺽 湲곕줉怨????먯꽭??諛곌꼍????移대뱶??異붽??⑸땲??";
    }

    private String detailedStorySummary(Episode episode, List<String> storyClues) {
        String base = caseSummary(episode);
        List<String> safeClues = storyClues == null ? List.of() : storyClues.stream()
                .filter(clue -> clue != null && !clue.isBlank())
                .map(String::trim)
                .toList();
        if (safeClues.isEmpty()) {
            return base + "\n\n?쒖옉 湲곕줉???닿툑?섏뿀?듬땲?? 泥??꾩옣 ?⑥꽌? ?ш굔?먮즺瑜??議고빐 ?듭떖 ?⑥꽌? ?μ냼 ?⑥꽌瑜?遺꾨━?섏꽭??";
        }
        return base + "\n\n?닿툑???쒖옉 湲곕줉: " + String.join(" 쨌 ", safeClues)
                + "\n??湲곕줉? ?ш굔??諛곌꼍??蹂닿컯?섎뒗 ?⑥꽌?낅땲?? 理쒖쥌 ?뺣떟?대굹 ?μ냼瑜?吏곸젒 留먰븯吏 ?딆쑝誘濡??ㅻⅨ 利앷굅 移대뱶? ?④퍡 ?議고빐???⑸땲??";
    }

    private String caseGoal(Episode episode) {
        return "현장 단서와 사건 자료를 종합해 범인, 흉기, 동기, 방법을 밝혀라.";
    }

    private String finalAnswerTypeLabel(String type) {
        return switch (type == null ? "" : type.trim().toUpperCase()) {
            case "CULPRIT" -> "범인";
            case "WEAPON" -> "흉기";
            case "MOTIVE" -> "동기";
            case "METHOD" -> "방법";
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



