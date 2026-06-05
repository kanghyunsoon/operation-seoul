package com.operation.seoul.review.repository;

import com.operation.seoul.review.domain.EpisodeReview;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EpisodeReviewRepository {
    @Select("""
            select r.id, r.episode_id, r.user_id, r.rating, r.difficulty_rating, r.content, r.spoiler,
                   r.status, r.created_at, r.updated_at, u.nickname as author_nickname, e.title as episode_title
            from episode_reviews r
            join users u on u.id = r.user_id
            join episodes e on e.id = r.episode_id
            where r.episode_id = #{episodeId}
              and r.status <> 'DELETED'
            order by r.created_at desc, r.id desc
            """)
    @Results(id = "EpisodeReviewMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "rating", column = "rating"),
            @Result(property = "difficultyRating", column = "difficulty_rating"),
            @Result(property = "content", column = "content"),
            @Result(property = "spoiler", column = "spoiler"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "episodeTitle", column = "episode_title")
    })
    List<EpisodeReview> findByEpisodeId(Long episodeId);

    @Select("""
            select r.id, r.episode_id, r.user_id, r.rating, r.difficulty_rating, r.content, r.spoiler,
                   r.status, r.created_at, r.updated_at, u.nickname as author_nickname, e.title as episode_title
            from episode_reviews r
            join users u on u.id = r.user_id
            join episodes e on e.id = r.episode_id
            where r.user_id = #{userId}
              and r.status <> 'DELETED'
            order by r.created_at desc, r.id desc
            """)
    @ResultMap("EpisodeReviewMap")
    List<EpisodeReview> findByUserId(Long userId);

    @Select("""
            select r.id, r.episode_id, r.user_id, r.rating, r.difficulty_rating, r.content, r.spoiler,
                   r.status, r.created_at, r.updated_at, u.nickname as author_nickname, e.title as episode_title
            from episode_reviews r
            join users u on u.id = r.user_id
            join episodes e on e.id = r.episode_id
            where (#{episodeId} is null or r.episode_id = #{episodeId})
              and (#{status} is null or #{status} = '' or r.status = #{status})
              and (
                    #{keyword} is null
                    or #{keyword} = ''
                    or u.nickname like concat('%', #{keyword}, '%')
                    or u.email like concat('%', #{keyword}, '%')
                    or e.title like concat('%', #{keyword}, '%')
                  )
            order by r.created_at desc, r.id desc
            """)
    @ResultMap("EpisodeReviewMap")
    List<EpisodeReview> findAdminReviews(@Param("episodeId") Long episodeId, @Param("status") String status, @Param("keyword") String keyword);

    @Select("""
            select r.id, r.episode_id, r.user_id, r.rating, r.difficulty_rating, r.content, r.spoiler,
                   r.status, r.created_at, r.updated_at, u.nickname as author_nickname, e.title as episode_title
            from episode_reviews r
            join users u on u.id = r.user_id
            join episodes e on e.id = r.episode_id
            where r.id = #{id}
            limit 1
            """)
    @ResultMap("EpisodeReviewMap")
    EpisodeReview findById(Long id);

    @Select("""
            select r.id, r.episode_id, r.user_id, r.rating, r.difficulty_rating, r.content, r.spoiler,
                   r.status, r.created_at, r.updated_at, u.nickname as author_nickname, e.title as episode_title
            from episode_reviews r
            join users u on u.id = r.user_id
            join episodes e on e.id = r.episode_id
            where r.episode_id = #{episodeId}
              and r.user_id = #{userId}
              and r.status <> 'DELETED'
            limit 1
            """)
    @ResultMap("EpisodeReviewMap")
    EpisodeReview findByEpisodeIdAndUserId(@Param("episodeId") Long episodeId, @Param("userId") Long userId);

    @Select("""
            select count(*)
            from user_episode_progress
            where user_id = #{userId}
              and episode_id = #{episodeId}
              and status = 'CLEARED'
              and cleared_at is not null
            """)
    int countClearedProgress(@Param("episodeId") Long episodeId, @Param("userId") Long userId);

    @Insert("""
            insert into episode_reviews (episode_id, user_id, rating, difficulty_rating, content, spoiler, status)
            values (#{episodeId}, #{userId}, #{rating}, #{difficultyRating}, #{content}, #{spoiler}, 'VISIBLE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EpisodeReview review);

    @Update("""
            update episode_reviews
            set rating = #{rating}, difficulty_rating = #{difficultyRating}, content = #{content},
                spoiler = #{spoiler}, updated_at = current_timestamp
            where id = #{id}
            """)
    int update(EpisodeReview review);

    @Update("update episode_reviews set status = 'DELETED', updated_at = current_timestamp where id = #{id}")
    int softDelete(Long id);

    @Update("update episode_reviews set status = 'HIDDEN', updated_at = current_timestamp where id = #{id}")
    int hide(Long id);

    @Update("update episode_reviews set status = 'VISIBLE', updated_at = current_timestamp where id = #{id}")
    int restore(Long id);

    @Select("""
            select coalesce(avg(rating), 0)
            from episode_reviews
            where episode_id = #{episodeId}
              and status = 'VISIBLE'
            """)
    Double averageRating(Long episodeId);

    @Select("""
            select coalesce(avg(difficulty_rating), 0)
            from episode_reviews
            where episode_id = #{episodeId}
              and status = 'VISIBLE'
            """)
    Double averageDifficultyRating(Long episodeId);

    @Select("select count(*) from episode_reviews where episode_id = #{episodeId} and status = 'VISIBLE'")
    Integer countVisible(Long episodeId);
}
