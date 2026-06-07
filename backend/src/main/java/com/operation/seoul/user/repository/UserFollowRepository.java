package com.operation.seoul.user.repository;

import com.operation.seoul.user.dto.UserFollowResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFollowRepository {

    @Insert("""
            insert ignore into user_follow (follower_id, following_id)
            values (#{followerId}, #{followingId})
            """)
    int follow(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Delete("""
            delete from user_follow
            where follower_id = #{followerId}
              and following_id = #{followingId}
            """)
    int unfollow(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Select("""
            select count(*)
            from user_follow
            where follower_id = #{followerId}
              and following_id = #{followingId}
            """)
    int isFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    @Select("select count(*) from user_follow where following_id = #{userId}")
    int countFollowers(Long userId);

    @Select("select count(*) from user_follow where follower_id = #{userId}")
    int countFollowing(Long userId);

    @Select("""
            select u.id as user_id,
                   u.nickname,
                   u.profile_image_url,
                   true as following,
                   (select count(*) from user_follow f2 where f2.following_id = u.id) as follower_count,
                   (select count(*) from user_follow f3 where f3.follower_id = u.id) as following_count
            from user_follow f
            join users u on u.id = f.following_id
            where f.follower_id = #{userId}
              and u.status = 'ACTIVE'
            order by f.created_at desc
            """)
    @Results(id = "UserFollowResponseMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "profileImageUrl", column = "profile_image_url"),
            @Result(property = "following", column = "following"),
            @Result(property = "followerCount", column = "follower_count"),
            @Result(property = "followingCount", column = "following_count")
    })
    List<UserFollowResponse> findFollowing(Long userId);

    @Select("""
            select u.id as user_id,
                   u.nickname,
                   u.profile_image_url,
                   case when exists (
                       select 1 from user_follow mine
                       where mine.follower_id = #{viewerId}
                         and mine.following_id = u.id
                   ) then true else false end as following,
                   (select count(*) from user_follow f2 where f2.following_id = u.id) as follower_count,
                   (select count(*) from user_follow f3 where f3.follower_id = u.id) as following_count
            from user_follow f
            join users u on u.id = f.follower_id
            where f.following_id = #{userId}
              and u.status = 'ACTIVE'
            order by f.created_at desc
            """)
    @ResultMap("UserFollowResponseMap")
    List<UserFollowResponse> findFollowers(@Param("userId") Long userId, @Param("viewerId") Long viewerId);
}
