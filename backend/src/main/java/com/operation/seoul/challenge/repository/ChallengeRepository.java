package com.operation.seoul.challenge.repository;

import com.operation.seoul.challenge.dto.ChallengeResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChallengeRepository {

    @Select("""
            select c.id, c.title, c.description, c.target_type, c.target_count, c.status, c.start_at, c.end_at,
                   case when e.user_id is null then false else true end as joined,
                   e.status as entry_status,
                   e.joined_at,
                   e.completed_at,
                   (select count(*)
                    from user_episode_progress p
                    where p.user_id = #{userId}
                      and p.status = 'CLEARED'
                      and p.cleared_at is not null) as progress_count
            from challenges c
            left join user_challenge_entries e on e.challenge_id = c.id and e.user_id = #{userId}
            where c.status = 'ACTIVE'
              and (c.start_at is null or c.start_at <= current_timestamp)
              and (c.end_at is null or c.end_at >= current_timestamp)
            order by c.target_count asc, c.id asc
            """)
    @Results(id = "ChallengeResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "title", column = "title"),
            @Result(property = "description", column = "description"),
            @Result(property = "targetType", column = "target_type"),
            @Result(property = "targetCount", column = "target_count"),
            @Result(property = "status", column = "status"),
            @Result(property = "startAt", column = "start_at"),
            @Result(property = "endAt", column = "end_at"),
            @Result(property = "joined", column = "joined"),
            @Result(property = "entryStatus", column = "entry_status"),
            @Result(property = "progressCount", column = "progress_count"),
            @Result(property = "joinedAt", column = "joined_at"),
            @Result(property = "completedAt", column = "completed_at")
    })
    List<ChallengeResponse> findActiveChallenges(Long userId);

    @Select("""
            select c.id, c.title, c.description, c.target_type, c.target_count, c.status, c.start_at, c.end_at,
                   true as joined,
                   e.status as entry_status,
                   e.joined_at,
                   e.completed_at,
                   (select count(*)
                    from user_episode_progress p
                    where p.user_id = #{userId}
                      and p.status = 'CLEARED'
                      and p.cleared_at is not null) as progress_count
            from user_challenge_entries e
            join challenges c on c.id = e.challenge_id
            where e.user_id = #{userId}
              and c.status = 'ACTIVE'
            order by case e.status when 'COMPLETED' then 1 else 0 end asc, c.target_count asc
            """)
    @ResultMap("ChallengeResponseMap")
    List<ChallengeResponse> findMyChallenges(Long userId);

    @Select("""
            select c.id, c.title, c.description, c.target_type, c.target_count, c.status, c.start_at, c.end_at,
                   case when e.user_id is null then false else true end as joined,
                   e.status as entry_status,
                   e.joined_at,
                   e.completed_at,
                   (select count(*)
                    from user_episode_progress p
                    where p.user_id = #{userId}
                      and p.status = 'CLEARED'
                      and p.cleared_at is not null) as progress_count
            from challenges c
            left join user_challenge_entries e on e.challenge_id = c.id and e.user_id = #{userId}
            where c.id = #{challengeId}
              and c.status = 'ACTIVE'
            limit 1
            """)
    @ResultMap("ChallengeResponseMap")
    ChallengeResponse findById(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    @Select("""
            select c.id
            from challenges c
            left join user_challenge_entries e on e.challenge_id = c.id and e.user_id = #{userId}
            where c.status = 'ACTIVE'
              and (c.start_at is null or c.start_at <= current_timestamp)
              and (c.end_at is null or c.end_at >= current_timestamp)
              and c.target_count > #{targetCount}
              and e.user_id is null
            order by c.target_count asc, c.id asc
            limit 1
            """)
    Long findNextHigherChallengeId(@Param("userId") Long userId, @Param("targetCount") int targetCount);

    @Insert("""
            insert ignore into user_challenge_entries (challenge_id, user_id, status)
            values (#{challengeId}, #{userId}, 'JOINED')
            """)
    int join(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    @Update("""
            update user_challenge_entries
            set status = 'COMPLETED',
                completed_at = current_timestamp
            where challenge_id = #{challengeId}
              and user_id = #{userId}
              and status <> 'COMPLETED'
            """)
    int complete(@Param("challengeId") Long challengeId, @Param("userId") Long userId);
}
