package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class DraftClueQualityRules {
    private DraftClueQualityRules() {
    }

    static String playerFacingText(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) return "";
        List<String> values = new ArrayList<>();
        values.add(trim(draft.getEpisodeTitle()));
        values.add(trim(draft.getSubtitle()));
        values.add(trim(draft.getFictionSynopsis()));
        values.add(trim(draft.getMissionDescription()));
        values.add(trim(draft.getFinalQuestion()));
        values.add(trim(draft.getFinalTruthSummary()));
        values.add(trim(draft.getActualHistorySummary()));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            values.add(trim(mission.getStoryText()));
            values.add(trim(mission.getQuestionText()));
            values.add(trim(mission.getRewardClue()));
            values.addAll(safeList(mission.getHints()));
        }
        for (AiEpisodeDraftResponse.SuspectDraft suspect : safeList(draft.getSuspects())) {
            values.add(trim(suspect.getDisplayName()));
            values.add(trim(suspect.getShortDescription()));
            values.add(trim(suspect.getRelationToVictim()));
            values.add(trim(suspect.getAlibiSummary()));
            values.add(trim(suspect.getSuspiciousPoint()));
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            values.add(trim(evidence.getTitle()));
            values.add(trim(evidence.getTextSummary()));
        }
        return values.stream().filter(value -> !blank(value)).collect(Collectors.joining(" "));
    }

    static boolean containsImmersionBreakingText(String text) {
        if (blank(text)) return false;
        String lowered = text.toLowerCase(Locale.ROOT);
        return containsAny(lowered,
                "rag",
                "tourapi",
                "external search",
                "admin review",
                "needs admin review",
                "real place",
                "fictional suspect",
                "관리자 검수",
                "관리자 확인",
                "검수가 필요",
                "외부 검색",
                "실제 장소",
                "가상의 용의자",
                "허구의 용의자");
    }

    static boolean isGenericFallbackClue(String clue) {
        String compacted = compact(clue);
        return containsAny(compacted,
                "조사단서는범인판단에필요한근거를제공합니다",
                "조사단서는흉기판단에필요한근거를제공합니다",
                "조사단서는동기판단에필요한근거를제공합니다",
                "조사단서는방법판단에필요한근거를제공합니다",
                "판단에필요한근거를제공합니다");
    }

    static boolean isSlotRelevantClue(String target, String clue) {
        if (blank(target) || blank(clue)) return true;
        String compacted = compact(clue);
        if ("CULPRIT".equals(target) && containsAny(compacted, "인물", "인력", "한명", "행동", "걸음걸이", "모습", "증언", "보조", "직원", "연구원", "서재", "누락", "확인")) {
            return true;
        }
        if ("MOTIVE".equals(target) && containsAny(compacted, "해고", "계약", "분쟁", "유산", "손실", "채무", "협박", "이익", "이득", "재정", "금전", "수익", "상속", "불만", "갈등", "불화", "통보", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정", "복수")) {
            return true;
        }
        if ("METHOD".equals(target) && containsAny(compacted, "교환", "약병", "약함", "약통", "약물", "복용", "캡슐", "외형", "목격", "증언", "장면", "이용", "바꾼", "바꾸", "교체", "조작", "반복", "순서", "준비물", "사용", "서명란", "오염", "접촉")) {
            return true;
        }
        return switch (target) {
            case "CULPRIT" -> containsAny(compacted, "지문", "출입", "접근", "알리바이", "동선", "기록", "cctv", "목격", "권한", "일치", "용의자");
            case "WEAPON" -> containsAny(compacted, "흉기", "독", "독극물", "캡슐", "약", "수면제", "잔", "물질", "성분", "검출", "도구");
            case "MOTIVE" -> containsAny(compacted, "동기", "복수", "해고", "계약", "분쟁", "유산", "손실", "채무", "원한", "협박", "이익", "불만", "갈등", "언쟁", "징계", "배제", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정");
            case "METHOD" -> containsAny(compacted, "방법", "바꿔치기", "교체", "조작", "혼입", "투입", "주입", "희석", "위조", "제조", "복용", "캡슐", "접근", "시간", "경로", "열쇠", "봉인", "반복", "순서", "준비물", "사용", "서명란", "오염", "접촉");
            default -> true;
        };
    }

    static boolean contradictsCulpritWithinSuspects(String clue) {
        String compacted = compact(clue);
        if (blank(compacted)) return false;
        boolean allSuspects = containsAny(compacted, "용의자세명", "용의자3명", "세용의자", "모든용의자", "용의자전원");
        boolean excludesAll = containsAny(compacted, "모두다르", "전부다르", "일치하지않", "불일치", "해당하지않");
        return allSuspects && excludesAll;
    }

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}