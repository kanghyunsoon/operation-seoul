package com.operation.seoul.user.repository;

import com.operation.seoul.user.dto.UserFeedClearMapResponse;
import com.operation.seoul.user.dto.UserFeedPostResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFeedRepository {

    @Select("select count(*) from region_question where user_id = #{userId}")
    int countCommunityPosts(Long userId);

    @Select("""
            select q.id,
                   q.region_id,
                   r.name as region_name,
                   q.title,
                   q.content,
                   q.created_at,
                   (select count(*) from region_question_like ql where ql.question_id = q.id) as like_count,
                   (select count(*) from region_answer a where a.question_id = q.id) as comment_count
            from region_question q
            join region r on r.id = q.region_id
            where q.user_id = #{userId}
            order by q.created_at desc, q.id desc
            limit #{limit} offset #{offset}
            """)
    @Results(id = "UserFeedPostMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "regionName", column = "region_name"),
            @Result(property = "title", column = "title"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "likeCount", column = "like_count"),
            @Result(property = "commentCount", column = "comment_count")
    })
    List<UserFeedPostResponse> findCommunityPosts(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            select e.id as episode_id,
                   e.title,
                   e.subtitle,
                   r.name as region_name,
                   e.era,
                   e.genre,
                   e.difficulty,
                   e.estimated_time,
                   e.estimated_distance,
                   p.score,
                   p.cleared_at
            from user_episode_progress p
            join episodes e on e.id = p.episode_id
            left join region r on r.id = e.region_id
            where p.user_id = #{userId}
              and p.status = 'CLEARED'
            order by p.cleared_at desc, p.id desc
            """)
    @Results(id = "UserFeedClearMapMap", value = {
            @Result(property = "episodeId", column = "episode_id", id = true),
            @Result(property = "title", column = "title"),
            @Result(property = "subtitle", column = "subtitle"),
            @Result(property = "regionName", column = "region_name"),
            @Result(property = "era", column = "era"),
            @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"),
            @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"),
            @Result(property = "score", column = "score"),
            @Result(property = "clearedAt", column = "cleared_at")
    })
    List<UserFeedClearMapResponse> findClearedMaps(Long userId);
}
