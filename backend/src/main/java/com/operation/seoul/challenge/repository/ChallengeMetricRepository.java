package com.operation.seoul.challenge.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChallengeMetricRepository {
    @Select("""
            select coalesce(max(score), 0)
            from user_episode_progress
            where user_id = #{userId}
              and status = 'CLEARED'
            """)
    int maxSingleScore(Long userId);

    @Select("""
            select coalesce(sum(score), 0)
            from user_episode_progress
            where user_id = #{userId}
              and status = 'CLEARED'
            """)
    int totalScore(Long userId);

    @Select("""
            select count(*)
            from user_follow mine
            where mine.follower_id = #{userId}
              and exists (
                  select 1
                  from user_follow back
                  where back.follower_id = mine.following_id
                    and back.following_id = #{userId}
              )
            """)
    int mutualFriendCount(Long userId);

    @Select("""
            select count(distinct date(coalesce(cleared_at, last_played_at, started_at)))
            from user_episode_progress
            where user_id = #{userId}
              and (started_at is not null or last_played_at is not null or cleared_at is not null)
            """)
    int playDays(Long userId);

    @Select("select count(*) from user_challenge_entries where user_id = #{userId} and status = 'COMPLETED'")
    int completedEntryCount(Long userId);

    @Select("select count(*) from user_challenge_entries where user_id = #{userId} and challenge_id = #{challengeId} and status = 'COMPLETED'")
    int isEntryCompleted(@Param("userId") Long userId, @Param("challengeId") Long challengeId);
}
