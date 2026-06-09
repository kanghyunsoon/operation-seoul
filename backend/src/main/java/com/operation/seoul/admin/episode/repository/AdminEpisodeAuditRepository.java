package com.operation.seoul.admin.episode.repository;

import com.operation.seoul.admin.episode.domain.AdminEpisodeAuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminEpisodeAuditRepository {

    @Insert("""
            insert into admin_episode_audit_logs (
                episode_id, episode_title, actor_user_id, actor_email, actor_nickname,
                action, target_type, target_id, summary, request_id
            )
            values (
                #{episodeId}, #{episodeTitle}, #{actorUserId}, #{actorEmail}, #{actorNickname},
                #{action}, #{targetType}, #{targetId}, #{summary}, #{requestId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminEpisodeAuditLog auditLog);

    @Select("""
            select id, episode_id, episode_title, actor_user_id, actor_email, actor_nickname,
                   action, target_type, target_id, summary, request_id, created_at
            from admin_episode_audit_logs
            where episode_id = #{episodeId}
            order by created_at desc, id desc
            limit #{limit}
            """)
    @Results(id = "AdminEpisodeAuditMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "episodeId", column = "episode_id"),
            @Result(property = "episodeTitle", column = "episode_title"),
            @Result(property = "actorUserId", column = "actor_user_id"),
            @Result(property = "actorEmail", column = "actor_email"),
            @Result(property = "actorNickname", column = "actor_nickname"),
            @Result(property = "action", column = "action"),
            @Result(property = "targetType", column = "target_type"),
            @Result(property = "targetId", column = "target_id"),
            @Result(property = "summary", column = "summary"),
            @Result(property = "requestId", column = "request_id"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<AdminEpisodeAuditLog> findByEpisodeId(
            @Param("episodeId") Long episodeId,
            @Param("limit") int limit
    );
}
