package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DraftInvestigationClueDebugSnapshotWriter {
    private static final Path SNAPSHOT_PATH = Path.of("build", "ai-draft-debug", "latest-pre-guardrail-investigation-clues.json");

    private final ObjectMapper objectMapper;

    DraftInvestigationClueDebugSnapshotWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        List<AiEpisodeDraftResponse.MissionDraft> investigation = DraftInvestigationCluePolicy.investigationMissions(draft);
        List<String> answers = DraftInvestigationCluePolicy.answerValues(draft);
        List<Map<String, Object>> missions = investigation.stream()
                .map(mission -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", mission.getOrder());
                    item.put("targetKeywordType", normalize(mission.getTargetKeywordType()));
                    item.put("supportsKeywordSlots", safeList(mission.getSupportsKeywordSlots()).stream().map(DraftInvestigationClueDebugSnapshotWriter::normalize).toList());
                    item.put("issues", DraftInvestigationCluePolicy.missionIssues(mission, answers));
                    item.put("rewardClue", trim(mission.getRewardClue()));
                    return item;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", trim(draft.getEpisodeTitle()));
        payload.put("issues", issues);
        payload.put("missions", missions);
        try {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
            return SNAPSHOT_PATH.toString();
        } catch (Exception e) {
            return "WRITE_FAILED: " + e;
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
