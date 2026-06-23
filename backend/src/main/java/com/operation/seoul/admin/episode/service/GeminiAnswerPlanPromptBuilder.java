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
                Final answer values may be short Korean keywords when they are playable, such as 칼, 망치, 톱, 독살, 교살, 추락, or 투여.
                Derive the four final answer values from the story anchors below: TourAPI historical incidents, cultural conflicts, records, materials, rituals, industries, disputes, preservation facts, or fallback regional/era/theme background when direct incidents are unavailable.
                Do not choose a generic domain template just because a place is a museum, gallery, cafe, market, mountain, or station.
                The answer values should feel like a fictionalized case built from the anchors' concrete nouns and conflicts.
                Before returning JSON, internally follow this generation process:
                1. Identify the strongest anchor domain from the context, such as records, newspapers, palace administration, contracts, routes, transit, tourism change, old-house reuse, disputes, preservation, rituals, commerce, regional identity, era background, or institutional history.
                2. Choose one murder mechanism that fits the anchor domain before choosing the weapon. Use varied mechanisms across generations: blunt force, stabbing/cutting, strangulation, staged fall, drowning, collision, confinement/freezing, explosion, allergy trigger, poisoning, or psychological coercion. Do not default to poisoning, contamination, toxic residue, or skin contact unless the anchor domain strongly supports that mechanism.
                3. Build the WEAPON from nouns that appear in that anchor domain. The carrier should be an object, fixture, tool, route element, storage object, vehicle-related object, or handled material a character could plausibly use in the chosen mechanism.
                4. Add danger by changing the state of that carrier according to the murder mechanism: loosened, broken, sharpened, weighted, locked, removed, jammed, rigged, hidden, swapped, weakened, contaminated, mislabeled, or treated. Use common state words, not newly invented specialist materials.
                5. Build MOTIVE from the same anchor domain's conflict: concealment, ownership, falsified record, broken contract, debt, responsibility, access, or reputation.
                6. Build METHOD by naming the weapon, altered part/state, victim's normal interaction, and death/attack route. METHOD must be more specific than WEAPON and physically plausible.
                7. Select only keywords that already satisfy the anchor-domain and METHOD construction rules before returning JSON.
                Domain material selection rules:
                - For route, transit, tourism-change, old-house reuse, and street-history anchors, choose carriers from signs, guide ropes, railings, steps, gates, locks, route barriers, vehicle-adjacent fixtures, route maps, guide sheets, permit records, lease contracts, reservation ledgers, visitor ledgers, envelopes, tickets, or handled fixtures.
                - For record, archive, newspaper, contract, preservation, and administration anchors, choose carriers from storage shelves, archive ladders, bindings, document weights, seals, cabinets, record sheets, original document envelopes, preservation containers, ledgers, certificates, or handled storage objects.
                - For palace, fortress, old-house, ritual, market, dock, mountain, river, or regional/era anchors, prefer physical mechanisms from architecture, route control, storage, tools, water, height, crowd movement, weather, or locked spaces before choosing poison.
                - Do not create a separate applicator object. The WEAPON is the altered anchor-domain carrier itself, not a tool used to alter it.
                - Harmful state must be a condition of the carrier: loosened, broken, sharpened, weighted, locked, jammed, rigged, hidden, swapped, weakened, contaminated, mislabeled, sealed, damaged, coated, or residue-covered.
                METHOD output rules:
                - Return two method fields: method_keyword and method_sentence.
                - method_keyword is the final METHOD answer and may be a short Korean keyword or phrase.
                - method_sentence may add detail for downstream story writing, but it is not used to reject short method_keyword values.
                CULPRIT must be a Korean person name only, such as 김도윤 or 한서윤.
                CULPRIT must not be a job, role, relationship, organization, title, or generic label such as 기록 보관 담당자, 관리자, 연구원, 재단 이사장, 관계자, 용의자, or 피해자의 동료.
                If the culprit concept starts from a role, invent a Korean name for that person and keep the role only as background for the later suspect card.
                WEAPON may be a short playable object keyword.
                MOTIVE may be a short motive keyword or phrase.
                Avoid copying any sample-like answer. Generate fresh values from the current story anchors only.
                Do not create place hints, destination clues, or final-place guessing.
                Use the selected places and research context only as background motifs.
                Never imply that a real crime happened at a real place.
                Do not use immersion-breaking wording such as "real place", "fictional suspect", "needs admin review", or "RAG context".
                Never reuse stale sample answers or names from earlier generations.
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
                "- theme: " + safePromptText(request == null ? "" : request.getTheme()),
                "- playTime: " + safePromptText(request == null ? "" : request.getPlayTime()),
                "- genre: " + safePromptText(request == null ? "" : request.getSelectedGenreName()),
                "Story anchors to fictionalize:",
                planContext.storyAnchors().isEmpty() ? "(none)" : planContext.storyAnchors().stream().map(anchor -> "- " + safePromptText(anchor)).collect(Collectors.joining("\n")),
                "Historical/cultural/regional motifs without place names or addresses:",
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
