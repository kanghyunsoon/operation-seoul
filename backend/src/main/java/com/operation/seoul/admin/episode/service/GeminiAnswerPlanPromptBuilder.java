package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.Locale;
import java.util.stream.Collectors;

final class GeminiAnswerPlanPromptBuilder {
    private GeminiAnswerPlanPromptBuilder() {
    }

    static String build(AiEpisodeDraftRequest request) {
        return """
                Return JSON only.
                Genre is fixed to CRIME_MYSTERY.
                Final answers are exactly four slots: CULPRIT, WEAPON, MOTIVE, METHOD. The METHOD slot is displayed to players as "사인" and must answer the direct cause of death or fatal injury pattern, not a vague procedure label.
                Final answer values may be short Korean keywords when they are playable, such as 칼, 망치, 톱, 복부 자상 과다출혈, 교살 질식, 익사, 감전사, 압사, or 독극물 중독.
                Derive the four final answer values from the story anchors below: TourAPI historical incidents, cultural conflicts, local materials, rituals, industries, disputes, transit/route features, architecture, commerce, landscape, water, tools, weather, crowd flow, or regional/era/theme background when direct incidents are unavailable.
                Do not choose a generic domain template just because a place is a museum, gallery, cafe, market, mountain, or station.
                Do not overuse any single domain template. The strongest domain must come from the current anchors, not from this instruction text.
                Never return generic one-word abstractions as WEAPON or MOTIVE. If a domain is relevant, choose the concrete object, physical fixture, service, material, route element, or conflict from the current anchors.
                """ + buildRecordDomainPolicy(request) + """
                Never return vague final answer values such as "산업 스파이", "은폐", "갈등", "복수", "사고", "협박", "조작", or "침입" by themselves.
                MOTIVE must name the concrete loss or exposure pressure: what secret, contract, money, responsibility, position, ownership, reputation, or succession would be lost.
                METHOD must name the cause-of-death answer in a compact Korean phrase: the fatal injury/death route, and when needed the killer action that produced it.
                WEAPON must be an inspectable physical object, fixture, material, tool, container, machine, route element, or altered part. Avoid broad category nouns unless the clue can identify the exact object.
                The answer values should feel like a fictionalized case built from the anchors' concrete nouns and conflicts.
                Before returning JSON, internally follow this generation process:
                1. Identify the strongest anchor domain from the context, such as route control, transit, tourism change, old-house reuse, disputes, rituals, commerce, craft tools, water/terrain, architecture, regional identity, era background, or institutional history.
                2. Choose one murder mechanism that fits the anchor domain before choosing the weapon. Use varied mechanisms across generations: blunt force, stabbing/cutting, strangulation, drowning, collision, confinement/freezing, crushing, electrocution, explosion, allergy trigger, poisoning, or psychological coercion. Do not default to poisoning, contamination, toxic residue, skin contact, fall, release-device, or any other single mechanism unless the current anchors strongly support that mechanism.
                3. Build the WEAPON from nouns that appear in that anchor domain. The carrier should be an object, fixture, tool, route element, vehicle-related object, or handled material a character could plausibly use in the chosen mechanism.
                4. Add danger by changing the state of that carrier according to the murder mechanism: broken, sharpened, weighted, removed, jammed, rigged, hidden, swapped, weakened, blocked, contaminated, mislabeled, or treated. Use common state words, not newly invented specialist materials.
                5. Build MOTIVE from the same anchor domain's conflict: concealment, broken contract, debt, responsibility, access, territory, inheritance, safety blame, trade loss, promotion, reputation, failed partnership, succession, exclusion, or public exposure.
                6. Build METHOD as the final cause-of-death keyword by naming the fatal injury/death route first, and include the killer action only when it prevents ambiguity. METHOD must be more specific than WEAPON and physically plausible.
                7. Select only keywords that already satisfy the anchor-domain and METHOD construction rules before returning JSON.
                Domain material selection rules:
                - For route, transit, tourism-change, old-house reuse, and street-history anchors, choose carriers from signs, guide ropes, gates, route barriers, vehicle-adjacent fixtures, route maps, guide sheets, tickets, tools, stalls, carts, handled fixtures, railings, or steps.
                - For institutional, cultural, commercial, or preservation anchors, choose carriers from the concrete nouns found in the current anchors first. If the current anchors do not name an object, use an era-appropriate fixture, tool, container, machine, display frame, shelf, ladder, seal, stall, or handled fixture.
                - For palace, fortress, old-house, ritual, market, dock, mountain, river, or regional/era anchors, prefer physical mechanisms from architecture, route control, storage, tools, water, crowd movement, weather, blocked spaces, or elevated structures before choosing poison.
                - A fall mechanism is allowed only when the current anchors specifically make an elevated fixture, railing, stair, platform, cliff, embankment, crowd bottleneck, or height-related maintenance issue more direct than the other mechanisms. Do not select it from generic route, architecture, old building, stairs, or tourism words alone.
                - If several mechanisms fit equally well, choose the one tied to the most concrete noun in the current anchors, not the most familiar crime template.
                - Do not create a separate applicator object. The WEAPON is the altered anchor-domain carrier itself, not a tool used to alter it.
                - Harmful state must be a condition of the carrier: broken, sharpened, weighted, jammed, rigged, hidden, swapped, weakened, blocked, contaminated, mislabeled, sealed, damaged, coated, or residue-covered.
                METHOD output rules:
                - Return two method fields: method_keyword and method_sentence.
                - method_keyword is the final METHOD answer shown to players as "사인". It must be a compact Korean cause-of-death phrase of 5~24 characters, not a full sentence.
                - method_keyword must prioritize the fatal injury/death route. Good examples: "복부 자상 과다출혈", "목 압박 질식", "익수 질식", "감전사", "두부 둔상", "흉부 관통상". Do not write "사망하게 함".
                - For stabbing/cutting cases, method_keyword must include both the injury action/body part and the death route when available, such as "복부 자상 과다출혈". Do not return only "과다출혈" if the clues point to stabbing, and do not return only "찔러 죽임" if forensic clues point to hemorrhage.
                - method_keyword may include at most one short action verb when needed, but it must not become a long execution sentence.
                - Do not return method_keyword as a single abstract noun or operation label. Forbidden by itself: 잠금해제, 잠금, 해제, 은폐, 조작, 침입, 유인, 사고, 방치, 교란, 위장, 접촉, 투입, 이동, 차단.
                - If a switch, door, gate, route, or device is involved, method_keyword must still be a cause-of-death phrase, e.g. "폐쇄 공간 질식", "노출 전선 감전사", "충돌성 두부 외상", not "잠금해제" or "조작".
                - Avoid lock-release wording unless the approved mechanism is confinement. Do not use "잠금장치를 풀어둔", "잠금장치를 해제한", "잠금을 풀어", or "해제하여" as a default execution pattern.
                - Do not use awkward result phrasing such as "떨어지게 하여", "추락하게 하여", "추락하여", "느슨하게 풀어둔 ...의", or "...의 떨어지게 하여". Prefer compact natural Korean verbs such as "빼", "부숴", "막아", "바꿔", "노출해", "찔러", "내리쳐", "조여".
                - method_sentence must expand method_keyword in one clear sentence: victim routine + altered object/state or attack action + direct physical result. The sentence is for downstream story writing and must not contradict method_keyword.
                CULPRIT must be a Korean person name only. Invent a fresh 2~4 syllable Korean name that is not copied from examples, previous generations, or source text.
                CULPRIT must not be a job, role, relationship, organization, title, or generic label.
                If the culprit concept starts from a role, invent a Korean name for that person and keep the role only as background for the later suspect card.
                WEAPON may be a short playable object keyword.
                MOTIVE may be a short motive phrase, but it must be specific enough to explain why the culprit had to kill now.
                METHOD may be compact, but it must be a concrete act or mechanism, not the result of hiding evidence, not access control alone, and not an investigation conclusion.
                Avoid copying any sample-like answer. Generate fresh values from the current story anchors only.
                Do not create place hints, destination clues, or final-place guessing.
                Use the selected places and research context only as background motifs.
                Never imply that a real crime happened at a real place.
                Do not use immersion-breaking wording such as "real place", "fictional suspect", "needs admin review", or "RAG context".
                Never reuse stale sample answers or names from earlier generations.
                Avoid repeating common stale names, role labels, or abstract keywords from previous outputs. If a value feels like a generic template rather than a noun/conflict from the current anchors, choose a different value.
                Choose fresh culprit, weapon, motive, and method values that fit the selected route and case premise.

                Required JSON shape:
                {
                  "culprit": "...",
                  "weapon": "...",
                  "motive": "...",
                  "method_keyword": "...",
                  "method_sentence": "..."
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
                "- era: " + safePromptText(request == null ? "" : request.getEra()),
                "- theme: " + safePromptText(request == null ? "" : request.getTheme()),
                "- playTime: " + safePromptText(request == null ? "" : request.getPlayTime()),
                "- genre: " + safePromptText(request == null ? "" : request.getSelectedGenreName()),
                "Story anchors to fictionalize:",
                planContext.storyAnchors().isEmpty() ? "(none)" : planContext.storyAnchors().stream().map(anchor -> "- " + safePromptText(anchor)).collect(Collectors.joining("\n")),
                "Historical/cultural/regional motifs without place names or addresses:",
                blank(historicalContext) ? "(none)" : safePromptText(historicalContext));
    }

    private static String buildRecordDomainPolicy(AiEpisodeDraftRequest request) {
        TourApiPlanContext planContext = TourApiPlanInputExtractor.extract(request);
        if (hasRecordAnchor(planContext)) {
            return """
                    If a paper-file domain is explicitly present in the current anchors, it may be used only as a physical clue source. Even then, WEAPON must be an inspectable object or altered fixture, not an abstract information category.
                    """;
        }
        return """
                Current anchors do not contain an explicit paper-file domain. Do not choose paper-file answers for WEAPON or MOTIVE.
                For historic, preserved, institutional, old, or administrative places without such an anchor, choose from architecture, route elements, tools, service objects, materials, crowd flow, ownership conflict, reputation pressure, debt, succession, or access conflict actually supported by the anchors.
                """;
    }

    private static boolean hasRecordAnchor(TourApiPlanContext context) {
        if (context == null) return false;
        String text = compact(String.join(" ",
                String.join(" ", context.storyAnchors()),
                context.historicalContext(),
                context.answerSeedContext()
        ));
        return containsAny(text, "기록", "문서", "자료", "장부", "고서", "소장", "아카이브", "archive", "record", "document", "ledger");
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

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(target)) return true;
        }
        return false;
    }
}
