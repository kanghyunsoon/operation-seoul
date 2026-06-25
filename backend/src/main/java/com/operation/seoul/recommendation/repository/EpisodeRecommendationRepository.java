package com.operation.seoul.recommendation.repository;

import com.operation.seoul.recommendation.dto.EpisodeRecommendationResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EpisodeRecommendationRepository {

    @Select("""
            select e.id as episode_id,
                   e.title,
                   e.subtitle,
                   e.era,
                   e.genre,
                   e.difficulty,
                   e.estimated_time,
                   e.estimated_distance,
                   case when f.episode_id is null then false else true end as favorited,
                   case when p.status = 'CLEARED' then true else false end as cleared,
                   case when exists (
                       select 1
                       from user_episode_progress mine
                       join episodes mine_episode on mine_episode.id = mine.episode_id
                       where mine.user_id = #{userId}
                         and mine.status = 'CLEARED'
                         and mine_episode.genre = e.genre
                   ) then 1 else 0 end as genre_match,
                   case when exists (
                       select 1
                       from user_episode_progress mine
                       join episodes mine_episode on mine_episode.id = mine.episode_id
                       where mine.user_id = #{userId}
                         and mine.status = 'CLEARED'
                         and mine_episode.difficulty = e.difficulty
                   ) then 1 else 0 end as difficulty_match
            from episodes e
            left join episode_favorites f on f.episode_id = e.id and f.user_id = #{userId}
            left join user_episode_progress p on p.episode_id = e.id and p.user_id = #{userId}
            where e.status = 'PUBLISHED'
            order by e.id asc
            """)
    @Results(id = "EpisodeRecommendationMap", value = {
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"),
            @Result(property = "era", column = "era"),
            @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"),
            @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"),
            @Result(property = "favorited", column = "favorited"),
            @Result(property = "cleared", column = "cleared")
    })
    List<EpisodeRecommendationResponse> findCandidates(Long userId);

    @Select("select count(*) from user_episode_progress where user_id = #{userId} and status = 'CLEARED'")
    int countCleared(Long userId);

    @Select("""
            select count(*)
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
              and p.status = 'CLEARED'
              and e.genre = #{genre}
            """)
    int countClearedGenre(@Param("userId") Long userId, @Param("genre") String genre);

    @Select("""
            select count(*)
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
              and p.status = 'CLEARED'
              and e.difficulty = #{difficulty}
            """)
    int countClearedDifficulty(@Param("userId") Long userId, @Param("difficulty") String difficulty);
}
