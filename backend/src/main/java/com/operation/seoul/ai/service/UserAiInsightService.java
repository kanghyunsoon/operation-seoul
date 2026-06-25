package com.operation.seoul.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.service.GeminiContentClient;
import com.operation.seoul.coaching.dto.CoachingReportResponse;
import com.operation.seoul.recommendation.dto.EpisodeRecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserAiInsightService {
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*(\\d+)\\s*[.)]\\s*(.+?)\\s*$");
    private final GeminiContentClient geminiContentClient;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    public UserAiInsightService(ObjectMapper objectMapper) {
        this.geminiContentClient = new GeminiContentClient(objectMapper);
    }

    public List<String> recommendationReasons(List<EpisodeRecommendationResponse> recommendations, int clearedCount) {
        if (recommendations == null || recommendations.isEmpty() || disabled()) {
            return List.of();
        }
        StringBuilder prompt = new StringBuilder("""
                너는 Operation KOREA의 미션 추천 코치다.
                아래 추천 후보마다 사용자의 다음 플레이 결정을 돕는 한국어 추천 이유를 1문장으로 작성하라.
                과장하지 말고, 입력에 없는 사실을 만들지 말고, 정답/스포일러/범인/흉기/동기를 언급하지 마라.
                반드시 번호 목록 형식만 출력하라. 예: 1. 추천 이유

                사용자 클리어 수: %d
                추천 후보:
                """.formatted(clearedCount));
        for (int i = 0; i < recommendations.size(); i++) {
            EpisodeRecommendationResponse item = recommendations.get(i);
            prompt.append(i + 1).append(". ")
                    .append("title=").append(safe(item.getTitle()))
                    .append(", subtitle=").append(safe(item.getSubtitle()))
                    .append(", era=").append(safe(item.getEra()))
                    .append(", genre=").append(safe(item.getGenre()))
                    .append(", difficulty=").append(safe(item.getDifficulty()))
                    .append(", favorited=").append(Boolean.TRUE.equals(item.getFavorited()))
                    .append(", cleared=").append(Boolean.TRUE.equals(item.getCleared()))
                    .append(", ruleReason=").append(safe(item.getReason()))
                    .append('\n');
        }
        return parseNumberedLines(callGemini(prompt.toString()).orElse(""), recommendations.size());
    }

    public List<String> coachingAdvice(
            int totalStarted,
            int totalCleared,
            int averageScore,
            int totalHints,
            int totalWrong,
            int totalQuestions,
            String playStyle,
            List<String> fallbackAdvice) {
        if (disabled()) {
            return List.of();
        }
        String prompt = """
                너는 Operation KOREA의 플레이 코치다.
                아래 플레이 통계를 바탕으로 사용자가 다음 미션에서 바로 적용할 수 있는 한국어 코칭을 3개 작성하라.
                각 문장은 70자 이내로 쓰고, 비난하지 말고 구체적인 행동으로 제안하라.
                입력에 없는 사건 내용, 정답, 범인, 흉기, 동기를 만들지 마라.
                반드시 번호 목록 형식만 출력하라.

                시작한 미션 수: %d
                클리어 수: %d
                평균 점수: %d
                힌트 사용 수: %d
                오답 수: %d
                최종 추리 질문 수: %d
                플레이 스타일: %s
                기존 규칙 조언: %s
                """.formatted(
                totalStarted,
                totalCleared,
                averageScore,
                totalHints,
                totalWrong,
                totalQuestions,
                safe(playStyle),
                safe(String.join(" / ", fallbackAdvice == null ? List.of() : fallbackAdvice)));
        return parseNumberedLines(callGemini(prompt), 3);
    }

    public Optional<String> episodeCoachingSummary(CoachingReportResponse report) {
        if (report == null || disabled()) {
            return Optional.empty();
        }
        String prompt = """
                너는 Operation KOREA의 플레이 코치다.
                아래 에피소드 기록을 바탕으로 한국어 코칭 요약을 1문장으로 작성하라.
                90자 이내로 쓰고, 정답/스포일러/범인/흉기/동기를 언급하지 마라.
                입력에 없는 사실을 만들지 마라.

                에피소드: %s
                상태: %s
                등급: %s
                점수: %s
                방문 장소 수: %s
                완료 장소 수: %s
                힌트 사용 수: %s
                오답 수: %s
                추리 질문 수: %s
                최종 제출 수: %s
                기존 요약: %s
                """.formatted(
                safe(report.getEpisodeTitle()),
                safe(report.getStatus()),
                safe(report.getGrade()),
                report.getScore(),
                report.getVisitedSpotCount(),
                report.getCompletedSpotCount(),
                report.getHintUsedCount(),
                report.getWrongAnswerCount(),
                report.getDeductionQuestionCount(),
                report.getFinalGuessCount(),
                safe(report.getSummary()));
        return callGemini(prompt).map(this::singleLine).filter(value -> !value.isBlank());
    }

    private Optional<String> callGemini(String prompt) {
        try {
            String text = geminiContentClient.generateContent(prompt, geminiModel, geminiApiKey);
            return Optional.ofNullable(text);
        } catch (Exception e) {
            log.warn("user_ai_insight_fallback reason={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<String> parseNumberedLines(Optional<String> text, int expected) {
        return text.map(value -> parseNumberedLines(value, expected)).orElse(List.of());
    }

    private List<String> parseNumberedLines(String text, int expected) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = NUMBERED_LINE.matcher(line);
            if (matcher.matches()) {
                values.add(singleLine(matcher.group(2)));
            }
        }
        if (values.size() < expected) {
            return List.of();
        }
        return values.stream().limit(expected).toList();
    }

    private String singleLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean disabled() {
        return geminiApiKey == null || geminiApiKey.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
