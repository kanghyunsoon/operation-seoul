package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DraftInvestigationClueGuardrail {
    private static final Map<String, String> SLOT_LABELS = FinalAnswerSlots.LABELS;

    private DraftInvestigationClueGuardrail() {
    }

    static void applyCanonicalInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        Map<String, String> answers = FinalAnswerContractSupport.approvedAnswers(request);
        List<String> clues = canonicalInvestigationClues(answers);
        List<String> targets = List.of("CULPRIT", "CULPRIT", "WEAPON", "WEAPON", "MOTIVE", "MOTIVE", "METHOD", "METHOD");
        List<AiEpisodeDraftResponse.MissionDraft> missions = safeList(draft.getMissions());
        int clueIndex = 0;
        for (AiEpisodeDraftResponse.MissionDraft mission : missions) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            if (clueIndex >= clues.size()) break;
            String target = targets.get(clueIndex);
            mission.setTargetKeywordType(target);
            mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
            mission.setRewardClueSlotId("ANSWER_CLUE");
            mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
            mission.setSupportsKeywordSlots(List.of(target));
            mission.setRewardClue(clues.get(clueIndex));
            clueIndex++;
        }
    }

    private static List<String> canonicalInvestigationClues(Map<String, String> answers) {
        if (answers != null) {
            String weapon = compact(answers.get("WEAPON"));
            String method = compact(answers.get("METHOD"));
            String objectLabel = cleanEvidenceObjectLabel(weapon, method);
            String containerLabel = cleanEvidenceContainerLabel(weapon, method);
            String motiveDocument = cleanMotiveDocumentLabel(compact(answers.get("MOTIVE")));
            String routineLabel = cleanMethodRoutineLabel(method);
            return List.of(
                    "출입 기록과 알리바이를 대조하면 사건 직전 피해자의 업무 공간에 혼자 접근한 사람은 한 명뿐이며, 같은 시간대에 " + containerLabel + " 보관 위치도 열려 있었다.",
                    containerLabel + "에서 피해자의 흔적 외 추가 지문 하나가 검출됐고, 그 지문 주인의 알리바이에는 CCTV 공백과 맞물리는 짧은 이동 시간이 남아 있다.",
                    "감식 결과 피해자는 음식 전체가 아니라 사건 직전 반복적으로 만진 " + objectLabel + "의 표면 성분과 접촉한 뒤 급성 반응을 보인 것으로 좁혀졌다.",
                    objectLabel + "의 오염 흔적과 물질 성분은 오래된 것이 아니라 사건 당일 새로 묻은 상태였고, 평소 보관 위치가 아닌 제한 구역에서 옮겨진 정황이 확인됐다.",
                    "사건 일주일 전 작성된 " + motiveDocument + "에는 피해자가 공개하려던 결정 때문에 내부 관계자 한 명이 직위나 계약상 손실을 볼 내용과 갈등 기록이 적혀 있었다.",
                    "삭제된 메시지와 목격 진술을 대조하면 그 관계자는 공개를 막아야 한다는 압박을 받았고, 피해자와 언쟁한 직후 감정적 문장을 남겼다.",
                    "피해자는 사건 직전에도 평소 절차대로 " + routineLabel + "을 확인했으며, 증상 발생 시각은 그 반복 행동 직후로 맞아떨어진다.",
                    "시간표, 지문, 오염 시점, 문서 기록을 겹치면 알리바이가 남는 두 명은 접근 권한과 동기, 조작 순서 조건을 동시에 만족하지 못한다."
            );
        }
        String weapon = compact(answers.get("WEAPON"));
        String motive = compact(answers.get("MOTIVE"));
        String method = compact(answers.get("METHOD"));
        String objectLabel = evidenceObjectLabel(weapon, method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        String motiveDocument = motiveDocumentLabel(motive);
        String routineLabel = methodRoutineLabel(method);
        return List.of(
                containerLabel + "에서는 피해자의 흔적 외에 업무 공간을 자유롭게 출입할 수 있는 한 사람의 추가 지문만 검출되었다.",
                "사건 시간대 출입 기록과 알리바이 대조 결과, 두 명의 용의자는 주요 시각의 동선이 외부 기록으로 확인되었다.",
                objectLabel + " 분석 결과 일반 성분과 다른 독성 물질이 검출되었고, 같은 성분은 다른 음식이나 주변 물건에서는 확인되지 않았다.",
                containerLabel + " 안쪽 잔류물과 폐기 흔적이 서로 맞아, 독성 물질이 사건 직전 준비물에만 섞였다는 점이 드러났다.",
                "사건 전 작성된 " + motiveDocument + "에는 피해자와 가까운 인물에게 불리한 결정과 은폐해야 할 문제가 함께 기록되어 있었다.",
                "피해자와 가까운 직원이 사건 직전 강한 불만과 압박감을 드러냈다는 메시지 기록이 남아 있었다.",
                "피해자는 사건 전 일정한 순서로 " + routineLabel + "를 확인하거나 사용했고, 그 준비물은 제한된 업무 공간에 보관되어 있었다.",
                routineLabel + " 교체 추정 시간과 보관 지점 접근 기록이 같은 업무 동선 위에서 겹친다."
        );
    }

    private static String cleanEvidenceObjectLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "서류", "봉투", "문서", "장부")) return "문서 봉투";
        if (containsAny(text, "붓펜", "잉크", "서명", "펜")) return "서명 도구";
        if (containsAny(text, "향수", "분사")) return "휴대용 분사 물품";
        if (containsAny(text, "약", "캡슐", "복용")) return "복용 물품";
        if (containsAny(text, "음료", "커피", "차", "와인", "잔", "보온병")) return "음료 용기";
        return "현장 물증";
    }

    private static String cleanEvidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "서류", "봉투", "문서", "장부")) return "문서 보관함";
        if (containsAny(text, "붓펜", "잉크", "서명", "펜")) return "필기구 보관함";
        if (containsAny(text, "향수", "분사")) return "개인 소지품 보관함";
        if (containsAny(text, "약", "캡슐", "복용")) return "약품 보관함";
        if (containsAny(text, "음료", "커피", "차", "와인", "잔", "보온병")) return "음료 준비대";
        return "증거 보관 지점";
    }

    private static String cleanMotiveDocumentLabel(String motive) {
        if (containsAny(motive, "위작", "전시", "작품", "감정")) return "감정 보고서";
        if (containsAny(motive, "밀수", "장부", "계약", "은폐")) return "비공개 계약 문서";
        if (containsAny(motive, "연구", "특허", "논문", "조작")) return "연구 감사 문서";
        if (containsAny(motive, "횡령", "투자", "손실", "채무")) return "회계 검토 문서";
        if (containsAny(motive, "유산", "상속")) return "상속 관련 문서";
        return "내부 결정 문서";
    }

    private static String cleanMethodRoutineLabel(String method) {
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        if (containsAny(method, "서명", "붓펜", "펜", "잉크")) return "서명 확인 절차";
        if (containsAny(method, "향수", "분사")) return "현장 준비물 사용";
        if (containsAny(method, "약", "캡슐", "복용")) return "반복 복용하던 약";
        if (containsAny(method, "음료", "마시", "커피", "차", "와인")) return "반복되던 음료 준비";
        return "반복되던 확인 절차";
    }

    private static String evidenceObjectLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 용기";
        if (containsAny(text, "시약", "실험", "연구")) return "시료 용기";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약물 용기";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 봉투";
        return "현장 물증";
    }

    private static String evidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 보관대";
        if (containsAny(text, "시약", "실험", "연구")) return "실험 준비물 보관함";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약통";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 보관함";
        return "증거 보관 지점";
    }

    private static String motiveDocumentLabel(String motive) {
        if (containsAny(motive, "연구", "조작", "논문", "실험", "시약")) return "연구 감사 문서";
        if (containsAny(motive, "해고", "계약", "인수인계")) return "인사 문서";
        if (containsAny(motive, "채무", "손실", "횡령", "재정", "금전")) return "회계 문서";
        if (containsAny(motive, "유산", "상속")) return "상속 관련 문서";
        return "내부 문서";
    }

    private static String methodRoutineLabel(String method) {
        if (containsAny(method, "음료", "커피", "차", "마시는")) return "매일 마시던 음료";
        if (containsAny(method, "약", "캡슐", "수면제", "복용")) return "매일 복용하던 약";
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        return "반복되던 준비물";
    }

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}