package com.operation.seoul.auth.repository;

import com.operation.seoul.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface SocialAccountRepository {

    default Optional<User> findUser(String provider, String providerUserId) {
        return Optional.ofNullable(findOneUser(provider, providerUserId));
    }

    @Select("""
            select u.id, u.email, u.password, u.nickname, u.is_admin, u.role,
                   u.profile_image_url, u.status_message, u.profile_public, u.status,
                   u.created_at, u.updated_at
            from user_social_accounts social
            join users u on u.id = social.user_id
            where social.provider = #{provider}
              and social.provider_user_id = #{providerUserId}
            limit 1
            """)
    @Results(id = "SocialUserResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "email", column = "email"),
            @Result(property = "password", column = "password"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "admin", column = "is_admin"),
            @Result(property = "role", column = "role"),
            @Result(property = "profileImageUrl", column = "profile_image_url"),
            @Result(property = "statusMessage", column = "status_message"),
            @Result(property = "profilePublic", column = "profile_public"),
            @Result(property = "status", column = "status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    User findOneUser(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    @Insert("""
            insert into user_social_accounts (user_id, provider, provider_user_id)
            values (#{userId}, #{provider}, #{providerUserId})
            """)
    int insert(@Param("userId") Long userId,
               @Param("provider") String provider,
               @Param("providerUserId") String providerUserId);
}
