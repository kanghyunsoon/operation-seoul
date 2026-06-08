package com.operation.seoul.ranking.repository;

import com.operation.seoul.ranking.dto.RankingEntryResponse;
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
}
