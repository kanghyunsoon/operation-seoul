package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;

import java.util.List;

final class DraftValidationResultFactory {
    private DraftValidationResultFactory() {
    }

    static AiEpisodeDraftValidationResponse build(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        long errors = findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).count();
        long warns = findings.stream().filter(finding -> "WARN".equals(finding.getSeverity())).count();
        return AiEpisodeDraftValidationResponse.builder()
                .valid(errors == 0)
                .riskScore((int) Math.min(100, errors * 20 + warns * 5))
                .summary(errors == 0 ? "필수 범죄 미스터리 검증을 통과했습니다." : "수정이 필요한 항목이 " + errors + "개 남아 있습니다.")
                .findings(findings)
                .requiredFixes(findings.stream()
                        .filter(finding -> "ERROR".equals(finding.getSeverity()))
                        .map(AiEpisodeDraftValidationResponse.Finding::getMessage)
                        .distinct()
                        .toList())
                .publishChecklist(List.of(
                        "장소 정보는 배경 모티브로만 사용합니다.",
                        "8개 조사 단서는 하나의 사건 진실로 수렴해야 하며 범인, 흉기, 동기, 방법을 모두 추론할 수 있어야 합니다.",
                        "장소 힌트, 정답 유도, 깨진 텍스트가 없어야 합니다."))
                .build();
    }

    static AiEpisodeDraftValidationResponse.Finding finding(
            String severity,
            String code,
            String message,
            Integer missionOrder,
            String fieldPath) {
        return AiEpisodeDraftValidationResponse.Finding.builder()
                .severity(severity)
                .code(code)
                .message(koreanFindingMessage(code, message))
                .missionOrder(missionOrder)
                .fieldPath(fieldPath)
                .autoFixable(false)
                .fixType("REGENERATE")
                .build();
    }

    static String koreanFindingMessage(String code, String fallback) {
        return switch (code) {
            case "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED" -> "finalAnswerKeywordItems에는 범인, 흉기, 동기, 방법 4개 슬롯과 값이 모두 필요합니다.";
            case "TEN_PLACES_REQUIRED" -> "미션 장소는 시작 1개, 조사 8개, 최종 1개로 총 10개여야 합니다.";
            case "ONE_START_REQUIRED" -> "시작 미션은 정확히 1개여야 합니다.";
            case "ONE_FINAL_REQUIRED" -> "최종 장소 미션은 정확히 1개여야 합니다.";
            case "FINAL_UNLOCK_CONDITION_REQUIRED" -> "최종 장소는 조사 미션 8개를 모두 완료한 뒤 자동 공개되어야 합니다.";
            case "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE" -> "최종 장소를 최종 정답을 추리하는 단서로 사용하면 안 됩니다.";
            case "EIGHT_INVESTIGATION_CLUES_REQUIRED" -> "조사 미션은 정확히 8개여야 합니다.";
            case "DEDUCTIVE_CLUE_REQUIRED" -> "조사 미션에는 추리에 직접 기여하는 보상 단서가 필요합니다.";
            case "GENERIC_DEDUCTIVE_CLUE" -> "조사 단서가 너무 일반적입니다. 기록, 지문, 알리바이, 성분 분석처럼 구체적인 사건 정보로 작성해야 합니다.";
            case "DUPLICATE_CLUE" -> "조사 단서가 중복됩니다. 8개 단서는 서로 다른 정보를 제공해야 합니다.";
            case "DIRECT_ANSWER_LEAK" -> "조사 단서가 최종 정답 값을 직접 노출하고 있습니다.";
            case "TARGET_KEYWORD_TYPE_REQUIRED" -> "조사 단서는 범인, 흉기, 동기, 방법 중 하나의 정답 슬롯에 연결되어야 합니다.";
            case "EXACTLY_ONE_SUPPORTED_SLOT_REQUIRED" -> "각 조사 단서는 하나의 정답 슬롯만 지원해야 합니다.";
            case "DEDUCTIVE_CLUE_NOT_ATMOSPHERE" -> "조사 단서는 분위기나 배경 묘사가 아니라 추리 근거여야 합니다.";
            case "CLUE_SLOT_MISMATCH" -> "조사 단서의 내용이 지정된 정답 슬롯과 맞지 않습니다.";
            case "CULPRIT_CLUE_CONTRADICTS_SUSPECT_SET" -> "범인 단서가 세 용의자 밖의 인물을 범인처럼 암시하고 있습니다.";
            case "DESTINATION_HINT_FORBIDDEN" -> "장소 힌트 또는 최종 장소 추리 구조는 사용할 수 없습니다.";
            case "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED" -> "8개 조사 단서가 범인, 흉기, 동기, 방법을 각각 2개씩 지원해야 합니다.";
            case "EXACTLY_THREE_SUSPECTS_REQUIRED" -> "용의자 카드는 정확히 3명이어야 합니다.";
            case "SUSPECT_DETAILS_REQUIRED" -> "각 용의자에는 이름, 알리바이, 의심 사유가 필요합니다.";
            case "SUSPECT_NAMES_MUST_BE_UNIQUE" -> "용의자 3명은 서로 다른 이름이어야 합니다.";
            case "CULPRIT_MUST_BE_SUSPECT" -> "범인 정답은 용의자 카드 3명 중 한 명이어야 합니다.";
            case "FINAL_TRUTH_MUST_EXPLAIN_ANSWERS" -> "진실 요약에는 범인, 흉기, 동기, 방법 4개 정답을 모두 설명해야 합니다.";
            case "EPISODE_TITLE_REQUIRED" -> "에피소드 제목이 필요합니다.";
            case "FICTION_SYNOPSIS_REQUIRED" -> "가상 사건 개요가 필요합니다.";
            case "FINAL_TRUTH_SUMMARY_REQUIRED" -> "최종 진실 요약에는 범인, 흉기, 동기, 방법을 설명해야 합니다.";
            case "EIGHT_EVIDENCE_CARDS_REQUIRED" -> "조사 미션 8개에 대응하는 증거 카드 8개가 필요합니다.";
            case "EVIDENCE_DETAILS_REQUIRED" -> "증거 카드에는 제목과 요약이 필요합니다.";
            case "EVIDENCE_SOURCE_MISSION_REQUIRED" -> "증거 카드는 조사 미션 2번부터 9번까지 각각 하나씩 연결되어야 합니다.";
            case "EVIDENCE_ANSWER_LEAK" -> "증거 카드가 최종 정답 값을 직접 노출하고 있습니다.";
            case "REAL_PLACE_CRIME_IMPLICATION" -> "실제 장소에서 실제 범죄가 발생한 것처럼 쓰면 안 됩니다.";
            case "IMMERSION_BREAKING_TEXT" -> "사용자에게 노출되는 문구에 구현 방식, 검수 표현, 가상 고지처럼 몰입을 깨는 표현이 포함되어 있습니다.";
            case "INVALID_FINAL_ANSWER_KEYWORDS" -> "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.";
            default -> fallback;
        };
    }
}
