package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.game.domain.ClearReport;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.ClearReportRepository;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.dto.MissionResponse;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final ClearReportRepository clearReportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * 최종 목적지와 후보 POI 목록을 받아 Gemini가 작전 JSON을 생성하게 합니다.
     * prompt 안에서 정답 키워드 노출 금지, 힌트/최종 미션 수, realStory 형식을 강하게 제한합니다.
     */
    public String generateCourseWithTarget(Map<String, Object> targetSpot, List<Map<String, Object>> subSpots) {
        String url = geminiUrl();
        String prompt = """
                당신은 Operation KOREA의 현장형 역사 추리 게임 작전 기획자입니다.

                [입력 데이터]
                - 최종 목적지: %s
                - 힌트 후보 POI: %s

                [생성 규칙]
                1. 최종 목적지와 직접 근거가 있는 실제 사건, 공식 사건명, 유명한 일화, 유래, 개통, 조성, 축제 탄생 중 하나를 정답으로 정하세요.
                   answerKeyword는 장소명, 인물명, 건물명이 아니라 최종 목적지에서 설명 가능한 사건/일화/역사적 행위/유래의 압축 키워드여야 합니다.
                   answerKeyword는 플레이어가 최종 채팅에서 직접 입력할 수 있는 2~8자 내외의 짧은 대표 표현이어야 합니다.
                   "지역 산업의 문화자원화"처럼 의미는 정확하지만 너무 학술적이거나 긴 표현은 answerKeyword로 쓰지 말고 realStory에서 해설하세요.
                   전국적으로 유명한 역사 사건을 아무 장소에나 붙이면 안 됩니다. 최종 목적지의 안내문, 공식 설명, 지명 유래, 교통/관광 콘텐츠의 탄생 배경으로 설명 가능한 경우에만 선택하세요.
                   최종 목적지가 현대 관광지/교통수단/축제/테마 공간이면, 무관한 근현대 정치 사건 대신 해당 장소의 조성, 운영 개시, 지역 산업 변화, 지명 유래, 설화, 축제 탄생과 직접 연결된 키워드를 정하세요.
                   현대 관광지/교통/축제/테마 콘텐츠가 최종 목적지라면 answerKeyword는 짧고 맞힐 수 있는 대표 표현으로 정하고, 의미가 같은 근접 표현으로도 자연스럽게 유추 가능해야 합니다.
                   answerKeyword는 되도록 "전망대 개장", "성곽 복원", "축제 탄생", "항구 개항"처럼 대상과 행위가 함께 보이는 구체 표현을 쓰세요.
                   "관광화", "자원화", "근대화"처럼 범위가 넓은 추상 명사만으로 끝나는 표현은 피하세요.
                   좋은 예는 "최종 목적지의 공식 해설로 바로 설명 가능한 사건/유래/개통/조성 키워드"입니다. 나쁜 예는 "장소와 직접 관련 없는 유명 사건", "장소명", "인물명", "건물명"입니다.
                   answerKeyword는 최종 채팅에서 맞혀야 하는 비밀 정답입니다. regionName, regionDescription, clue에는 절대 직접 쓰지 마세요.

                2. 힌트 미션 3개와 최종 미션 1개, 총 4개만 만드세요.
                   힌트 미션 title/lat/lng는 제공된 힌트 후보 POI의 공식 명칭과 좌표를 사용하세요.
                   최종 미션은 반드시 제공된 최종 목적지의 공식 명칭과 좌표를 사용하세요.

                3. 전체 작전은 방탈출 narrative scenario 형식으로 설계하세요.
                   먼저 세팅, 주인공, 목표, 플롯을 내부적으로 정리한 뒤 결과에는 자연스러운 스토리만 쓰세요.
                   예: 닫힌 교실, 잠긴 노트북, 스케줄러의 날짜, 프로젝터에 겹쳐지는 기호처럼 장소와 사물이 사건을 밀어내는 구조입니다.
                   Operation KOREA의 테마는 역사 추리, 현장 잠입, 봉인된 기록 복원입니다.
                   재미 요소는 Discovery/Exploration, Challenge, Thrill/Sensation을 섞고, 기능 설명이나 플레이 방법 설명은 쓰지 마세요.

                4. regionDescription은 브리핑 화면에 그대로 표시할 "스토리 시작 전 배경 서사"입니다.
                   regionName은 "작전명 [한국어 제목]" 형식으로만 쓰고, "Operation:", "오퍼레이션", 영어 부제는 붙이지 마세요.
                   모든 미션에 반복될 수 있는 고정 오프닝(예: "요원, 본부 암호 채널을 개방한다")은 쓰지 마세요.
                   "기록보관소", "서버", "본부가 승인한다", "작전 투입" 같은 범용 작전 설명으로 시작하지 마세요.
                   지역명, 마커, 좌표, TourAPI, 사진 촬영, AI 채팅, 이동 방법 같은 시스템 설명은 쓰지 마세요.
                   힌트 후보 POI의 이름, visionKeyword, "안내판/비석/조형물/전시관" 같은 현장 단서명을 나열하지 마세요.
                   정답 사건을 설명하지 말고, 그 사건이 벌어지기 전 실제 배경에서 어떤 필요, 갈등, 선택이 쌓였는지 보여주세요.
                   실종, 살인, 복수, 유령, 범죄, 사라진 역무원 같은 강한 장르 장치는 실제 배경과 직접 근거가 있을 때만 쓰세요.
                   근거 없는 비극을 만들지 말고, 누락된 기록/엇갈린 증언/남겨진 약속 정도의 낮은 긴장으로 플레이어가 왜 추적해야 하는지 보여주세요.
                   반드시 3문단으로 나누고 문단 사이에는 \\n\\n을 넣으세요.
                   1문단은 시대/사회/지역 배경과 그 장소가 중요해진 이유를 보여주세요.
                   2문단은 사건 이전에 사람들이 겪은 필요, 갈등, 변화의 압력을 보여주세요.
                   3문단은 남겨진 기록이나 풀리지 않은 질문 때문에 이야기가 현재 플레이어에게 이어지는 이유를 보여주세요.
                   방탈출 시나리오의 "전체 스토리"처럼 읽혀야 하지만, 과하게 시적이거나 장르 소설처럼 부풀리면 안 됩니다.
                   하라체는 마지막 한 문장 정도에만 쓰고, 대부분은 자연스러운 서사형 문장으로 이어 가세요.
                   "입니다", "했습니다", "하십시오" 같은 존댓말을 섞지 말고 문체를 단정형/하라체로 통일하세요.
                   "기억하라", "의심하라", "침묵", "사라진", "찢어진", "검은" 같은 단어를 반복하지 마세요.
                   세팅, 주인공, 목표, 플롯이 자연스럽게 느껴져야 합니다. 결과에는 항목명이나 번호를 쓰지 말고 한 편의 도입부처럼 쓰세요.
                   정답 단어와 최종 목적지명은 직접 쓰지 말고, 정답을 맞출 정도로 결정적인 연도/인물/문구도 피하세요.

                5. 각 미션의 description은 해당 장소에 붙는 짧은 narrative scenario 조각입니다.
                   "무엇을 하라"는 사용법이 아니라, 그 장소에 남은 장면/사물/소리/기록이 다음 의문으로 이어지는 식으로 2~3문장 작성하세요.
                   모든 문장을 하라체로 끝내지 말고, 본부가 현장 상황을 짧게 보고하는 첩보 지령 톤을 유지하세요.
                   번호 목록이나 단계 설명은 쓰지 마세요.

                6. 각 힌트 clue는 "현장에서 무엇을 보라"는 안내문이 아니라, 플레이어가 미션을 클리어한 뒤 받는 방탈출식 스토리 비트입니다.
                   clue에는 관찰하십시오, 살펴보십시오, 주목하십시오, 찾으십시오, 확인하십시오, 기억하십시오 같은 지시형 문장을 쓰지 마세요.
                   안내판, 비석, 간판, 벽면, 입구, 조형물처럼 현장 오브젝트를 그대로 지목하지 마세요.
                   각 clue는 2~4개의 짧은 행으로 쓰고, 행 사이에는 \\n을 넣으세요.
                   감정의 흐름은 "이상한 물건 발견 -> 감정 변화/긴장 상승 -> 다음 행동의 동기"가 되게 하세요.
                   힌트 3개는 서로 이어지는 단계여야 합니다. 첫 번째는 사건의 상처를 암시하고, 두 번째는 갈등이나 선택을 키우고, 세 번째는 마지막 결심 또는 잠금 해제 직전의 감정을 줍니다.
                   정답 단어, 최종 목적지명, 직접적인 연도/공식 명칭은 쓰지 마세요.
                   예: "봉투 안쪽에서 접힌 영수증과 지워진 이름표가 나왔다.\\n누군가는 같은 장소를 전혀 다른 목적으로 불렀고, 그 차이가 기록의 첫 금을 만들었다.\\n남은 조각은 결론보다 먼저 이유를 묻고 있다."

                7. final mission의 visionKeyword는 사진 인증용이 아니라 현장에서 찾아볼 만한 단서 대상입니다.
                   예: "현판", "비석", "문양", "동상", "기둥", "안내판", "문"
                   final mission의 clue에는 최종 사건을 열기 직전의 마지막 혼합형 힌트만 남기세요.
                   visionKeyword 대상의 물리적 흔적과 사건의 정황을 함께 숨기고, answerKeyword를 직접 쓰지 마세요.

                8. realStory는 최종 클리어 후 보여줄 역사 해설입니다.
                   사건의 실제 배경, 최종 장소와의 관련성, 플레이어가 모은 힌트의 의미를 8~12문장으로 쓰세요.

                9. JSON만 출력하세요. 마크다운 코드블록이나 추가 설명은 쓰지 마세요.

                {
                  "regionName": "작전명 비유적인 작전 이름",
                  "regionDescription": "[정답과 최종 장소명을 숨긴 방탈출식 스토리 시작 전 배경 서사]",
                  "missions": [
                    {
                      "title": "[힌트 후보 공식명]",
                      "lat": 37.0,
                      "lng": 127.0,
                      "visionKeyword": "[현장 관찰 키워드]",
                      "description": "[해당 장소의 방탈출식 스토리 조각]",
                      "clue": "[2~4행의 방탈출식 스토리 비트. 현장 관찰 지시 금지]",
                      "isFinal": false
                    },
                    {
                      "title": "[최종 목적지 공식명]",
                      "lat": 37.0,
                      "lng": 127.0,
                      "visionKeyword": "[최종 현장 단서 대상]",
                      "description": "[마지막 장면의 방탈출식 스토리 조각]",
                      "clue": "[마지막 봉인을 여는 혼합형 사건 힌트. 정답 직접 노출 금지]",
                      "answerKeyword": "[장소명이 아닌 사건/일화 키워드]",
                      "realStory": "[역사 해설]",
                      "isFinal": true
                    }
                  ]
                }
                """.formatted(targetSpot, subSpots);

        return callGeminiStandard(url, prompt);
    }

    /** 선택된 스팟에 맞춰 힌트 미션 1개만 다시 작성합니다. */
    public Map<String, String> generateHintMissionPatch(
            Mission mission,
            Region region,
            Map<String, String> selectedSpot,
            String finalMissionTitle) {
        String prompt = """
                당신은 Operation KOREA의 현장 추리 게임 미션 작가입니다.

                [기존 작전]
                - 작전명: %s
                - 작전 배경: %s
                - 최종 현장명: %s

                [수정할 힌트 미션]
                - 현재 제목: %s
                - 현재 설명: %s
                - 현재 단서: %s

                [새로 선택한 스팟]
                - 이름: %s
                - 주소/분류: %s
                - 좌표: %s, %s

                [작성 규칙]
                1. 새 스팟을 배경으로 힌트 미션 1개만 다시 쓴다.
                2. title은 출력하지 말고, 스팟 이름은 시스템이 별도로 적용한다.
                3. visionKeyword는 현장에서 사진 인증에 쓸 수 있는 짧은 관찰 키워드 1개로 쓴다.
                4. description은 해당 스팟 분위기를 활용한 방탈출식 장면 2~3문장으로 쓴다.
                5. clue는 플레이어가 힌트 미션 완료 후 받는 이야기 단서다. 지시문이나 사용법이 아니라 2~4줄의 서사 단서로 쓴다.
                6. 최종 현장명, 최종 정답, 정답을 직접 암시하는 결정적 표현은 쓰지 않는다.
                7. JSON만 출력한다.

                {
                  "visionKeyword": "현장 인증 키워드",
                  "description": "스팟 기반 미션 설명",
                  "clue": "힌트 완료 후 제공할 서사 단서"
                }
                """.formatted(
                region == null ? "" : nullSafe(region.getName()),
                region == null ? "" : summarizeForPrompt(region.getDescription(), 420),
                nullSafe(finalMissionTitle),
                nullSafe(mission.getTitle()),
                summarizeForPrompt(mission.getDescription(), 260),
                summarizeForPrompt(mission.getClue(), 260),
                selectedSpot.getOrDefault("title", ""),
                selectedSpot.getOrDefault("address", selectedSpot.getOrDefault("category", "")),
                selectedSpot.getOrDefault("mapY", ""),
                selectedSpot.getOrDefault("mapX", "")
        );

        String raw = callGeminiStandard(geminiUrl(), prompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            int startIndex = raw.indexOf('{');
            int endIndex = raw.lastIndexOf('}');
            if (startIndex == -1 || endIndex == -1) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw.substring(startIndex, endIndex + 1));
            Map<String, String> patch = new HashMap<>();
            patch.put("visionKeyword", root.path("visionKeyword").asText(""));
            patch.put("description", root.path("description").asText(""));
            patch.put("clue", root.path("clue").asText(""));
            return patch;
        } catch (Exception e) {
            log.warn("Hint mission patch JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 최종 채팅에서 사용자의 답변에 대한 짧은 판정 내레이션을 스트리밍합니다.
     * 정답이면 성공 안내, 오답이면 직접 정답을 공개하지 않는 재추론 안내를 생성합니다.
     */
    public ResponseBodyEmitter streamNarration(Long missionId, String userAnswer, boolean isCorrect) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String fieldClue = getFinalFieldClue(mission);
        String prompt = String.format("""
                당신은 Operation KOREA의 본부 오퍼레이터입니다.
                미션: %s
                현장 관찰 단서: "%s"
                대원 입력: "%s"
                정답 여부: %s

                [응답 규칙]
                - 한국어로 2문장 이하, 120자 이하.
                - 정답이면 성공을 짧게 알리고 자세한 해설은 종료 기록에서 제공된다고 안내하세요.
                - 오답이면 단정적인 정답 공개 없이, 수집한 단서와 현장 관찰 단서를 다시 연결해 보라고 안내하세요.
                - 마크다운 제목이나 목록은 쓰지 마세요.
                """, mission.getTitle(), fieldClue, userAnswer, isCorrect);

        return streamPrompt(geminiUrl(), prompt);
    }

    /**
     * 플레이어가 질문형 입력을 했을 때 정답을 직접 노출하지 않는 힌트 답변을 스트리밍합니다.
     */
    public ResponseBodyEmitter streamHintAnswer(Long missionId, Long userId, String userQuestion) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String clueContext = buildCollectedClueContext(getClearedClues(mission, userId));
        String fieldClue = getFinalFieldClue(mission);
        boolean genericHintRequest = isGenericHintRequest(userQuestion);
        String prompt = String.format("""
                당신은 역사 추리 게임의 최종 채팅 진행자입니다.
                최종 장소: "%s"
                현장 관찰 단서: "%s"
                실제 역사 기록 요약: "%s"
                플레이어가 수집한 단서: %s
                플레이어 질문: "%s"
                질문 유형: %s

                [응답 규칙]
                - 한국어로 1~2문장, 130자 이하.
                - 질문 유형이 "일반 힌트 요청"이면 관련성 판정을 하지 말고, 수집한 단서 중 한 장면을 암시적으로 다시 배열하세요.
                - 질문 유형이 "가설 검증 요청"이면 "관련 있음", "부분적으로 관련 있음", "거리가 있음" 중 하나로 시작해도 됩니다.
                - 정답 단어, 정답의 동의어, 정답을 정의하는 설명문을 쓰지 마세요.
                - "A가 B로 변했다", "무엇이 무엇으로 바뀌었다"처럼 결론 구조를 완성하지 마세요.
                - 단어 후보를 던지지 말고 관찰 순서, 대비되는 이미지, 빠진 연결고리만 말하세요.
                """,
                mission.getTitle(),
                fieldClue,
                summarizeForPrompt(mission.getRealStory(), 320),
                clueContext,
                userQuestion,
                genericHintRequest ? "일반 힌트 요청" : "가설 검증 요청"
        );

        return streamPrompt(geminiUrl(), prompt, answer -> sanitizeHintAnswer(answer, mission, genericHintRequest));
    }

    /** 사용자의 입력이 정답 제출인지 힌트 질문인지 구분하기 위한 휴리스틱입니다. */
    public boolean isHintQuestion(String userAnswer) {
        if (userAnswer == null) {
            return false;
        }
        String trimmed = userAnswer.trim();
        return trimmed.endsWith("?")
                || trimmed.endsWith("？")
                || trimmed.contains("관련")
                || trimmed.contains("맞아")
                || trimmed.contains("인가")
                || trimmed.contains("이야")
                || trimmed.contains("일까")
                || trimmed.contains("뭐")
                || trimmed.contains("무엇")
                || trimmed.contains("왜")
                || trimmed.contains("언제")
                || trimmed.contains("어디")
                || trimmed.contains("누구")
                || trimmed.contains("어떻게")
                || trimmed.contains("힌트");
    }

    /** "힌트 줘"처럼 일반 도움 요청인지, "이게 맞아?"처럼 가설 검증 요청인지 구분합니다. */
    private boolean isGenericHintRequest(String userQuestion) {
        String normalized = normalizeAnswer(userQuestion);
        if (normalized.isBlank()) {
            return false;
        }
        boolean asksForHint = normalized.contains("힌트")
                || normalized.contains("모르겠")
                || normalized.contains("감이안")
                || normalized.contains("도와")
                || normalized.contains("막혔");
        boolean asksRelation = normalized.contains("관련")
                || normalized.contains("맞아")
                || normalized.contains("인가")
                || normalized.contains("일까")
                || normalized.contains("이거")
                || normalized.contains("이게");
        return asksForHint && !asksRelation;
    }

    /** Gemini 힌트가 실수로 정답 키워드나 판정문을 노출하면 안전한 fallback 문장으로 대체합니다. */
    private String sanitizeHintAnswer(String answer, Mission mission, boolean genericHintRequest) {
        if (answer == null || answer.isBlank()) {
            return buildSafeHintFallback();
        }

        String normalizedAnswer = normalizeAnswer(answer);
        String normalizedKeyword = normalizeAnswer(mission.getAnswerKeyword());
        boolean exposesKeyword = !normalizedKeyword.isBlank() && normalizedAnswer.contains(normalizedKeyword);
        boolean usesJudgementPhraseOnGenericHint = genericHintRequest && containsAny(
                normalizedAnswer,
                "정답과관련", "정답에관련", "매우관련", "질문하신내용", "관련있습니다", "관련있음", "정답은", "키워드는"
        );

        if (exposesKeyword || usesJudgementPhraseOnGenericHint) {
            return buildSafeHintFallback();
        }
        return answer.trim();
    }

    /** 힌트 생성 실패 또는 안전성 보정 시 사용할 고정 대체 문장입니다. */
    private String buildSafeHintFallback() {
        return "지금은 결론보다 배열이 중요하다. 가장 먼저 얻은 단서가 만든 균열과 마지막 현장 표식이 만나는 지점을 다시 좁혀라.";
    }

    /**
     * 클리어 화면에 필요한 역사 리포트와 단서별 해설, 점수 정보를 모읍니다.
     * AI 리포트 생성이 실패해도 기본 realStory와 fallback 단서 해설을 반환합니다.
     */
    public Map<String, Object> generateClearReport(Long missionId, Long userId) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        String realStory = mission.getRealStory();
        List<Map<String, String>> clearedClues = getClearedClues(mission, userId);
        GameSession finalSession = gameSessionRepository.findByUserIdAndMissionId(userId, missionId).orElse(null);
        boolean cleared = finalSession != null && "CLEARED".equals(finalSession.getStatus());

        if (!cleared) {
            return Map.of(
                    "missionId", mission.getId(),
                    "title", mission.getTitle(),
                    "cleared", false,
                    "message", "클리어하지 못한 사건입니다.",
                    "answerKeyword", "",
                    "report", "",
                    "clueExplanations", Map.of(),
                    "score", 0,
                    "elapsedSeconds", 0L,
                    "routeDistanceMeters", 0.0
            );
        }

        ClearReport savedReport = clearReportRepository.findByUserIdAndMissionId(userId, missionId);
        if (savedReport != null) {
            return buildClearReportResponse(
                    mission,
                    finalSession,
                    savedReport.getReport(),
                    parseClueExplanations(savedReport.getClueExplanationsJson())
            );
        }

        String report = realStory;
        Map<String, List<String>> clueExplanations = new HashMap<>();
        Map<String, Object> generatedReport = generatePlayerClearReport(mission, answerKeyword, realStory, clearedClues);
        if (generatedReport != null) {
            report = (String) generatedReport.getOrDefault("report", report);
            Object explanations = generatedReport.get("clueExplanations");
            if (explanations instanceof Map<?, ?> explanationMap) {
                for (Map.Entry<?, ?> entry : explanationMap.entrySet()) {
                    clueExplanations.put(String.valueOf(entry.getKey()), normalizeParagraphList(entry.getValue()));
                }
            }
        }

        if (report == null || report.isBlank()) {
            report = "작전이 완료되었습니다. 수집한 단서는 최종 장소와 연결된 실제 역사 사건을 추론하도록 설계되었습니다.";
        }
        if (clueExplanations.isEmpty()) {
            clueExplanations = buildFallbackClueExplanations(clearedClues, answerKeyword, mission.getTitle());
        }

        ClearReport newReport = new ClearReport();
        newReport.setUserId(userId);
        newReport.setMissionId(missionId);
        newReport.setReport(report);
        newReport.setClueExplanationsJson(writeClueExplanations(clueExplanations));
        clearReportRepository.upsert(newReport);

        return buildClearReportResponse(mission, finalSession, report, clueExplanations);
    }

    private Map<String, Object> buildClearReportResponse(
            Mission mission,
            GameSession finalSession,
            String report,
            Map<String, List<String>> clueExplanations) {
        return Map.of(
                "missionId", mission.getId(),
                "title", mission.getTitle(),
                "cleared", true,
                "answerKeyword", mission.getAnswerKeyword() == null ? "" : mission.getAnswerKeyword(),
                "report", report,
                "clueExplanations", clueExplanations,
                "score", finalSession != null && finalSession.getScore() != null ? finalSession.getScore() : 0,
                "elapsedSeconds", finalSession != null && finalSession.getElapsedSeconds() != null ? finalSession.getElapsedSeconds() : 0L,
                "routeDistanceMeters", finalSession != null && finalSession.getRouteDistanceMeters() != null ? finalSession.getRouteDistanceMeters() : 0.0
        );
    }

    private Map<String, List<String>> parseClueExplanations(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, List<String>> parsed = new HashMap<>();
            root.properties().forEach(entry -> parsed.put(entry.getKey(), normalizeParagraphList(entry.getValue())));
            return parsed;
        } catch (Exception e) {
            log.warn("Saved clear report clue JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String writeClueExplanations(Map<String, List<String>> clueExplanations) {
        try {
            return objectMapper.writeValueAsString(clueExplanations == null ? Map.of() : clueExplanations);
        } catch (Exception e) {
            log.warn("Clear report clue JSON write failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 사용자의 최종 답변을 정답 키워드와 비교합니다.
     * 1차로 정규화 문자열 비교를 하고, 애매한 경우 Gemini에게 TRUE/FALSE 판정을 위임합니다.
     */
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if (answerKeyword == null || answerKeyword.isBlank()) {
            return true;
        }

        String normalizedAnswer = normalizeAnswer(userAnswer);
        String normalizedKeyword = normalizeAnswer(answerKeyword);
        if (normalizedAnswer.equals(normalizedKeyword) || normalizedAnswer.contains(normalizedKeyword)) {
            return true;
        }
        if (isSameShortKoreanCompoundIgnoringOrder(normalizedKeyword, normalizedAnswer)) {
            return true;
        }

        String prompt = String.format("""
                정답 키워드: "%s"
                대원 답변: "%s"
                실제 역사 기록 요약: "%s"
                판정 기준: 답변이 정답 키워드 자체이거나 같은 역사 사건/일화/전환 개념의 공식 명칭을 명확히 말한 경우만 TRUE입니다.
                띄어쓰기, 조사, 약간의 어순 차이, 어색하지만 같은 뜻을 가리키는 짧은 조어는 의미가 같으면 TRUE입니다.
                정답의 핵심 명사와 답변의 핵심 명사가 같은 문맥에서 상위어/하위어/인접 개념이고, 나머지 행위나 변화 방향이 같으면 TRUE입니다.
                단, 정답을 구성하는 일부 단어만 말했거나 넓은 분야/장소/인물/시대 배경만 말한 경우는 FALSE입니다.
                답변이 애매하면 TRUE로 확장하지 말고 FALSE입니다.
                TRUE 또는 FALSE만 출력하세요.
                """, answerKeyword, userAnswer, summarizeForPrompt(mission.getRealStory(), 360));

        String result = callGeminiStandard(geminiUrl(), prompt);
        return result != null && "TRUE".equalsIgnoreCase(result.trim());
    }

    /** 짧은 한국어 복합어에서 어순만 살짝 바뀐 답변을 허용하기 위한 보조 비교입니다. */
    private boolean isSameShortKoreanCompoundIgnoringOrder(String normalizedKeyword, String normalizedAnswer) {
        if (normalizedKeyword == null || normalizedAnswer == null) {
            return false;
        }
        if (normalizedKeyword.length() < 4 || normalizedKeyword.length() > 10) {
            return false;
        }
        if (normalizedKeyword.length() != normalizedAnswer.length()) {
            return false;
        }
        if (!normalizedKeyword.matches("[가-힣]+") || !normalizedAnswer.matches("[가-힣]+")) {
            return false;
        }

        String keywordSuffix = extractComparableSuffix(normalizedKeyword);
        String answerSuffix = extractComparableSuffix(normalizedAnswer);
        if (keywordSuffix.isBlank() || !keywordSuffix.equals(answerSuffix)) {
            return false;
        }

        String keywordBody = normalizedKeyword.substring(0, normalizedKeyword.length() - keywordSuffix.length());
        String answerBody = normalizedAnswer.substring(0, normalizedAnswer.length() - answerSuffix.length());
        if (keywordBody.length() < 2 || answerBody.length() < 2) {
            return false;
        }
        return sortCharacters(keywordBody).equals(sortCharacters(answerBody));
    }

    /**
     * 비교 가능한 사건/행위 접미사를 추출합니다.
     * "-화"는 표현 폭이 넓어 정답을 과하게 열 수 있으므로 로컬 어순 보정 대상에서 제외합니다.
     */
    private String extractComparableSuffix(String value) {
        for (String suffix : List.of("도입", "개통", "개장", "조성", "건립", "설립", "창건", "복원", "재건", "이전", "철거", "폐지", "개항", "개방", "운동", "봉기", "의거", "전투", "선언", "협정", "조약", "사건", "축제", "탄생", "유래")) {
            if (value.endsWith(suffix)) {
                return suffix;
            }
        }
        return "";
    }

    /** 문자열의 code point를 정렬해 어순 차이를 비교할 때 사용합니다. */
    private String sortCharacters(String value) {
        return value.chars()
                .sorted()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /** 문자열에 여러 후보 키워드 중 하나라도 포함되는지 확인합니다. */
    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 최종 미션과 같은 region 안에서 이미 클리어한 힌트 단서를 모읍니다. */
    private List<Map<String, String>> getClearedClues(Mission finalMission, Long userId) {
        List<Map<String, String>> clues = new ArrayList<>();
        if (finalMission.getRegionId() == null) {
            return clues;
        }

        for (Mission mission : missionRepository.findByRegionId(finalMission.getRegionId())) {
            if (mission.isFinal()) {
                continue;
            }
            boolean cleared = gameSessionRepository.findByUserIdAndMissionId(userId, mission.getId())
                    .map(session -> "CLEARED".equals(session.getStatus()))
                    .orElse(false);
            if (!cleared) {
                continue;
            }
            clues.add(Map.of(
                    "id", String.valueOf(mission.getId()),
                    "title", mission.getTitle() == null ? "" : mission.getTitle(),
                    "clue", getMissionClueText(mission)
            ));
        }
        return clues;
    }

    /** Mission.clue를 우선 사용하고, 없으면 answerKeyword를 보조 단서로 사용합니다. */
    private String getMissionClueText(Mission mission) {
        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return MissionResponse.sanitizeStoryClue(mission, mission.getClue());
        }
        return mission.getAnswerKeyword() == null ? "" : mission.getAnswerKeyword();
    }

    /** Gemini prompt에 넣을 수 있게 수집 단서를 짧은 문자열로 합칩니다. */
    private String buildCollectedClueContext(List<Map<String, String>> clearedClues) {
        if (clearedClues == null || clearedClues.isEmpty()) {
            return "아직 수집한 단서 없음";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, String> clue : clearedClues) {
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(clue.getOrDefault("title", "단서"))
                    .append(": ")
                    .append(summarizeForPrompt(clue.getOrDefault("clue", ""), 120));
        }
        return builder.toString();
    }

    /** 최종 현장에서 관찰할 단서를 구성합니다. */
    private String getFinalFieldClue(Mission mission) {
        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return mission.getClue();
        }
        if (mission.getVisionKeyword() != null && !mission.getVisionKeyword().isBlank()) {
            return "'" + mission.getVisionKeyword() + "'에 남은 흠집과 연도가 마지막 봉인의 결을 비춘다. 닫힌 문 너머에서 오래된 결정이 아직 흔들린다.";
        }
        return "마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 사건의 방향이 한쪽으로 기울어 있다.";
    }

    /** 긴 역사 해설이 prompt를 과도하게 차지하지 않도록 공백 정리 후 길이를 제한합니다. */
    private String summarizeForPrompt(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 클리어 후 보여줄 플레이어 맞춤 리포트를 Gemini JSON으로 생성합니다. */
    private Map<String, Object> generatePlayerClearReport(
            Mission mission,
            String answerKeyword,
            String realStory,
            List<Map<String, String>> clearedClues) {
        String prompt = String.format("""
                당신은 역사 추리 관광 게임의 클리어 리포트 작성자입니다.

                [최종 장소] %s
                [정답 사건] %s
                [실제 역사 기록] %s
                [플레이어가 수집한 힌트] %s

                JSON만 반환하세요.
                report는 실제 역사 사실과 게임 단서의 의미를 8~12문장으로 설명하세요.
                clueExplanations는 각 힌트 id를 key로 쓰고, 값은 2~3개 문단 배열로 작성하세요.

                {
                  "report": "문장 단위의 역사 해설",
                  "clueExplanations": {
                    "힌트id": ["문단1", "문단2"]
                  }
                }
                """, mission.getTitle(), answerKeyword, realStory == null ? "" : realStory, clearedClues);

        String raw = callGeminiStandard(geminiUrl(), prompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            int startIndex = raw.indexOf('{');
            int endIndex = raw.lastIndexOf('}');
            if (startIndex == -1 || endIndex == -1) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw.substring(startIndex, endIndex + 1));
            Map<String, Object> parsed = new HashMap<>();
            parsed.put("report", root.path("report").asText(""));

            Map<String, List<String>> explanations = new HashMap<>();
            JsonNode explanationNode = root.path("clueExplanations");
            explanationNode.properties().forEach(entry -> explanations.put(entry.getKey(), normalizeParagraphList(entry.getValue())));
            parsed.put("clueExplanations", explanations);
            return parsed;
        } catch (Exception e) {
            log.warn("Clear report JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** AI JSON 값이 배열/문자열 어느 쪽으로 와도 화면 표시용 문단 리스트로 정규화합니다. */
    private List<String> normalizeParagraphList(Object value) {
        List<String> paragraphs = new ArrayList<>();
        if (value instanceof JsonNode node && node.isArray()) {
            node.forEach(item -> {
                if (!item.asText("").isBlank()) {
                    paragraphs.add(item.asText());
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(item -> {
                if (item != null && !String.valueOf(item).isBlank()) {
                    paragraphs.add(String.valueOf(item));
                }
            });
        } else if (value != null && !String.valueOf(value).isBlank()) {
            paragraphs.add(String.valueOf(value));
        }
        return paragraphs;
    }

    /** AI 리포트 생성 실패 시 단서별 기본 해설을 구성합니다. */
    private Map<String, List<String>> buildFallbackClueExplanations(
            List<Map<String, String>> clues,
            String answerKeyword,
            String finalTitle) {
        Map<String, List<String>> explanations = new HashMap<>();
        for (Map<String, String> clue : clues) {
            String id = clue.getOrDefault("id", "");
            String title = clue.getOrDefault("title", "수집한 단서");
            String clueText = clue.getOrDefault("clue", "현장 단서");
            explanations.put(id, List.of(
                    title + "에서 얻은 단서는 \"" + clueText + "\"입니다.",
                    "이 단서는 " + finalTitle + "와 연결된 사건을 직접 말하지 않고, 플레이어가 정황을 조합하도록 만든 힌트입니다.",
                    "최종 정답은 " + answerKeyword + "이며, 각 힌트는 그 사건의 분위기와 역사적 배경을 우회적으로 가리킵니다."
            ));
        }
        return explanations;
    }

    /** 정답 비교용으로 공백과 기호를 제거합니다. */
    private String normalizeAnswer(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{P}\\p{S}]", "").toLowerCase();
    }

    /** sanitizer 없이 일반 스트리밍 응답을 생성합니다. */
    private ResponseBodyEmitter streamPrompt(String url, String prompt) {
        return streamPrompt(url, prompt, null);
    }

    /**
     * Gemini 응답을 한 번 받은 뒤 문자 단위로 ResponseBodyEmitter에 흘려보냅니다.
     * 실제 Gemini streaming API가 아니라 서버 측 타자기 스트림이므로 프론트 버퍼와 함께 UX를 맞춥니다.
     */
    private ResponseBodyEmitter streamPrompt(String url, String prompt, java.util.function.UnaryOperator<String> sanitizer) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(120000L);

        new Thread(() -> {
            try {
                String aiResponseText = callGeminiStandard(url, prompt);
                if (sanitizer != null) {
                    aiResponseText = sanitizer.apply(aiResponseText);
                }
                if (aiResponseText != null) {
                    for (char c : aiResponseText.toCharArray()) {
                        emitter.send(String.valueOf(c));
                        Thread.sleep(25);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /** Gemini generateContent를 호출하고 마크다운 코드블록 노이즈를 제거한 텍스트를 반환합니다. */
    private String callGeminiStandard(String url, String prompt) {
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            return text.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            log.error("Gemini request failed: {}", e.getMessage());
            return null;
        }
    }

    /** 현재 프로젝트에서 사용하는 Gemini 모델 endpoint를 구성합니다. */
    private String geminiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + geminiApiKey.trim();
    }
}
