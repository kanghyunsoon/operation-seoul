package com.operation.seoul.coaching.repository;

import com.operation.seoul.coaching.dto.CoachingReportResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CoachingRepository {

    @Select("""
            select p.episode_id,
                   e.title as episode_title,
                   p.status,
                   p.score,
                   p.visited_spot_ids,
                   p.completed_spot_ids,
                   p.hint_used_count,
                   p.wrong_answer_count,
                   p.deduction_question_count,
                   p.final_guess_count,
                   p.started_at,
                   p.cleared_at
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
            order by coalesce(p.cleared_at, p.last_played_at, p.started_at) desc, p.id desc
            """)
    @Results(id = "CoachingReportMap", value = {
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "episodeTitle", column = "episode_title"),
            @Result(property = "status", column = "status"),
            @Result(property = "score", column = "score"),
            @Result(property = "hintUsedCount", column = "hint_used_count"),
            @Result(property = "wrongAnswerCount", column = "wrong_answer_count"),
            @Result(property = "deductionQuestionCount", column = "deduction_question_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "clearedAt", column = "cleared_at")
    })
    List<CoachingReportResponse> findReports(Long userId);

    @Select("""
            select p.episode_id,
                   e.title as episode_title,
                   p.status,
                   p.score,
                   p.visited_spot_ids,
                   p.completed_spot_ids,
                   p.hint_used_count,
                   p.wrong_answer_count,
                   p.deduction_question_count,
                   p.final_guess_count,
                   p.started_at,
                   p.cleared_at
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
              and p.episode_id = #{episodeId}
            limit 1
            """)
    @ResultMap("CoachingReportMap")
    CoachingReportResponse findReport(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Select("select count(*) from user_episode_progress where user_id = #{userId} and status <> 'NOT_STARTED'")
    int countStarted(Long userId);

    @Select("select count(*) from user_episode_progress where user_id = #{userId} and status = 'CLEARED'")
    int countCleared(Long userId);

    @Select("select coalesce(round(avg(score)), 0) from user_episode_progress where user_id = #{userId} and status = 'CLEARED' and score is not null")
    int averageScore(Long userId);

    @Select("select coalesce(sum(hint_used_count), 0) from user_episode_progress where user_id = #{userId}")
    int sumHints(Long userId);

    @Select("select coalesce(sum(wrong_answer_count), 0) from user_episode_progress where user_id = #{userId}")
    int sumWrongAnswers(Long userId);

    @Select("select coalesce(sum(deduction_question_count), 0) from user_episode_progress where user_id = #{userId}")
    int sumDeductionQuestions(Long userId);
}
