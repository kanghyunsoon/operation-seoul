package com.operation.seoul.review.repository;

import com.operation.seoul.review.domain.EpisodeReviewComment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EpisodeReviewCommentRepository {
    @Select("""
            select c.id, c.review_id, c.user_id, c.content, c.spoiler, c.status, c.created_at, c.updated_at,
                   u.nickname as author_nickname
            from episode_review_comments c
            join users u on u.id = c.user_id
            where c.review_id = #{reviewId}
              and c.status <> 'DELETED'
            order by c.created_at asc, c.id asc
            """)
    @Results(id = "EpisodeReviewCommentMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "reviewId", column = "review_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "content", column = "content"),
            @Result(property = "spoiler", column = "spoiler"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "authorNickname", column = "author_nickname")
    })
    List<EpisodeReviewComment> findByReviewId(Long reviewId);

    @Select("""
            select c.id, c.review_id, c.user_id, c.content, c.spoiler, c.status, c.created_at, c.updated_at,
                   u.nickname as author_nickname
            from episode_review_comments c
            join users u on u.id = c.user_id
            where c.id = #{id}
            limit 1
            """)
    @ResultMap("EpisodeReviewCommentMap")
    EpisodeReviewComment findById(Long id);

    @Insert("""
            insert into episode_review_comments (review_id, user_id, content, spoiler, status)
            values (#{reviewId}, #{userId}, #{content}, #{spoiler}, 'VISIBLE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EpisodeReviewComment comment);

    @Update("""
            update episode_review_comments
            set content = #{content}, spoiler = #{spoiler}, updated_at = current_timestamp
            where id = #{id}
            """)
    int update(EpisodeReviewComment comment);

    @Update("update episode_review_comments set status = 'DELETED', updated_at = current_timestamp where id = #{id}")
    int softDelete(Long id);
}
