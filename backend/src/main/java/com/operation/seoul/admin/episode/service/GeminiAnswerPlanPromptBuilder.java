package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.stream.Collectors;

final class GeminiAnswerPlanPromptBuilder {
    private GeminiAnswerPlanPromptBuilder() {
    }

    static String build(AiEpisodeDraftRequest request) {
        return """
                Return JSON only.
                Genre is fixed to CRIME_MYSTERY.
                Final answers are exactly four slots: CULPRIT, WEAPON, MOTIVE, METHOD.
                Every final answer keyword must be concrete and playable.
                Derive the four final answer values from the TourAPI story anchors below: historical incidents, cultural conflicts, records, materials, rituals, industries, disputes, or preservation facts.
                Do not choose a generic domain template just because a place is a museum, gallery, cafe, market, mountain, or station.
                The answer values should feel like a fictionalized case built from the anchors' concrete nouns and conflicts.
                Before returning JSON, internally verify that every slot would pass these server checks:
                - CULPRIT is a specific fictional Korean person name, not a role, occupation, historic name, literary name, mythic name, or public figure.
                - WEAPON includes both the ordinary carrier object and the harmful detail or substance.
                - MOTIVE is a concrete pressure, secret, dispute, contract, record, debt, ownership issue, or cover-up reason anchored in the TourAPI motifs.
                - METHOD is at least one complete Korean phrase that includes: harmful object/substance, where it was placed or delivered, how the victim contacts/uses it, and the action/resulting exposure.
                - METHOD must be more specific than WEAPON. It must not merely restate the weapon and must not be only a final result.
                - METHOD should follow this pattern: "<WEAPON or harmful substance> + <carrier/location> + <victim routine/contact> + <specific tampering/delivery verb>".
                - METHOD must be physically plausible. Match the victim interaction to the object: food, drink, and medicine can be eaten or drunk; pens, brushes, documents, gloves, cards, and tools should use contact, signing, opening, spraying, inhaling, or handling instead of eating/drinking.
                - Do not write unclear phrases such as "이식하여 섭취 유도", "내용물 섭취 유도", "몰래 사용하게 함", or "접촉하게 함" unless the exact carrier, contact point, and victim routine are named.
                If any slot fails the checklist, replace it before returning JSON.
                CULPRIT must be a new fictional modern Korean person. Do not use historical, literary, mythic, or public figure names such as 이몽룡, 성춘향, 홍길동, 임꺽정, 장보고, 유관순, 세종대왕, 이순신, 안중근, or 김구.
                METHOD must explain the concrete delivery route and action. Do not use vague result-only wording such as "혼란을 야기함", "몰래 투여함", "정신을 잃게 함", or "상태를 악화시킴".
                CULPRIT must be a Korean person name, optionally followed by role in parentheses, such as "오지훈(기록 담당자)" or "서민재". Never return only an occupation such as "큐레이터", "여행사 직원", or "관리자".
                WEAPON must identify a harmful object or substance with the dangerous detail, such as "마취 성분이 섞인 향수병", "독성 분말이 묻은 문서 봉투", or "독성 시약이 든 보온병"; never return only an ordinary object or container such as "향수병", "봉투", "약병", or "컵".
                MOTIVE must be a concrete reason that explains why the culprit acted, such as "위작 거래 은폐" or "불법 원정 사고 은폐"; never return generic words such as "은폐", "범죄", "복수", or "돈".
                METHOD must be a concrete crime process with object and action, such as "향수병에 마취 성분을 넣어 피해자에게 분사" or "문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함"; never return a single verb or empty predicate such as "함", "넣기", "투여", or "조작".
                Bad example: CULPRIT="관리자", WEAPON="봉투", MOTIVE="은폐", METHOD="함".
                Good example: CULPRIT="서민재(기록 담당자)", WEAPON="독성 분말이 묻은 문서 봉투", MOTIVE="비공개 계약 문서 은폐", METHOD="문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함".
                Do not create place hints, destination clues, or final-place guessing.
                Use the selected places and research context only as background motifs.
                Never imply that a real crime happened at a real place.
                Do not use immersion-breaking wording such as "real place", "fictional suspect", "needs admin review", or "RAG context".
                Never reuse stale sample answers or names: 강수진, 서민재, 윤서진, 독성 캡슐, 비밀 계약 은폐, 약병 바꿔치기, 마취 성분이 섞인 향수병, 비공개 계약 파기 은폐, 향수병에 마취 성분을 넣어 피해자에게 분사, 독성 분말이 묻은 문서 봉투, 비공개 계약 문서 은폐, 문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함.
                Choose fresh culprit, weapon, motive, and method values that fit the selected route and case premise.

                Required JSON shape:
                {
                  "finalAnswerKeywords": [
                    {"slotId":"CULPRIT","type":"CULPRIT","label":"범인","keyword":"..."},
                    {"slotId":"WEAPON","type":"WEAPON","label":"흉기","keyword":"..."},
                    {"slotId":"MOTIVE","type":"MOTIVE","label":"동기","keyword":"..."},
                    {"slotId":"METHOD","type":"METHOD","label":"방법","keyword":"..."}
                  ]
                }

                Context:
                """ + buildContext(request);
    }

    private static String buildContext(AiEpisodeDraftRequest request) {
        TourApiPlanContext planContext = TourApiPlanInputExtractor.extract(request);
        String historicalContext = planContext.historicalContext();
        return String.join("\n",
                "Admin input:",
                "- area: " + safePromptText(request == null ? "" : request.getArea()),
                "- theme: " + safePromptText(request == null ? "" : request.getTheme()),
                "- playTime: " + safePromptText(request == null ? "" : request.getPlayTime()),
                "- genre: " + safePromptText(request == null ? "" : request.getSelectedGenreName()),
                "TourAPI story anchors to fictionalize:",
                planContext.storyAnchors().isEmpty() ? "(none)" : planContext.storyAnchors().stream().map(anchor -> "- " + safePromptText(anchor)).collect(Collectors.joining("\n")),
                "TourAPI historical/cultural motifs without place names or addresses:",
                blank(historicalContext) ? "(none)" : safePromptText(historicalContext));
    }

    private static String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
