package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DraftSuspectGuardrail {
    private DraftSuspectGuardrail() {
    }

    static boolean hasUsableSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        boolean hasCulprit = false;
        Set<String> names = new LinkedHashSet<>();
        for (AiEpisodeDraftResponse.SuspectDraft suspect : suspects) {
            if (suspect == null || blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                return false;
            }
            if (!names.add(compact(suspect.getDisplayName()))) {
                return false;
            }
            String suspectText = compact(String.join(" ",
                    trim(suspect.getDisplayName()),
                    trim(suspect.getAlias()),
                    trim(suspect.getRelationToVictim())));
            hasCulprit = hasCulprit || suspectText.contains(compact(culprit));
        }
        return hasCulprit;
    }

    static List<AiEpisodeDraftResponse.SuspectDraft> canonicalSuspects(List<AiEpisodeDraftResponse.SuspectDraft> source, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> result = new ArrayList<>();
        AiEpisodeDraftResponse.SuspectDraft culpritDraft = safeList(source).stream()
                .filter(suspect -> suspect != null && containsAny(compact(String.join(" ", trim(suspect.getDisplayName()), trim(suspect.getAlias()))), compact(culprit)))
                .findFirst()
                .orElseGet(() -> AiEpisodeDraftResponse.SuspectDraft.builder().displayName(culprit).build());
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(defaultIfBlank(culpritDraft.getDisplayName(), culprit))
                .alias(culpritDraft.getAlias())
                .relationToVictim(defaultIfBlank(culpritDraft.getRelationToVictim(), "피해자의 비서"))
                .alibiSummary(defaultIfBlank(culpritDraft.getAlibiSummary(), "사건 추정 시각 동안 행사 자료를 정리하고 있었다고 주장하며, 일부 노트북 사용 기록이 남아 있다."))
                .suspiciousPoint(defaultIfBlank(culpritDraft.getSuspiciousPoint(), "최근 해고 통보를 받았고 피해자의 일정과 약 복용 습관을 가장 잘 알고 있었다."))
                .shortDescription(culpritDraft.getShortDescription())
                .portraitImageUrl(culpritDraft.getPortraitImageUrl())
                .imagePrompt(culpritDraft.getImagePrompt())
                .build());
        addNonCulpritSuspect(result, source, "박도현", "사업 파트너",
                "사건 시간 동안 투자자와 화상회의를 했다고 주장하며, 회의 접속 기록이 대부분 남아 있다.",
                "피해자와 투자 분쟁이 있었고 피해자 사망 시 경제적 이익을 얻을 수 있었다.");
        addNonCulpritSuspect(result, source, "이재훈", "피해자의 조카",
                "사건 시간 동안 전시 준비를 하고 있었다고 주장하며, 일부 CCTV에 모습이 남아 있다.",
                "유산 상속 예정자였고 최근 피해자와 크게 다퉜으나 CCTV 공백 시간이 사망 추정 시각과 어긋난다.");
        return result.stream().limit(3).toList();
    }

    private static void addNonCulpritSuspect(List<AiEpisodeDraftResponse.SuspectDraft> result, List<AiEpisodeDraftResponse.SuspectDraft> source, String fallbackName, String relation, String alibi, String suspicion) {
        AiEpisodeDraftResponse.SuspectDraft existing = safeList(source).stream()
                .filter(suspect -> suspect != null && result.stream().noneMatch(saved -> compact(saved.getDisplayName()).equals(compact(suspect.getDisplayName()))))
                .findFirst()
                .orElse(null);
        String displayName = defaultIfBlank(existing == null ? "" : existing.getDisplayName(), fallbackName);
        String compactDisplayName = compact(displayName);
        if (result.stream().anyMatch(saved -> compact(saved.getDisplayName()).equals(compactDisplayName))) {
            displayName = fallbackName;
        }
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(displayName)
                .alias(existing == null ? null : existing.getAlias())
                .relationToVictim(defaultIfBlank(existing == null ? "" : existing.getRelationToVictim(), relation))
                .alibiSummary(defaultIfBlank(existing == null ? "" : existing.getAlibiSummary(), alibi))
                .suspiciousPoint(defaultIfBlank(existing == null ? "" : existing.getSuspiciousPoint(), suspicion))
                .shortDescription(existing == null ? null : existing.getShortDescription())
                .portraitImageUrl(existing == null ? null : existing.getPortraitImageUrl())
                .imagePrompt(existing == null ? null : existing.getImagePrompt())
                .build());
    }

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String defaultIfBlank(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}