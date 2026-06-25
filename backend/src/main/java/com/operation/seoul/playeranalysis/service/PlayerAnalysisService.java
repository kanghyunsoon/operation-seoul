package com.operation.seoul.playeranalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.service.GeminiContentClient;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.episode.domain.FinalDeductionQuestion;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.playeranalysis.domain.PlayerAnalysis;
import com.operation.seoul.playeranalysis.domain.PlayerAnalysisMbti;
import com.operation.seoul.playeranalysis.domain.ReasoningAnswer;
import com.operation.seoul.playeranalysis.dto.PlayMbtiDto;
import com.operation.seoul.playeranalysis.dto.PlayerAnalysisRequest;
import com.operation.seoul.playeranalysis.dto.PlayerAnalysisResponse;
import com.operation.seoul.playeranalysis.dto.ReasoningAnswerDto;
import com.operation.seoul.playeranalysis.repository.PlayerAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerAnalysisService {
    private static final Set<String> PLAYER_TYPES = Set.of("증거 수집가", "빠른 판단가", "완벽주의자", "스토리 몰입러", "힌트 전략가", "로컬 탐험가");
    private static final List<PlayMbtiDto> MBTI_TEMPLATE = List.of(
            PlayMbtiDto.builder().dimension("사고 방식").leftLabel("논리형").rightLabel("직관형").leftPercent(60).rightPercent(40).build(),
            PlayMbtiDto.builder().dimension("단서 활용").leftLabel("증거 중심").rightLabel("감각 중심").leftPercent(65).rightPercent(35).build(),
            PlayMbtiDto.builder().dimension("의사결정").leftLabel("신중형").rightLabel("결단형").leftPercent(55).rightPercent(45).build(),
            PlayMbtiDto.builder().dimension("플레이 스타일").leftLabel("탐험형").rightLabel("목표형").leftPercent(50).rightPercent(50).build()
    );

    private final PlayerAnalysisRepository playerAnalysisRepository;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    @Transactional
    public PlayerAnalysisResponse createAnalysis(PlayerAnalysisRequest request, User user) {
        if (request == null || request.getMissionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSION_REQUIRED", "missionId is required.");
        }
        Long userId = resolveUserId(request.getUserId(), user);
        return createAndStoreAnalysis(userId, request.getMissionId(), trimAnswers(request.getAnswers()), null);
    }

    @Transactional
    public void createAnalysisAfterFinalAnswer(Long userId, Long missionId, boolean correct) {
        try {
            createAndStoreAnalysis(userId, missionId, null, correct);
        } catch (Exception e) {
            log.warn("player_analysis_after_final_answer_failed userId={} missionId={} reason={}", userId, missionId, e.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public PlayerAnalysisResponse latestAnalysis(Long requestedUserId, User user) {
        Long userId = resolveUserId(requestedUserId, user);
        PlayerAnalysis analysis = playerAnalysisRepository.findLatestAnalysis(userId);
        if (analysis == null) {
            return null;
        }
        List<PlayMbtiDto> mbti = playerAnalysisRepository.findMbtiByAnalysisId(analysis.getId()).stream()
                .map(item -> PlayMbtiDto.builder()
                        .dimension(item.getDimension())
                        .leftLabel(item.getLeftLabel())
                        .rightLabel(item.getRightLabel())
                        .leftPercent(safePercent(item.getLeftPercent()))
                        .rightPercent(100 - safePercent(item.getLeftPercent()))
                        .build())
                .toList();
        return PlayerAnalysisResponse.builder()
                .playerType(analysis.getPlayerType())
                .summary(analysis.getSummary())
                .strength(analysis.getStrength())
                .weakness(analysis.getWeakness())
                .recommendation(analysis.getRecommendation())
                .playMbti(mbti.isEmpty() ? fallback().getPlayMbti() : mbti)
                .build();
    }

    private PlayerAnalysisResponse createAndStoreAnalysis(Long userId, Long missionId, List<ReasoningAnswerDto> requestAnswers, Boolean finalCorrect) {
        UserEpisodeProgress progress = playerAnalysisRepository.findProgress(userId, missionId);
        if (progress == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAY_LOG_NOT_FOUND", "분석할 플레이 기록이 없습니다.");
        }

        List<ReasoningAnswerDto> answers = requestAnswers == null || requestAnswers.isEmpty()
                ? latestAnswers(userId, missionId)
                : requestAnswers;
        answers.forEach(answer -> saveReasoningAnswer(userId, missionId, answer));

        PlayerAnalysisResponse response = analyzeWithFallback(answers, progress, finalCorrect);
        PlayerAnalysis analysis = new PlayerAnalysis();
        analysis.setUserId(userId);
        analysis.setMissionId(missionId);
        analysis.setPlayerType(response.getPlayerType());
        analysis.setSummary(response.getSummary());
        analysis.setStrength(response.getStrength());
        analysis.setWeakness(response.getWeakness());
        analysis.setRecommendation(response.getRecommendation());
        playerAnalysisRepository.insertAnalysis(analysis);
        for (PlayMbtiDto item : response.getPlayMbti()) {
            PlayerAnalysisMbti mbti = new PlayerAnalysisMbti();
            mbti.setAnalysisId(analysis.getId());
            mbti.setDimension(item.getDimension());
            mbti.setLeftLabel(item.getLeftLabel());
            mbti.setRightLabel(item.getRightLabel());
            mbti.setLeftPercent(item.getLeftPercent());
            mbti.setRightPercent(item.getRightPercent());
            playerAnalysisRepository.insertMbti(mbti);
        }
        return response;
    }

    private PlayerAnalysisResponse analyzeWithFallback(List<ReasoningAnswerDto> answers, UserEpisodeProgress progress, Boolean finalCorrect) {
        if (answers == null || answers.isEmpty() || geminiApiKey == null || geminiApiKey.isBlank()) {
            return fallback();
        }
        try {
            String prompt = buildPrompt(answers, progress, finalCorrect);
            String raw = new GeminiContentClient(objectMapper).generateContent(prompt, geminiModel, geminiApiKey);
            return normalize(parseJson(raw));
        } catch (Exception e) {
            log.warn("player_analysis_ai_failed reason={}", e.getClass().getSimpleName());
            return fallback();
        }
    }

    private PlayerAnalysisResponse parseJson(String raw) throws Exception {
        String json = extractJson(raw);
        JsonNode root = objectMapper.readTree(json);
        List<PlayMbtiDto> playMbti = new ArrayList<>();
        for (JsonNode item : root.path("playMbti")) {
            playMbti.add(PlayMbtiDto.builder()
                    .dimension(item.path("dimension").asText())
                    .leftLabel(item.path("leftLabel").asText())
                    .rightLabel(item.path("rightLabel").asText())
                    .leftPercent(item.path("leftPercent").asInt())
                    .rightPercent(item.path("rightPercent").asInt())
                    .build());
        }
        return PlayerAnalysisResponse.builder()
                .playerType(root.path("playerType").asText())
                .summary(root.path("summary").asText())
                .strength(root.path("strength").asText())
                .weakness(root.path("weakness").asText())
                .recommendation(root.path("recommendation").asText())
                .playMbti(playMbti)
                .build();
    }

    private PlayerAnalysisResponse normalize(PlayerAnalysisResponse response) {
        PlayerAnalysisResponse fallback = fallback();
        String playerType = PLAYER_TYPES.contains(response.getPlayerType()) ? response.getPlayerType() : fallback.getPlayerType();
        Map<String, PlayMbtiDto> byDimension = new LinkedHashMap<>();
        if (response.getPlayMbti() != null) {
            for (PlayMbtiDto item : response.getPlayMbti()) {
                byDimension.put(item.getDimension(), item);
            }
        }
        List<PlayMbtiDto> normalizedMbti = MBTI_TEMPLATE.stream()
                .map(template -> normalizeMbti(byDimension.get(template.getDimension()), template))
                .toList();
        return PlayerAnalysisResponse.builder()
                .playerType(playerType)
                .summary(limitText(response.getSummary(), fallback.getSummary()))
                .strength(limitText(response.getStrength(), fallback.getStrength()))
                .weakness(limitText(response.getWeakness(), fallback.getWeakness()))
                .recommendation(limitText(response.getRecommendation(), fallback.getRecommendation()))
                .playMbti(normalizedMbti)
                .build();
    }

    private PlayMbtiDto normalizeMbti(PlayMbtiDto item, PlayMbtiDto template) {
        int left = safePercent(item == null ? template.getLeftPercent() : item.getLeftPercent());
        return PlayMbtiDto.builder()
                .dimension(template.getDimension())
                .leftLabel(template.getLeftLabel())
                .rightLabel(template.getRightLabel())
                .leftPercent(left)
                .rightPercent(100 - left)
                .build();
    }

    private String buildPrompt(List<ReasoningAnswerDto> answers, UserEpisodeProgress progress, Boolean finalCorrect) {
        return """
                당신은 관광형 방탈출 서비스의 AI 플레이 분석가입니다.

                아래 사용자의 추리 질문 답변과 플레이 로그를 바탕으로 플레이어 성향을 분석하세요.

                주의사항:
                - 정답 내용 자체를 다시 노출하지 마세요.
                - 사용자를 비난하지 마세요.
                - 긍정적인 피드백 중심으로 작성하세요.
                - 반드시 JSON만 반환하세요.
                - playerType은 아래 후보 중 하나만 선택하세요:
                  ["증거 수집가", "빠른 판단가", "완벽주의자", "스토리 몰입러", "힌트 전략가", "로컬 탐험가"]

                플레이 MBTI는 반드시 아래 4개 축을 모두 포함하세요.

                1. 사고 방식:
                - leftLabel: "논리형"
                - rightLabel: "직관형"

                2. 단서 활용:
                - leftLabel: "증거 중심"
                - rightLabel: "감각 중심"

                3. 의사결정:
                - leftLabel: "신중형"
                - rightLabel: "결단형"

                4. 플레이 스타일:
                - leftLabel: "탐험형"
                - rightLabel: "목표형"

                각 축의 leftPercent와 rightPercent 합은 반드시 100이어야 합니다.
                퍼센트는 0~100 사이 정수만 사용하세요.
                summary, strength, weakness, recommendation은 각각 80자 이내로 작성하세요.

                출력 형식:

                {
                  "playerType": "증거 수집가",
                  "summary": "80자 이내",
                  "strength": "80자 이내",
                  "weakness": "80자 이내",
                  "recommendation": "80자 이내",
                  "playMbti": [
                    {"dimension": "사고 방식", "leftLabel": "논리형", "rightLabel": "직관형", "leftPercent": 0, "rightPercent": 100},
                    {"dimension": "단서 활용", "leftLabel": "증거 중심", "rightLabel": "감각 중심", "leftPercent": 0, "rightPercent": 100},
                    {"dimension": "의사결정", "leftLabel": "신중형", "rightLabel": "결단형", "leftPercent": 0, "rightPercent": 100},
                    {"dimension": "플레이 스타일", "leftLabel": "탐험형", "rightLabel": "목표형", "leftPercent": 0, "rightPercent": 100}
                  ]
                }

                사용자 추리 답변:
                %s

                플레이 로그:
                %s
                """.formatted(reasoningAnswersText(answers), playLogText(progress, finalCorrect));
    }

    private String reasoningAnswersText(List<ReasoningAnswerDto> answers) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (ReasoningAnswerDto answer : answers.stream().limit(20).toList()) {
            builder.append(index++).append(". Q: ").append(safe(answer.getQuestion())).append('\n')
                    .append("   A: ").append(safe(answer.getAnswer())).append('\n');
        }
        return builder.toString();
    }

    private String playLogText(UserEpisodeProgress progress, Boolean finalCorrect) {
        long totalSeconds = 0;
        if (progress.getActiveElapsedSeconds() != null && progress.getActiveElapsedSeconds() > 0) {
            totalSeconds = (long) progress.getActiveElapsedSeconds() + value(progress.getClearTimePenaltySeconds());
        } else if (progress.getStartedAt() != null && progress.getClearedAt() != null) {
            totalSeconds = Duration.between(progress.getStartedAt(), progress.getClearedAt()).getSeconds() + value(progress.getClearTimePenaltySeconds());
        }
        return """
                최종 정답 정오 여부: %s
                오답 횟수: %d
                플레이 시작 시간: %s
                플레이 종료 시간: %s
                총 플레이 시간(초): %d
                최종 점수: %s
                추리 질문 수: %d
                가설 검증 수: %d
                최종 제출 수: %d
                """.formatted(
                finalCorrect == null ? "기록 기반 판단" : (finalCorrect ? "정답" : "오답"),
                value(progress.getWrongAnswerCount()),
                progress.getStartedAt(),
                progress.getClearedAt(),
                Math.max(0, totalSeconds),
                progress.getScore() == null ? "미산정" : progress.getScore(),
                value(progress.getDeductionQuestionCount()),
                value(progress.getHypothesisCount()),
                value(progress.getFinalGuessCount())
        );
    }

    private List<ReasoningAnswerDto> latestAnswers(Long userId, Long missionId) {
        List<FinalDeductionQuestion> questions = new ArrayList<>(playerAnalysisRepository.findLatestDeductionQuestions(userId, missionId));
        Collections.reverse(questions);
        return questions.stream()
                .map(item -> {
                    ReasoningAnswerDto dto = new ReasoningAnswerDto();
                    dto.setQuestion(item.getUserQuestion());
                    dto.setAnswer(item.getAiAnswerText());
                    return dto;
                })
                .toList();
    }

    private List<ReasoningAnswerDto> trimAnswers(List<ReasoningAnswerDto> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.stream()
                .filter(item -> item != null && item.getQuestion() != null && !item.getQuestion().isBlank()
                        && item.getAnswer() != null && !item.getAnswer().isBlank())
                .limit(20)
                .toList();
    }

    private void saveReasoningAnswer(Long userId, Long missionId, ReasoningAnswerDto dto) {
        ReasoningAnswer answer = new ReasoningAnswer();
        answer.setUserId(userId);
        answer.setMissionId(missionId);
        answer.setQuestion(safe(dto.getQuestion()));
        answer.setAnswer(safe(dto.getAnswer()));
        playerAnalysisRepository.insertReasoningAnswer(answer);
    }

    private Long resolveUserId(Long requestedUserId, User user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication is required.");
        }
        if (requestedUserId != null && !requestedUserId.equals(user.getId()) && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_MISMATCH", "다른 사용자의 분석 결과는 조회할 수 없습니다.");
        }
        return user.getId();
    }

    private PlayerAnalysisResponse fallback() {
        return PlayerAnalysisResponse.builder()
                .playerType("스토리 몰입러")
                .summary("전반적으로 안정적인 방식으로 미션을 진행한 플레이어입니다.")
                .strength("스토리와 단서를 함께 살피며 균형 있게 추리했습니다.")
                .weakness("더 많은 단서를 비교하면 추리의 정확도를 높일 수 있습니다.")
                .recommendation("다음에는 단서 조합이 중요한 미스터리 미션을 추천합니다.")
                .playMbti(MBTI_TEMPLATE)
                .build();
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private String limitText(String value, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return text.length() <= 80 ? text : text.substring(0, 80);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int safePercent(Integer value) {
        return Math.max(0, Math.min(100, value == null ? 0 : value));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
