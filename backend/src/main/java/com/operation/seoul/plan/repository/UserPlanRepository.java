package com.operation.seoul.plan.repository;

import com.operation.seoul.plan.dto.UserPlanResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserPlanRepository {

    @Insert("""
            insert into user_plans (user_id, episode_id, planned_at, memo, status)
            values (#{userId}, #{episodeId}, #{plannedAt}, #{memo}, #{status})
            on duplicate key update
                planned_at = values(planned_at),
                memo = values(memo),
                status = values(status),
                updated_at = current_timestamp
            """)
    int upsert(@Param("userId") Long userId,
               @Param("episodeId") Long episodeId,
               @Param("plannedAt") LocalDateTime plannedAt,
               @Param("memo") String memo,
               @Param("status") String status);

    @Update("""
            update user_plans
            set planned_at = #{plannedAt},
                memo = #{memo},
                status = #{status},
                updated_at = current_timestamp
            where id = #{planId}
              and user_id = #{userId}
            """)
    int update(@Param("planId") Long planId,
               @Param("userId") Long userId,
               @Param("plannedAt") LocalDateTime plannedAt,
               @Param("memo") String memo,
               @Param("status") String status);

    @Delete("""
            delete from user_plans
            where id = #{planId}
              and user_id = #{userId}
            """)
    int delete(@Param("planId") Long planId, @Param("userId") Long userId);

    @Select("""
            select p.id, p.episode_id, e.title as episode_title, e.subtitle as episode_subtitle,
                   e.era, e.genre, e.difficulty, e.estimated_time, e.estimated_distance,
                   p.planned_at, p.memo, p.status, p.created_at, p.updated_at
            from user_plans p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
              and e.status = 'PUBLISHED'
            order by
              case when p.status = 'DONE' then 1 else 0 end asc,
              p.planned_at asc,
              p.id desc
            """)
    @Results(id = "UserPlanResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "episodeTitle", column = "episode_title"),
            @Result(property = "episodeSubtitle", column = "episode_subtitle"),
            @Result(property = "era", column = "era"),
            @Result(property = "genre", column = "genre"),
            @Result(property = "difficulty", column = "difficulty"),
            @Result(property = "estimatedTime", column = "estimated_time"),
            @Result(property = "estimatedDistance", column = "estimated_distance"),
            @Result(property = "plannedAt", column = "planned_at"),
            @Result(property = "memo", column = "memo"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<UserPlanResponse> findByUserId(Long userId);

    @Select("""
            select p.id, p.episode_id, e.title as episode_title, e.subtitle as episode_subtitle,
                   e.era, e.genre, e.difficulty, e.estimated_time, e.estimated_distance,
                   p.planned_at, p.memo, p.status, p.created_at, p.updated_at
            from user_plans p
            join episodes e on e.id = p.episode_id
            where p.user_id = #{userId}
              and p.episode_id = #{episodeId}
            limit 1
            """)
    @ResultMap("UserPlanResponseMap")
    UserPlanResponse findByUserIdAndEpisodeId(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    @Select("""
            select p.id, p.episode_id, e.title as episode_title, e.subtitle as episode_subtitle,
                   e.era, e.genre, e.difficulty, e.estimated_time, e.estimated_distance,
                   p.planned_at, p.memo, p.status, p.created_at, p.updated_at
            from user_plans p
            join episodes e on e.id = p.episode_id
            where p.id = #{planId}
              and p.user_id = #{userId}
            limit 1
            """)
    @ResultMap("UserPlanResponseMap")
    UserPlanResponse findByIdAndUserId(@Param("planId") Long planId, @Param("userId") Long userId);
}
