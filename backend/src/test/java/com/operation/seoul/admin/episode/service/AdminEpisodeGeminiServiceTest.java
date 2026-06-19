package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminEpisodeGeminiServiceTest {
    private final AdminEpisodeGeminiService service = new AdminEpisodeGeminiService(new ObjectMapper());

    @Test
    void appliesFixedCrimeMysteryAnswerContract() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);

        applyApprovedContract(draft, source);

        assertEquals("범죄 미스터리", draft.getGenre());
        assertEquals("범죄 미스터리", draft.getSelectedGenre());
        assertEquals(4, draft.getFinalAnswerKeywords().size());
        assertEquals("강수진", draft.getFinalAnswers().getCulprit());
        assertEquals("독성 캡슐", draft.getFinalAnswers().getWeapon());
        assertEquals("비밀 계약 은폐", draft.getFinalAnswers().getMotive());
        assertEquals("약병 바꿔치기", draft.getFinalAnswers().getMethod());
        assertTrue(draft.getFinalAnswer().contains("범인: 강수진"));
    }

    @Test
    void validatesTenPlaceEightClueStructure() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);
        request.setSourceInput(source);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertTrue(result.isValid());
        assertFalse(result.getFindings().stream().anyMatch(finding -> "DESTINATION_HINT_FORBIDDEN".equals(finding.getCode())));
    }

    @Test
    void rejectsDirectAnswerLeakInInvestigationClue() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.getMissions().get(1).setRewardClue("추가 지문이 강수진의 지문과 일치한다.");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "DIRECT_ANSWER_LEAK".equals(finding.getCode())));
    }

    @Test
    void rejectsMojibakeGeneratedText() throws Exception {
        AiEpisodeDraftRequest source = sourceInput();
        AiEpisodeDraftResponse.EpisodeDraft draft = playableDraft(source);
        applyApprovedContract(draft, source);
        draft.setEpisodeTitle("조선 후기, 속초의 그림자 譏硫え ???");

        AiEpisodeDraftValidationRequest request = new AiEpisodeDraftValidationRequest();
        request.setDraft(draft);

        AiEpisodeDraftValidationResponse result = service.validateDraft(request);

        assertFalse(result.isValid());
        assertTrue(result.getFindings().stream().anyMatch(finding -> "MOJIBAKE_TEXT_DETECTED".equals(finding.getCode())));
    }

    private void applyApprovedContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest source) throws Exception {
        Method method = AdminEpisodeGeminiService.class.getDeclaredMethod(
                "applyApprovedFinalAnswerContract",
                AiEpisodeDraftResponse.EpisodeDraft.class,
                AiEpisodeDraftRequest.class,
                List.class
        );
        method.setAccessible(true);
        method.invoke(service, draft, source, new ArrayList<String>());
    }

    private AiEpisodeDraftRequest sourceInput() {
        AiEpisodeDraftRequest request = new AiEpisodeDraftRequest();
        request.setSelectedGenreId("CRIME_MYSTERY");
        request.setSelectedGenreName("범죄 미스터리");
        request.setFinalAnswerKeywordItems(List.of(
                keyword("CULPRIT", "범인", "강수진"),
                keyword("WEAPON", "흉기", "독성 캡슐"),
                keyword("MOTIVE", "동기", "비밀 계약 은폐"),
                keyword("METHOD", "방법", "약병 바꿔치기")
        ));
        AiEpisodeDraftRequest.FinalAnswersInput answers = new AiEpisodeDraftRequest.FinalAnswersInput();
        answers.setCulprit("강수진");
        answers.setWeapon("독성 캡슐");
        answers.setMotive("비밀 계약 은폐");
        answers.setMethod("약병 바꿔치기");
        request.setFinalAnswers(answers);

        List<AiEpisodeDraftRequest.PlaceInput> places = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            AiEpisodeDraftRequest.PlaceInput place = new AiEpisodeDraftRequest.PlaceInput();
            place.setName("테스트 장소 " + (i + 1));
            place.setAddress("서울 테스트로 " + (i + 1));
            place.setLatitude(37.5 + i * 0.001);
            place.setLongitude(126.9 + i * 0.001);
            place.setRole(i == 0 ? "START" : i == 9 ? "FINAL" : "ANSWER_HINT");
            places.add(place);
        }
        request.setPlaces(places);
        return request;
    }

    private AiEpisodeDraftRequest.AnswerKeywordInput keyword(String slotId, String label, String value) {
        AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
        item.setSlotId(slotId);
        item.setLabel(label);
        item.setKeyword(value);
        return item;
    }

    private AiEpisodeDraftResponse.EpisodeDraft playableDraft(AiEpisodeDraftRequest source) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < source.getPlaces().size(); i++) {
            boolean start = i == 0;
            boolean finalPlace = i == 9;
            missions.add(AiEpisodeDraftResponse.MissionDraft.builder()
                    .order(i + 1)
                    .placeName(source.getPlaces().get(i).getName())
                    .markerType(start ? "START" : finalPlace ? "FINAL" : "ANSWER_HINT")
                    .publicMarkerType(start ? "START" : "ANSWER_HINT")
                    .clueRole(start ? "START" : finalPlace ? "FINAL_PLACE" : "ANSWER_HINT")
                    .finalPlace(finalPlace)
                    .storyText(start ? "사건 파일을 확인하는 시작 장소입니다." : finalPlace ? "조사 완료 후 자동 공개되는 최종 정답 입력 장소입니다." : "현장 기록과 사건 자료를 비교하는 조사 장소입니다.")
                    .puzzleType("STORY_COMBINATION")
                    .questionText("현장 기록과 사건 파일을 비교해 답을 입력하세요.")
                    .answer("현장단서" + (i + 1))
                    .answerFormat("TEXT")
                    .rewardClue(start || finalPlace ? "사건 진행 정보" : clue(i))
                    .rewardClueSlotId(start || finalPlace ? "" : "ANSWER_CLUE")
                    .targetKeywordType(start || finalPlace ? "" : target(i))
                    .targetKeywordDisplayType(start || finalPlace ? "" : targetLabel(target(i)))
                    .supportsKeywordSlots(start || finalPlace ? List.of() : List.of(target(i)))
                    .hints(List.of("시간 기록을 비교하세요.", "접근 권한을 확인하세요.", "물질 흔적을 대조하세요."))
                    .build());
        }

        return AiEpisodeDraftResponse.EpisodeDraft.builder()
                .episodeTitle("테스트 범죄 미스터리")
                .subtitle("허구 사건 초안")
                .genre("범죄 미스터리")
                .selectedGenre("범죄 미스터리")
                .fictionSynopsis("실제 장소를 배경 모티브로만 사용하는 허구 사건입니다.")
                .missionDescription("8개 조사 단서로 네 개 정답 슬롯을 판단합니다.")
                .finalQuestion("범인, 흉기, 동기, 방법을 입력하세요.")
                .finalTruthSummary("사건과 인물은 모두 허구입니다.")
                .actualHistorySummary("장소 정보는 배경 모티브로만 사용했습니다.")
                .missions(missions)
                .suspects(List.of(
                        suspect("강수진", "회의 시간이 비어 있습니다.", "기록 접근 권한이 있습니다."),
                        suspect("박도윤", "통화 기록이 불완전합니다.", "약병 보관함에 접근했습니다."),
                        suspect("이재민", "동선 설명이 엇갈립니다.", "CCTV 공백 시간대가 있습니다.")
                ))
                .build();
    }

    private String clue(int index) {
        return List.of(
                "피해자 일정표에 없던 인물이 회의실에 접근했다.",
                "출입 기록의 추가 지문은 자료실 접근 권한자에게서 나왔다.",
                "약병의 봉인 상태가 사건 직전 한 차례 바뀌었다.",
                "독성 물질은 캡슐 내부에서만 검출되었다.",
                "비밀 계약 종료 문서가 사건 당일 삭제되었다.",
                "피해자는 계약 은폐를 공개하려 했다.",
                "약병 보관함 열쇠가 정상 위치에서 사라졌다.",
                "회의실 컵과 약병의 교체 시간이 같은 동선에 묶인다."
        ).get(index - 1);
    }

    private String target(int index) {
        return switch (index) {
            case 1, 2 -> "CULPRIT";
            case 3, 4 -> "WEAPON";
            case 5, 6 -> "MOTIVE";
            default -> "METHOD";
        };
    }

    private String targetLabel(String target) {
        return switch (target) {
            case "CULPRIT" -> "범인";
            case "WEAPON" -> "흉기";
            case "MOTIVE" -> "동기";
            case "METHOD" -> "방법";
            default -> "";
        };
    }

    private AiEpisodeDraftResponse.SuspectDraft suspect(String name, String alibi, String suspicion) {
        return AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(name)
                .alibiSummary(alibi)
                .suspiciousPoint(suspicion)
                .build();
    }
}
