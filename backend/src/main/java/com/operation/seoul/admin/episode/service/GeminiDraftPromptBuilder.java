package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GeminiDraftPromptBuilder {
    private static final List<String> SLOT_IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");

    private GeminiDraftPromptBuilder() {
    }

    static String build(AiEpisodeDraftRequest request) {
        return """
                Return JSON only, matching AiEpisodeDraftResponse.EpisodeDraft.
                Write in Korean.
                Genre is fixed to 범죄 미스터리.
                Final answers are exactly CULPRIT, WEAPON, MOTIVE, METHOD.
                Use the approved final answer values exactly. Do not invent a different culprit, weapon, motive, or method.
                The culprit answer value must be one of the 3 suspect displayName values.
                Suspect displayName must be a Korean personal name only, such as "서민재". Do not put role or job in displayName, such as "서민재(운영팀장)".
                Put roles and jobs in relationToVictim or shortDescription instead.
                All suspects, finalTruthSummary, rewardClue values, and evidences must converge to the approved final answers.
                finalTruthSummary must include the approved CULPRIT, WEAPON, MOTIVE, and METHOD values verbatim.
                Never reuse stale sample answers or names: 강수진, 독성 캡슐, 비밀 계약 은폐, 약병 바꿔치기, 독이 섞인 수면제 캡슐, 피해자의 매일 복용 약을 독성 캡슐로 바꿔치기.
                The final place is not a deduction answer. It unlocks automatically after all 8 investigation missions are cleared.
                Use TourAPI, external research notes, reference URLs, admin memo, and place descriptions only as background motifs.
                Do not state or imply that a real crime happened at any real place.
                Do not put selected real place names in fictionSynopsis as the murder scene. Create a fictional indoor location such as a private office, gallery office, research meeting room, archive room, or event preparation room.
                Do not write phrases that break immersion, including "실제 장소", "가상의 용의자", "관리자 검수", "RAG", "TourAPI", "외부 검색".
                Do not create place hints, destination clues, DESTINATION_HINT, DESTINATION_CLUE, FINAL_DESTINATION, or PLACE_HINT.
                Create 10 missions: order 1 START, orders 2-9 ANSWER_HINT, order 10 FINAL.
                The 8 investigation rewardClue values must be distinct deductive clues, not atmosphere.
                Never use generic clue text such as "조사 단서는 ... 판단에 필요한 근거를 제공합니다."
                Each investigation rewardClue must have exactly one internal targetKeywordType for answer-board grouping.
                Player-facing rewardClue text must still work as a mixed evidence chain: each clue should narrow more than one part of the truth through timeline, access, object trace, motive pressure, and routine sequence.
                Do not write visibly separated pairs such as "two culprit clues, two weapon clues". The eight clues should feel like one investigation that gradually converges on culprit, weapon, motive, and method together.
                rewardClue must not directly include final answer values, including the culprit name.
                Investigation rewardClue should avoid suspect display names in general. Use indirect labels such as "a suspect", "the person with access", "the owner of the fingerprint", or "the person shown in CCTV".
                Keep suspect display names in the suspects array and finalTruthSummary only, not in the 8 investigation rewardClue values.
                Before returning JSON, self-check every investigation rewardClue against the approved final answer values.
                If a rewardClue contains the exact approved CULPRIT, WEAPON, MOTIVE, or METHOD value, rewrite it as indirect evidence.
                Example: do not write the culprit name; write "the person with unrestricted office access" or "the owner of the extra fingerprint" instead.
                Example: do not write the weapon answer value; write toxicology, container, residue, or material facts that let the player infer it.
                Example: do not write the motive answer value; write documents, debt records, conflict messages, or benefit facts that imply it.
                Example: do not write the method answer value; write timing, access path, object state, or tampering sequence facts that imply it.
                Suspects must include exactly 3 people, each with alibiSummary and suspiciousPoint.
                Evidences must include exactly 8 cards mapped to sourceMissionOrder 2 through 9.
                actualHistorySummary must explain the historical/cultural motif behind the final place without saying the case is real.

                Do not omit or null these fields: episodeTitle, fictionSynopsis, finalTruthSummary, missions, suspects, evidences.

                Case blueprint:
                - Create a fictional victim, incident setup, cause or mechanism, limited suspect pool, and timeline.
                - The case overview must resemble a locked-room crime mystery: victim found dead or incapacitated, clear cause/mechanism, no obvious forced entry, and exactly 3 suspects present in the plausible incident window.
                - The culprit must be exactly one of the 3 suspects.
                - Each suspect needs a concrete alibiSummary and a concrete suspiciousPoint.
                - Suspect alibiSummary must include the claimed activity during the incident window and what record/witness partially supports it.
                - Suspect suspiciousPoint must include motive pressure, recent conflict, access, missing time, or benefit from the victim's death.
                - Build the truth so CULPRIT, WEAPON, MOTIVE, and METHOD are uniquely deducible only after combining all 8 clues.
                - The 8 clues must be evidence facts such as records, fingerprints, access logs, object traces, CCTV gaps, medical/toxicology facts, contracts, schedules, or witness observations.
                - Do not use simple mood, scenery, tourism facts, route directions, address numbers, sign text, or place-name extraction as deduction clues.
                - Every clue must add different information. Avoid repeating the same fact with different wording.
                - finalAnswerKeywordItems and finalAnswers must contain only the approved 4 answer slots.

                Target story pattern:
                - fictionSynopsis should be a full case overview, not route/place description. Format it like: "A prominent victim is found dead before an important event. Cause is identified. Door/timeline limits outside intrusion. No forced entry. Only 3 suspects remained inside. Victim had ownership, contract, inheritance, research, or business conflicts."
                - fictionSynopsis must include at least five concrete beats: victim identity, event scheduled soon, cause/mechanism, locked room or CCTV/time gap, exactly 3 suspects, and conflict background.
                - Suspect cards should read like: name and relation, alibi during the estimated incident time, supporting record, suspicious point, and why the person remains plausible.
                - Final truth should state culprit, weapon, method, and motive with the approved answer values, then explain why the other two suspects are weakened by the clues.
                - The 8 rewardClue values should read as a progressive evidence chain: restricted access trace tied to evidence storage, alibi narrowing tied to fingerprint or CCTV, material analysis tied to victim routine, object state tied to preparation timing, conflict document tied to benefit, pressure message tied to concealment, routine timing tied to execution, and final cross-check excluding the other two suspects.

                Slot-specific clue rules:
                - CULPRIT-targeted clues should primarily identify access, fingerprints, CCTV, alibi gaps, or exclusive opportunity, but may also mention object storage or timing if it helps narrow the whole truth. Do not write the culprit name.
                - WEAPON-targeted clues should primarily identify the object, material, residue, damage pattern, analysis result, or physical trace, but may also connect that trace to victim routine or access path.
                - MOTIVE-targeted clues should primarily identify revenge, conflict, loss, debt, inheritance, threat, benefit, betrayal, or concealment pressure, but may also connect the pressure to who had access.
                - METHOD-targeted clues should primarily identify replacement, tampering, injection, concealment, timing, access path, misdirection, or execution sequence, but may also mention why other suspects are weakened.
                - CULPRIT rewardClue should naturally include at least one of these Korean evidence anchors: 지문, 출입, 접근, 알리바이, 동선, 기록, CCTV, 목격, 권한, 일치, 용의자.
                - WEAPON rewardClue should naturally include at least one of these Korean evidence anchors: 흉기, 도구, 독극물, 물질, 성분, 검출, 흔적, 파손, 잔류물, 분석.
                - MOTIVE rewardClue should naturally include at least one of these Korean evidence anchors: 동기, 복수, 계약, 분쟁, 유산, 손실, 채무, 협박, 이익, 은폐, 배신, 갈등.
                - METHOD rewardClue should naturally include at least one of these Korean evidence anchors: 방법, 교체, 조작, 삽입, 주입, 은폐, 위장, 접근, 시간, 경로, 순서.

                Required mission contract:
                - START: markerType START, clueRole START, publicMarkerType START, finalPlace false.
                - Investigation: markerType ANSWER_HINT, clueRole ANSWER_HINT, publicMarkerType ANSWER_HINT, finalPlace false.
                - Orders 2-9 must each include a concrete rewardClue sentence. Never leave rewardClue null, empty, or templated.
                - Invalid rewardClue examples: "2번 조사 단서는 범인 판단에 필요한 근거를 제공합니다.", "이 단서는 정답 추리에 필요합니다.", "현장에서 단서가 발견되었다."
                - Valid rewardClue examples must name a concrete record, trace, timing, object state, document, witness observation, or analysis result.
                - Required internal tagging for answer board grouping:
                  order 2 targetKeywordType CULPRIT, but write the clue as access/opportunity evidence.
                  order 3 targetKeywordType CULPRIT, but write the clue as alibi narrowing or trace matching evidence.
                  order 4 targetKeywordType WEAPON, but write the clue as toxicology/material evidence.
                  order 5 targetKeywordType WEAPON, but write the clue as object/container evidence.
                  order 6 targetKeywordType MOTIVE, but write the clue as conflict/document/benefit evidence.
                  order 7 targetKeywordType MOTIVE, but write the clue as pressure/message/revenge evidence.
                  order 8 targetKeywordType METHOD, but write the clue as routine/timing evidence.
                  order 9 targetKeywordType METHOD, but write the clue as tampering/access-sequence evidence.
                - Do not make the clue text read like "culprit clue 1", "weapon clue 2", or any visible category checklist.
                - FINAL: markerType FINAL, clueRole FINAL_PLACE, publicMarkerType ANSWER_HINT, finalPlace true, unlockCondition ALL_INVESTIGATION_MISSIONS_CLEARED.

                Required JSON shape:
                {
                  "episodeTitle": "...",
                  "subtitle": "...",
                  "genre": "범죄 미스터리",
                  "fictionSynopsis": "사건 개요. 피해자, 사망/피해 정황, 제한된 용의자 범위, 시간대를 포함.",
                  "missionDescription": "8개 조사 단서로 범인, 흉기, 동기, 방법을 추론한다.",
                  "finalTruthSummary": "범인: <approved CULPRIT>. 흉기: <approved WEAPON>. 동기: <approved MOTIVE>. 방법: <approved METHOD>. 네 정답이 왜 유일한지 설명.",
                  "actualHistorySummary": "최종 장소의 역사/문화 모티브 설명.",
                  "missions": [
                    {"order":1,"markerType":"START","publicMarkerType":"START","clueRole":"START","finalPlace":false},
                    {"order":2,"markerType":"ANSWER_HINT","publicMarkerType":"ANSWER_HINT","clueRole":"ANSWER_HINT","finalPlace":false,"targetKeywordType":"CULPRIT","supportsKeywordSlots":["CULPRIT"],"rewardClue":"범인 이름 없이 접근 권한이나 알리바이 공백을 보여주는 증거"},
                    {"order":10,"markerType":"FINAL","publicMarkerType":"ANSWER_HINT","clueRole":"FINAL_PLACE","finalPlace":true,"unlockCondition":"ALL_INVESTIGATION_MISSIONS_CLEARED"}
                  ],
                  "suspects": [
                    {"displayName":"...","relationToVictim":"...","alibiSummary":"...","suspiciousPoint":"..."}
                  ],
                  "evidences": [
                    {"title":"...","type":"STORY_CLUE","textSummary":"rewardClue와 연결되는 구체적 증거","sourceMissionOrder":2}
                  ]
                }

                Context:
                """ + buildStoryGenerationContext(request);
    }

    private static String buildStoryGenerationContext(AiEpisodeDraftRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        builder.append("routePolicy:\n");
        builder.append("- Do not use real route place names, addresses, shop names, restaurant names, POI names, reference URLs, or external research notes in story generation.\n");
        builder.append("- Real route places are only mission map anchors. The server attaches mission.placeName after draft generation.\n");
        builder.append("- Write fictional crime locations only: private gallery office, locked study, research meeting room, archive room, preparation room, or similar indoor scenes.\n");
        builder.append("- Missions should be ordered investigation beats, not destination hints or place-name puzzles.\n");
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
