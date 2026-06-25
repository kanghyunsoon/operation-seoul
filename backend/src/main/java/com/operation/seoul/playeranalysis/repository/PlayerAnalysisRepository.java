package com.operation.seoul.playeranalysis.repository;

import com.operation.seoul.episode.domain.FinalDeductionQuestion;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import com.operation.seoul.playeranalysis.domain.PlayerAnalysis;
import com.operation.seoul.playeranalysis.domain.PlayerAnalysisMbti;
import com.operation.seoul.playeranalysis.domain.ReasoningAnswer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlayerAnalysisRepository {
    @Select("""
            select id, user_id, episode_id, visited_spot_ids, completed_spot_ids, collected_answer_clues,
                   collected_destination_clues, collected_story_clues, final_arrived_spot_id, hint_used_count,
                   wrong_answer_count, deduction_question_count, hypothesis_count, final_guess_count,
                   active_elapsed_seconds, clear_time_penalty_seconds, score, started_at, last_played_at,
                   cleared_at, status, unlocked_suspect_ids, cleared_suspect_ids, unlocked_evidence_ids
            from user_episode_progress
            where user_id = #{userId} and episode_id = #{missionId}
            limit 1
            """)
    @Results(id = "AnalysisProgressMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "visitedSpotIds", column = "visited_spot_ids"),
            @Result(property = "completedSpotIds", column = "completed_spot_ids"),
            @Result(property = "collectedAnswerClues", column = "collected_answer_clues"),
            @Result(property = "collectedDestinationClues", column = "collected_destination_clues"),
            @Result(property = "collectedStoryClues", column = "collected_story_clues"),
            @Result(property = "finalArrivedSpotId", column = "final_arrived_spot_id"),
            @Result(property = "hintUsedCount", column = "hint_used_count"),
            @Result(property = "wrongAnswerCount", column = "wrong_answer_count"),
            @Result(property = "deductionQuestionCount", column = "deduction_question_count"),
            @Result(property = "hypothesisCount", column = "hypothesis_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count"),
            @Result(property = "activeElapsedSeconds", column = "active_elapsed_seconds"),
            @Result(property = "clearTimePenaltySeconds", column = "clear_time_penalty_seconds"),
            @Result(property = "score", column = "score"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "lastPlayedAt", column = "last_played_at"),
            @Result(property = "clearedAt", column = "cleared_at"),
            @Result(property = "status", column = "status"),
            @Result(property = "unlockedSuspectIds", column = "unlocked_suspect_ids"),
            @Result(property = "clearedSuspectIds", column = "cleared_suspect_ids"),
            @Result(property = "unlockedEvidenceIds", column = "unlocked_evidence_ids")
    })
    UserEpisodeProgress findProgress(@Param("userId") Long userId, @Param("missionId") Long missionId);

    @Select("""
            select q.id, q.session_id, q.user_question, q.ai_answer_type, q.ai_answer_text, q.created_at
            from final_deduction_questions q
            join final_deduction_sessions s on s.id = q.session_id
            where s.user_id = #{userId} and s.episode_id = #{missionId}
            order by q.id desc
            limit 20
            """)
    @Results(id = "AnalysisDeductionQuestionMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "userQuestion", column = "user_question"),
            @Result(property = "aiAnswerType", column = "ai_answer_type"),
            @Result(property = "aiAnswerText", column = "ai_answer_text"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<FinalDeductionQuestion> findLatestDeductionQuestions(@Param("userId") Long userId, @Param("missionId") Long missionId);

    @Insert("""
            insert into reasoning_answer (user_id, mission_id, question, answer)
            values (#{userId}, #{missionId}, #{question}, #{answer})
            """)
    int insertReasoningAnswer(ReasoningAnswer answer);

    @Insert("""
            insert into player_analysis (user_id, mission_id, player_type, summary, strength, weakness, recommendation)
            values (#{userId}, #{missionId}, #{playerType}, #{summary}, #{strength}, #{weakness}, #{recommendation})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAnalysis(PlayerAnalysis analysis);

    @Insert("""
            insert into player_analysis_mbti (analysis_id, dimension, left_label, right_label, left_percent, right_percent)
            values (#{analysisId}, #{dimension}, #{leftLabel}, #{rightLabel}, #{leftPercent}, #{rightPercent})
            """)
    int insertMbti(PlayerAnalysisMbti mbti);

    @Select("""
            select id, user_id, mission_id, player_type, summary, strength, weakness, recommendation, created_at
            from player_analysis
            where user_id = #{userId}
            order by created_at desc, id desc
            limit 1
            """)
    @Results(id = "PlayerAnalysisMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "missionId", column = "mission_id"),
            @Result(property = "playerType", column = "player_type"),
            @Result(property = "summary", column = "summary"),
            @Result(property = "strength", column = "strength"),
            @Result(property = "weakness", column = "weakness"),
            @Result(property = "recommendation", column = "recommendation"),
            @Result(property = "createdAt", column = "created_at")
    })
    PlayerAnalysis findLatestAnalysis(Long userId);

    @Select("""
            select id, analysis_id, dimension, left_label, right_label, left_percent, right_percent
            from player_analysis_mbti
            where analysis_id = #{analysisId}
            order by id asc
            """)
    @Results(id = "PlayerAnalysisMbtiMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "analysisId", column = "analysis_id"),
            @Result(property = "dimension", column = "dimension"),
            @Result(property = "leftLabel", column = "left_label"),
            @Result(property = "rightLabel", column = "right_label"),
            @Result(property = "leftPercent", column = "left_percent"),
            @Result(property = "rightPercent", column = "right_percent")
    })
    List<PlayerAnalysisMbti> findMbtiByAnalysisId(Long analysisId);
}
