package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GeminiDraftPromptBuilder {
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;

    private GeminiDraftPromptBuilder() {
    }

    static String build(AiEpisodeDraftRequest request) {
        return """
                JSON만 반환한다. 모든 문장은 한국어로 작성한다.
                장르는 항상 "범죄 미스터리"다.

                너의 역할은 장소 안내문 작성자가 아니라 크라임씬 사건 작가다.
                승인된 최종 정답 4개(CULPRIT, WEAPON, MOTIVE, METHOD)를 바탕으로 하나의 완성된 살인 사건을 만든다.
                실제 장소명, 주소, 상호명, Kakao 주변 후보, 지도 동선은 사건 줄거리와 단서에 사용하지 않는다.
                장소는 나중에 미션에 배정될 지도 좌표일 뿐이다.

                반드시 지킬 것:
                - CULPRIT 값은 suspects[0..2].displayName 중 정확히 한 명이어야 한다.
                - suspect displayName은 한국인 이름만 쓴다. 직업/역할은 relationToVictim 또는 shortDescription에 쓴다.
                - finalTruthSummary에는 승인된 CULPRIT, WEAPON, MOTIVE, METHOD 값을 그대로 모두 포함한다.
                - fictionSynopsis는 경로 설명이 아니라 사건 개요다. 피해자, 발견 상황, 사망 방식, 제한된 용의자 3명, 갈등 배경, 수사해야 할 핵심 의문을 포함한다.
                - actualHistorySummary는 허구 사건 해설이 아니다. 아래 storyAnchors/historicalContext가 있으면 그 실제 배경이 사건 모티브로 어떻게 변환됐는지 설명한다. 직접 역사 사건이 부족하면 "직접 역사 사건이 아니라 지역/시대/공간 성격을 모티브로 삼았다"고 명확히 쓴다.
                - missions는 10개다. 1번 START, 2~9번 ANSWER_HINT, 10번 FINAL.
                - 2~9번 rewardClue는 각각 구체적인 증거 문장이어야 한다. 기록, 지문, 출입 로그, CCTV 공백, 물증 상태, 분석 결과, 계약/문서, 목격 진술처럼 수사 자료로 쓴다.
                - rewardClue에 정답 값을 그대로 쓰지 않는다. 특히 범인 이름, 흉기명, 동기 문구, 방법 문장을 직접 노출하지 않는다.
                - evidences는 8개이며 sourceMissionOrder 2~9에 각각 연결한다.
                - "단순 사고", "반복되는 숫자", "방향 표식", "최종 장소를 찾아라", "장소를 비교하라", "TourAPI", "RAG", "Kakao" 같은 표현은 쓰지 않는다.

                미션 슬롯:
                - order 2: targetKeywordType CULPRIT, 접근/권한/기회 증거
                - order 3: targetKeywordType CULPRIT, 알리바이/CCTV/지문 대조 증거
                - order 4: targetKeywordType WEAPON, 물증/흔적/손상/성분 분석 증거
                - order 5: targetKeywordType WEAPON, 보관 위치/이동/사용 흔적 증거
                - order 6: targetKeywordType MOTIVE, 계약/소유권/거래/평판/책임 관련 문서 증거
                - order 7: targetKeywordType MOTIVE, 협박/압박/삭제 메시지/목격 진술 증거
                - order 8: targetKeywordType METHOD, 피해자 루틴/사건 직전 행동/시간표 증거
                - order 9: targetKeywordType METHOD, 조작 순서/접근 경로/실행 가능성 교차 검증 증거

                반환 JSON 필수 필드:
                episodeTitle, subtitle, genre, selectedGenre, fictionSynopsis, missionDescription,
                finalTruthSummary, actualHistorySummary, finalQuestion, missions, suspects, evidences.

                Context:
                """ + buildStoryGenerationContext(request);
    }

    private static String buildStoryGenerationContext(AiEpisodeDraftRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        appendTourApiContext(builder, request);
        builder.append("missionOrders:\n");
        builder.append("- 1 START\n");
        builder.append("- 2 ANSWER_HINT CULPRIT access/opportunity clue\n");
        builder.append("- 3 ANSWER_HINT CULPRIT alibi/trace clue\n");
        builder.append("- 4 ANSWER_HINT WEAPON material/analysis clue\n");
        builder.append("- 5 ANSWER_HINT WEAPON object/container clue\n");
        builder.append("- 6 ANSWER_HINT MOTIVE conflict/document clue\n");
        builder.append("- 7 ANSWER_HINT MOTIVE pressure/message clue\n");
        builder.append("- 8 ANSWER_HINT METHOD routine/timing clue\n");
        builder.append("- 9 ANSWER_HINT METHOD tampering/sequence clue\n");
        builder.append("- 10 FINAL unlocks after all investigation missions\n");
        return builder.toString();
    }

    private static void appendTourApiContext(StringBuilder builder, AiEpisodeDraftRequest request) {
        TourApiPlanContext context = TourApiPlanInputExtractor.extract(request);
        builder.append("storyAnchors:\n");
        if (context.storyAnchors().isEmpty()) {
            builder.append("- 직접 역사/사건 앵커 없음. 지역/시대/공간 성격 기반으로만 모티브를 만든다.\n");
        } else {
            context.storyAnchors().forEach(anchor -> builder.append("- ").append(safePromptText(anchor)).append('\n'));
        }
        builder.append("historicalContext:\n");
        builder.append(safePromptText(context.historicalContext())).append('\n');
        builder.append("forbiddenSourcePolicy:\n");
        builder.append("- Kakao Local, 주변 후보, 현장 검수 메모, 실제 route place names are not story material.\n");
        builder.append("- Story and clues must be written before assigning real map places.\n");
    }

    private static void appendApprovedAnswers(StringBuilder builder, AiEpisodeDraftRequest request) {
        Map<String, String> approved = approvedAnswers(request);
        builder.append("approvedFinalAnswers:\n");
        for (String slot : SLOT_IDS) {
            builder.append("- ").append(slot).append(": ").append(safePromptText(approved.get(slot))).append('\n');
        }
    }

    private static Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> result.put(slot, ""));
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
                if (SLOT_IDS.contains(slot)) putIfNotBlank(result, slot, answerKeywordValue(item));
            }
        }
        if (request != null && request.getFinalAnswers() != null) {
            putIfNotBlank(result, "CULPRIT", request.getFinalAnswers().getCulprit());
            putIfNotBlank(result, "WEAPON", request.getFinalAnswers().getWeapon());
            putIfNotBlank(result, "MOTIVE", request.getFinalAnswers().getMotive());
            putIfNotBlank(result, "METHOD", request.getFinalAnswers().getMethod());
        }
        return result;
    }

    private static String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getSourceText());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private static void putIfNotBlank(Map<String, String> values, String key, String value) {
        if (!blank(value)) values.put(key, value.trim());
    }

    private static NameRole splitNameRole(String value) {
        String text = trim(value);
        if (blank(text)) return new NameRole("", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*([가-힣]{2,4})\\s*\\(([^)]+)\\)\\s*$")
                .matcher(text);
        if (matcher.matches()) {
            return new NameRole(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new NameRole(text, "");
    }

    private record NameRole(String name, String role) {}

    private static String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
