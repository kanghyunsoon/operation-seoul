package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class DraftNarrativeGuardrail {
    private DraftNarrativeGuardrail() {
    }

    static boolean shouldRepairSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        String synopsis = trim(draft == null ? "" : draft.getFictionSynopsis());
        String compacted = compact(synopsis);
        if (synopsis.length() < 140) return true;
        if (!containsAny(compacted, "피해자", "숨진", "사망", "발견", "외부침입", "잠겨", "용의자", "세명", "3명")) {
            return true;
        }
        for (AiEpisodeDraftRequest.PlaceInput place : request == null || request.getPlaces() == null ? List.<AiEpisodeDraftRequest.PlaceInput>of() : request.getPlaces()) {
            String placeName = trim(place.getName());
            if (placeName.length() >= 3 && synopsis.contains(placeName)) {
                return true;
            }
        }
        return false;
    }

    static boolean synopsisMentionsAllSuspects(AiEpisodeDraftResponse.EpisodeDraft draft) {
        String synopsis = compact(draft.getFictionSynopsis());
        if (blank(synopsis)) return false;
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        return suspects.stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftResponse.SuspectDraft::getDisplayName)
                .filter(name -> !blank(name))
                .allMatch(name -> synopsis.contains(compact(name)));
    }

    static boolean redactRealPlaceNamesFromStoryFields(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        if (draft == null || request == null || request.getPlaces() == null) return false;
        List<String> placeNames = request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .map(DraftNarrativeGuardrail::trim)
                .filter(name -> name.length() >= 3)
                .distinct()
                .toList();
        if (placeNames.isEmpty()) return false;
        String beforeDraftText = String.join(" ",
                trim(draft.getEpisodeTitle()),
                trim(draft.getSubtitle()),
                trim(draft.getFictionSynopsis()),
                trim(draft.getMissionDescription()),
                trim(draft.getFinalTruthSummary()),
                trim(draft.getActualHistorySummary()));
        boolean changed = containsAnyPlaceName(beforeDraftText, placeNames);
        draft.setEpisodeTitle(redactRealPlaceNames(draft.getEpisodeTitle(), placeNames, "case scene"));
        draft.setSubtitle(redactRealPlaceNames(draft.getSubtitle(), placeNames, "case scene"));
        draft.setFictionSynopsis(redactRealPlaceNames(draft.getFictionSynopsis(), placeNames, "case scene"));
        draft.setMissionDescription(redactRealPlaceNames(draft.getMissionDescription(), placeNames, "investigation point"));
        draft.setFinalTruthSummary(redactRealPlaceNames(draft.getFinalTruthSummary(), placeNames, "case scene"));
        draft.setActualHistorySummary(redactRealPlaceNames(draft.getActualHistorySummary(), placeNames, "final point"));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            String before = String.join(" ",
                    trim(mission.getStoryText()),
                    trim(mission.getQuestionText()),
                    trim(mission.getRewardClue()));
            mission.setStoryText(redactRealPlaceNames(mission.getStoryText(), placeNames, "investigation point"));
            mission.setQuestionText(redactRealPlaceNames(mission.getQuestionText(), placeNames, "investigation point"));
            mission.setRewardClue(redactRealPlaceNames(mission.getRewardClue(), placeNames, "investigation point"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            String before = String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary()));
            evidence.setTitle(redactRealPlaceNames(evidence.getTitle(), placeNames, "case file"));
            evidence.setTextSummary(redactRealPlaceNames(evidence.getTextSummary(), placeNames, "case file"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        return changed;
    }

    static boolean normalizeSuspectVictimReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null || draft.getSuspects() == null) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.SuspectDraft suspect : draft.getSuspects()) {
            if (suspect == null) continue;
            String shortDescription = normalizeVictimReference(suspect.getShortDescription());
            String relationToVictim = normalizeVictimReference(suspect.getRelationToVictim());
            String suspiciousPoint = normalizeVictimReference(suspect.getSuspiciousPoint());
            String alibiSummary = normalizeVictimReference(suspect.getAlibiSummary());
            changed = changed
                    || !Objects.equals(shortDescription, suspect.getShortDescription())
                    || !Objects.equals(relationToVictim, suspect.getRelationToVictim())
                    || !Objects.equals(suspiciousPoint, suspect.getSuspiciousPoint())
                    || !Objects.equals(alibiSummary, suspect.getAlibiSummary());
            suspect.setShortDescription(shortDescription);
            suspect.setRelationToVictim(relationToVictim);
            suspect.setSuspiciousPoint(suspiciousPoint);
            suspect.setAlibiSummary(alibiSummary);
        }
        return changed;
    }

    private static String normalizeVictimReference(String value) {
        if (value == null) return null;
        return value.replace("김준혁", "한태준");
    }

    private static String redactRealPlaceNames(String value, List<String> placeNames, String replacement) {
        if (blank(value)) return value;
        String result = value;
        for (String placeName : placeNames) {
            result = result.replace(placeName, replacement);
        }
        return result;
    }

    private static boolean containsAnyPlaceName(String value, List<String> placeNames) {
        if (blank(value)) return false;
        return placeNames.stream().anyMatch(value::contains);
    }

    static String canonicalSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, String weapon, String motive, String method) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        String first = suspectName(suspects, 0, "용의자 A");
        String second = suspectName(suspects, 1, "용의자 B");
        String third = suspectName(suspects, 2, "용의자 C");
        String weaponPhrase = blank(weapon) ? "독성 물질" : weapon;
        String routineLabel = methodRoutineLabel(method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        CaseSynopsisTemplate template = caseSynopsisTemplate(weapon, motive, method);
        return template.victimIntro() + "\n\n"
                + "사인은 " + weaponPhrase + "에서 검출된 독성 성분과 연결된 급성 반응으로 추정되었다.\n\n"
                + template.lockedRoomBeat() + " 현장에는 몸싸움의 흔적이 없었고, 독성 물질이 어떤 경로로 피해자에게 닿았는지는 즉시 밝혀지지 않았다.\n\n"
                + "수사 결과, 사건 추정 시간대에 내부에 남아 있었던 인물은 " + first + ", " + second + ", " + third + " 세 명뿐이었다.\n\n"
                + template.conflictBeat(defaultIfBlank(motive, template.defaultMotive())) + " 플레이어는 세 용의자의 알리바이, "
                + containerLabel + " 접근 기록, " + routineLabel + " 변조 흔적, 그리고 현장 기록의 공백을 대조해 범인과 범행 방식을 밝혀야 한다.";
    }

    private static CaseSynopsisTemplate caseSynopsisTemplate(String weapon, String motive, String method) {
        String text = compact(String.join(" ", trim(weapon), trim(motive), trim(method)));
        if (containsAny(text, "항만", "화물", "밀수", "장부", "서류", "봉투")) {
            return new CaseSynopsisTemplate(
                    "항만 물류 감사관 한태준이 비공개 감사 보고회를 하루 앞둔 밤, 운영사 회의실 안쪽 자료 검토실에서 숨진 채 발견되었다.",
                    "자료 검토실 출입문은 전자 잠금장치로 닫혀 있었고 외부 침입 기록은 남아 있지 않았다.",
                    "한태준은 최근 항만 물품 거래와 내부 장부를 대조하며 비정상적인 화물 흐름을 추적하고 있었지만, %s를 둘러싼 이해관계가 여러 사람에게 치명적인 압박이 되고 있었다.",
                    "밀수 장부 은폐"
            );
        }
        if (containsAny(text, "연구", "실험", "시약", "논문", "특허")) {
            return new CaseSynopsisTemplate(
                    "바이오 연구소 책임자 한태준이 신약 투자 발표회를 하루 앞둔 밤, 연구동 회의실에서 숨진 채 발견되었다.",
                    "회의실은 내부 보안 카드로 잠긴 상태였고 CCTV는 사건 직전 짧은 공백을 보였다.",
                    "한태준은 최근 연구 성과와 특허 권리를 재정리하고 있었지만, %s를 둘러싼 갈등이 연구팀 내부에 깊게 남아 있었다.",
                    "연구 조작 기록 은폐"
            );
        }
        if (containsAny(text, "미술", "전시", "갤러리", "작품", "위작", "붓펜", "잉크", "서명")) {
            return new CaseSynopsisTemplate(
                    "유명 미술품 수집가 한태준이 개인 갤러리 개관 행사 전날 밤, 자신의 집무실에서 숨진 채 발견되었다.",
                    "집무실 문은 안에서 잠겨 있었고 외부 침입 흔적은 발견되지 않았다.",
                    "한태준은 최근 고가 작품의 감정 결과와 전시 공개를 앞두고 있었지만, %s를 둘러싼 이해관계가 관계자들을 압박하고 있었다.",
                    "위작 전시 의혹 은폐"
            );
        }
        if (containsAny(text, "카페", "와인", "잔", "음료", "식당", "보온병")) {
            return new CaseSynopsisTemplate(
                    "외식 브랜드 투자자 한태준이 신규 매장 계약 발표 전날 밤, 비공개 시음 회의실에서 숨진 채 발견되었다.",
                    "회의실 출입 기록은 내부 관계자 카드만 남아 있었고 외부 침입 흔적은 없었다.",
                    "한태준은 최근 투자금 흐름과 매장 운영권을 재검토하고 있었지만, %s를 둘러싼 갈등이 관계자들에게 큰 위협이 되고 있었다.",
                    "투자금 횡령 발각 은폐"
            );
        }
        return new CaseSynopsisTemplate(
                "중요 계약을 앞둔 사업가 한태준이 발표 전날 밤, 제한 구역 안쪽 회의실에서 숨진 채 발견되었다.",
                "회의실 출입문은 내부에서 잠긴 상태였고 외부 침입 흔적은 발견되지 않았다.",
                "한태준은 최근 내부 계약과 권리 관계를 재정리하고 있었지만, %s를 둘러싼 이해관계가 세 용의자 모두에게 부담으로 작용하고 있었다.",
                "비공개 계약 은폐"
        );
    }

    private static String suspectName(List<AiEpisodeDraftResponse.SuspectDraft> suspects, int index, String fallback) {
        if (suspects == null || suspects.size() <= index || suspects.get(index) == null) {
            return fallback;
        }
        return defaultIfBlank(suspects.get(index).getDisplayName(), fallback);
    }

    private record CaseSynopsisTemplate(String victimIntro, String lockedRoomBeat, String conflictBeatFormat, String defaultMotive) {
        String conflictBeat(String motive) {
            return String.format(conflictBeatFormat, motive);
        }
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

    private static String evidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 보관대";
        if (containsAny(text, "시약", "실험", "연구")) return "실험 준비물 보관함";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약통";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 보관함";
        return "증거 보관 지점";
    }

    private static String methodRoutineLabel(String method) {
        if (containsAny(method, "음료", "커피", "차", "마시는")) return "매일 마시던 음료";
        if (containsAny(method, "약", "캡슐", "수면제", "복용")) return "매일 복용하던 약";
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        return "반복하던 준비물";
    }
}
