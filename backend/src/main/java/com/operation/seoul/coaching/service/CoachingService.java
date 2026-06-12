package com.operation.seoul.coaching.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.coaching.dto.CoachingReportResponse;
import com.operation.seoul.coaching.dto.CoachingSummaryResponse;
import com.operation.seoul.coaching.repository.CoachingRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoachingService {
    private final CoachingRepository coachingRepository;

    public CoachingSummaryResponse getSummary(User user) {
        List<CoachingReportResponse> reports = coachingRepository.findReports(user.getId()).stream()
                .map(this::enrich)
                .limit(5)
                .toList();
        int totalStarted = coachingRepository.countStarted(user.getId());
        int totalCleared = coachingRepository.countCleared(user.getId());
        int totalHints = coachingRepository.sumHints(user.getId());
        int totalWrong = coachingRepository.sumWrongAnswers(user.getId());
        int totalQuestions = coachingRepository.sumDeductionQuestions(user.getId());
        int averageScore = coachingRepository.averageScore(user.getId());
        return CoachingSummaryResponse.builder()
                .totalStarted(totalStarted)
                .totalCleared(totalCleared)
                .averageScore(averageScore)
                .totalHints(totalHints)
                .totalWrongAnswers(totalWrong)
                .totalDeductionQuestions(totalQuestions)
                .playStyle(playStyle(totalCleared, totalHints, totalWrong, totalQuestions))
                .globalAdvice(globalAdvice(totalStarted, totalCleared, totalHints, totalWrong, totalQuestions))
                .recentReports(reports)
                .build();
    }

    public CoachingReportResponse getEpisodeReport(User user, Long episodeId) {
        CoachingReportResponse report = coachingRepository.findReport(user.getId(), episodeId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COACHING_REPORT_NOT_FOUND", "Coaching report not found.");
        }
        return enrich(report);
    }

    private CoachingReportResponse enrich(CoachingReportResponse report) {
        report.setGrade(grade(report.getScore(), report.getStatus()));
        report.setSummary(summary(report));
        report.setStrengths(strengths(report));
        report.setImprovements(improvements(report));
        report.setNextActions(nextActions(report));
        return report;
    }

    private String grade(Integer score, String status) {
        if (!"CLEARED".equals(status)) return "진행 중";
        int value = score == null ? 0 : score;
        if (value >= 1200) return "S";
        if (value >= 1000) return "A";
        if (value >= 800) return "B";
        return "C";
    }

    private String summary(CoachingReportResponse report) {
        if (!"CLEARED".equals(report.getStatus())) {
            return "아직 클리어 전입니다. 방문한 장소와 수집한 단서를 기준으로 다음 행동을 정리하세요.";
        }
        return "%s 클리어 기록은 %s 등급입니다. 오답 %d회, 추리 질문 %d회, 최종 제출 %d회를 기준으로 분석했습니다."
                .formatted(report.getEpisodeTitle(), report.getGrade(), value(report.getWrongAnswerCount()), value(report.getDeductionQuestionCount()), value(report.getFinalGuessCount()));
    }

    private List<String> strengths(CoachingReportResponse report) {
        List<String> values = new ArrayList<>();
        if ("CLEARED".equals(report.getStatus())) values.add("사건을 끝까지 완주했습니다.");
        if (value(report.getWrongAnswerCount()) <= 1) values.add("정답 제출 전에 근거를 잘 검토했습니다.");
        if (value(report.getDeductionQuestionCount()) <= 6) values.add("추리 질문을 비교적 압축해서 사용했습니다.");
        if (value(report.getHintUsedCount()) == 0) values.add("힌트 의존도가 낮습니다.");
        if (values.isEmpty()) values.add("진행 기록이 쌓이고 있어 다음 코칭 기준이 생겼습니다.");
        return values;
    }

    private List<String> improvements(CoachingReportResponse report) {
        List<String> values = new ArrayList<>();
        if (!"CLEARED".equals(report.getStatus())) values.add("미션 메모에서 단서 유형을 먼저 분류한 뒤 최종 추리로 넘어가세요.");
        if (value(report.getWrongAnswerCount()) >= 3) values.add("오답이 많습니다. 제출 전에 정답 단서와 목적지 단서를 분리해 확인하세요.");
        if (value(report.getDeductionQuestionCount()) >= 10) values.add("질문 수가 많습니다. 예/아니오로 검증 가능한 질문부터 우선하세요.");
        if (value(report.getFinalGuessCount()) >= 3) values.add("최종 제출 전 후보를 하나로 좁히는 검증 질문을 추가하세요.");
        if (value(report.getHintUsedCount()) >= 3) values.add("힌트를 보기 전 현장 관찰 요소를 사진/메모로 먼저 정리하세요.");
        if (values.isEmpty()) values.add("현재 기록은 안정적입니다. 다음에는 더 높은 점수를 목표로 힌트와 질문 수를 줄여보세요.");
        return values;
    }

    private List<String> nextActions(CoachingReportResponse report) {
        if (!"CLEARED".equals(report.getStatus())) {
            return List.of("미션 메모 단서 보드를 다시 확인하세요.", "완료하지 않은 장소를 먼저 방문하세요.", "최종 추리는 목적지 근거가 2개 이상 모였을 때 시작하세요.");
        }
        return List.of("클리어 리포트를 보고 놓친 단서를 복기하세요.", "랭킹에서 같은 사건의 상위 기록과 비교하세요.", "추천 화면에서 다음 미션 메모을 선택하세요.");
    }

    private String playStyle(int totalCleared, int totalHints, int totalWrong, int totalQuestions) {
        if (totalCleared == 0) return "탐색 시작형";
        if (totalHints <= totalCleared && totalWrong <= totalCleared) return "정밀 추리형";
        if (totalQuestions >= totalCleared * 8) return "질문 검증형";
        if (totalHints >= totalCleared * 2) return "힌트 활용형";
        return "균형형";
    }

    private List<String> globalAdvice(int totalStarted, int totalCleared, int totalHints, int totalWrong, int totalQuestions) {
        List<String> advice = new ArrayList<>();
        if (totalStarted == 0) advice.add("첫 미션 메모을 시작하면 코칭 정확도가 올라갑니다.");
        if (totalStarted > totalCleared) advice.add("진행 중인 사건을 먼저 마무리하면 추천과 챌린지 진행률이 좋아집니다.");
        if (totalWrong > totalCleared * 2) advice.add("정답 제출 전 단서 보드에서 정답 단서와 목적지 단서를 따로 검토하세요.");
        if (totalQuestions > totalCleared * 8) advice.add("최종 추리 질문은 후보를 제거하는 방식으로 줄이는 것이 좋습니다.");
        if (totalHints > totalCleared * 2) advice.add("힌트 사용 전 현장 관찰 키워드를 3개 이상 적어보세요.");
        if (advice.isEmpty()) advice.add("현재 플레이 패턴은 안정적입니다. 다음 목표는 질문 수와 힌트 수를 줄이는 것입니다.");
        return advice;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
