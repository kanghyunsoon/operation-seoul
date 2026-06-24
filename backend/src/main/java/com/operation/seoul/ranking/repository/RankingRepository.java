package com.operation.seoul.ranking.repository;

import com.operation.seoul.ranking.dto.RankingEntryResponse;
import com.operation.seoul.ranking.dto.PlayerRankingResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RankingRepository {

    @Select("""
            select p.episode_id,
                   e.title as episode_title,
                   p.user_id,
                   u.nickname,
                   u.profile_image_url,
                   p.score,
                   p.wrong_answer_count,
                   p.deduction_question_count,
                   p.final_guess_count,
                   p.cleared_at
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            join users u on u.id = p.user_id
            where p.status = 'CLEARED'
              and p.cleared_at is not null
              and u.status = 'ACTIVE'
              and (#{episodeId} is null or p.episode_id = #{episodeId})
            order by p.score desc,
                     p.wrong_answer_count asc,
                     p.deduction_question_count asc,
                     p.final_guess_count asc,
                     p.cleared_at asc
            limit #{limit}
            """)
    @Results(id = "RankingEntryMap", value = {
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "episodeTitle", column = "episode_title"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "profileImageUrl", column = "profile_image_url"),
            @Result(property = "score", column = "score"),
            @Result(property = "wrongAnswerCount", column = "wrong_answer_count"),
            @Result(property = "deductionQuestionCount", column = "deduction_question_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count"),
            @Result(property = "clearedAt", column = "cleared_at")
    })
    List<RankingEntryResponse> findRankings(@Param("episodeId") Long episodeId, @Param("limit") int limit);

    @Select("""
            select p.episode_id,
                   e.title as episode_title,
                   p.user_id,
                   u.nickname,
                   u.profile_image_url,
                   p.score,
                   p.wrong_answer_count,
                   p.deduction_question_count,
                   p.final_guess_count,
                   p.cleared_at
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            join users u on u.id = p.user_id
            where p.status = 'CLEARED'
              and p.cleared_at is not null
              and u.status = 'ACTIVE'
              and p.user_id = #{userId}
              and (#{episodeId} is null or p.episode_id = #{episodeId})
            order by p.cleared_at desc
            """)
    @ResultMap("RankingEntryMap")
    List<RankingEntryResponse> findMyClears(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Select("""
            select p.user_id,
                   u.nickname,
                   u.profile_image_url,
                   coalesce(sum(p.score), 0) as total_score,
                   count(*) as clear_count,
                   coalesce(sum(p.wrong_answer_count), 0) as wrong_answer_count,
                   coalesce(sum(p.deduction_question_count), 0) as deduction_question_count,
                   coalesce(sum(p.final_guess_count), 0) as final_guess_count
            from user_episode_progress p
            join users u on u.id = p.user_id
            where p.status = 'CLEARED'
              and p.cleared_at is not null
              and u.status = 'ACTIVE'
            group by p.user_id, u.nickname, u.profile_image_url
            order by total_score desc,
                     clear_count desc,
                     wrong_answer_count asc,
                     deduction_question_count asc,
                     final_guess_count asc
            limit #{limit}
            """)
    @Results(id = "PlayerRankingMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "profileImageUrl", column = "profile_image_url"),
            @Result(property = "totalScore", column = "total_score"),
            @Result(property = "clearCount", column = "clear_count"),
            @Result(property = "wrongAnswerCount", column = "wrong_answer_count"),
            @Result(property = "deductionQuestionCount", column = "deduction_question_count"),
            @Result(property = "finalGuessCount", column = "final_guess_count")
    })
    List<PlayerRankingResponse> findPlayerRankings(@Param("limit") int limit);
}
