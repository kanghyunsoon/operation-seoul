package com.operation.seoul.favorite.repository;

import com.operation.seoul.favorite.domain.EpisodeFavorite;
import com.operation.seoul.favorite.dto.EpisodeFavoriteResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EpisodeFavoriteRepository {

    @Insert("""
            insert ignore into episode_favorites (user_id, episode_id)
            values (#{userId}, #{episodeId})
            """)
    int insertIgnore(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Delete("""
            delete from episode_favorites
            where user_id = #{userId}
              and episode_id = #{episodeId}
            """)
    int delete(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Select("""
            select id, user_id, episode_id, created_at
            from episode_favorites
            where user_id = #{userId}
              and episode_id = #{episodeId}
            limit 1
            """)
    @Results(id = "EpisodeFavoriteMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "createdAt", column = "created_at")
    })
    EpisodeFavorite findByUserIdAndEpisodeId(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Select("""
            select episode_id
            from episode_favorites
            where user_id = #{userId}
            """)
    List<Long> findEpisodeIdsByUserId(Long userId);

    @Select("""
            select f.id as favorite_id, e.id as episode_id, e.title, e.subtitle, e.era, e.genre,
                   e.difficulty, e.estimated_time, e.estimated_distance, f.created_at
            from episode_favorites f
            join episodes e on e.id = f.episode_id
            where f.user_id = #{userId}
              and e.status = 'PUBLISHED'
            order by f.created_at desc, f.id desc
            """)
    @Results(id = "EpisodeFavoriteResponseMap", value = {
            @Result(property = "favoriteId", column = "favorite_id"),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"),
            @Result(property = "era", column = "era"),
            @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"),
            @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<EpisodeFavoriteResponse> findPublishedFavoritesByUserId(Long userId);
}
