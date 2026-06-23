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
                - method_keyword is a compressed Korean label for the method, not the final METHOD answer.
                - method_keyword must compress method_sentence into a short noun phrase built from the same carrier or interaction plus the crime mechanism.
                - method_keyword must not be a full sentence and must not end with action endings such as 함, 하게 함, or 되도록 함.
                - method_keyword must not introduce a different carrier, different contact route, or different crime mechanism from method_sentence.
                - method_sentence is the final METHOD answer. It must be one complete Korean sentence of at least 25 characters.
                - method_sentence must contain the exact weapon string from the weapon field without changing or shortening it.
                - method_sentence must contain these four concrete elements: weapon/carrier, altered_part_or_state, victim_routine, and death_or_attack_route.
                - carrier means the object, fixture, tool, route element, storage object, vehicle-related object, water/height/locked-space element, or handled material used in the mechanism.
                - altered_part_or_state means the changed part or condition, such as loosened railing, jammed lock, sharpened edge, weighted object, hidden obstruction, broken step, swapped sign, locked chamber, exposed wire, allergen-coated item, or contaminated surface.
                - victim_routine must be a normal action the victim performs, such as checking, opening, signing, passing, climbing, leaning, moving, following a route, storing, retrieving, entering, or inspecting.
                - death_or_attack_route must name the fatal route, such as strangulation, fall, stabbing/cutting, blunt-force impact, drowning, collision, freezing/confinement, explosion, allergy reaction, poisoning, or coerced self-harm.
                - method_sentence should follow this shape: "<weapon>를/을 피해자가 <routine>할 때 <death_or_attack_route>로 이어지게 함".
                - METHOD must not be a short result phrase such as "피부에 닿게 함", "호흡기 질환을 유발", "추락하게 함", or "죽게 함".
                CULPRIT must be a new fictional modern Korean person name, not a role, occupation, historical name, literary name, mythic name, or public figure.
                WEAPON must include both an anchor-supported carrier object and the harmful altered state.
                MOTIVE must be a concrete pressure, secret, dispute, contract, record, debt, ownership issue, or cover-up reason anchored in the TourAPI motifs.
                method_sentence must be more specific than weapon and must not merely restate weapon or describe only the final result.
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
