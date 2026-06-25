package com.operation.seoul.auth.repository;

import com.operation.seoul.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface UserRepository {

    default Optional<User> findByEmail(String email) {
        return Optional.ofNullable(findOneByEmail(email));
    }

    default Optional<User> findById(Long id) {
        return Optional.ofNullable(findOneById(id));
    }

    default User save(User user) {
        if (user.getId() == null) {
            insert(user);
        } else {
            update(user);
        }
        return user;
    }

    @Select("""
            select id, email, password, nickname, is_admin, role, profile_image_url, status_message, profile_public, status, created_at, updated_at
            from users
            where email = #{email}
            limit 1
            """)
    @Results(id = "UserResultMap", value = {
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
    User findOneByEmail(String email);

    @Select("""
            select id, email, password, nickname, is_admin, role, profile_image_url, status_message, profile_public, status, created_at, updated_at
            from users
            where id = #{id}
            limit 1
            """)
    @ResultMap("UserResultMap")
    User findOneById(Long id);

    @Select("select count(*) from users where email = #{email}")
    int countByEmail(String email);

    @Select("select count(*) from users where nickname = #{nickname}")
    int countByNickname(String nickname);

    @Insert("""
            insert into users (email, password, nickname, is_admin, role, profile_image_url, status_message, profile_public, status)
            values (#{email}, #{password}, #{nickname}, #{admin}, #{role}, #{profileImageUrl}, #{statusMessage}, #{profilePublic}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("""
            update users
            set email = #{email},
                password = #{password},
                nickname = #{nickname},
                is_admin = #{admin},
                role = #{role},
                profile_image_url = #{profileImageUrl},
                status_message = #{statusMessage},
                profile_public = #{profilePublic},
                status = #{status},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int update(User user);

    @Update("update users set status = 'DELETED', updated_at = current_timestamp where id = #{id}")
    int softDeleteById(Long id);

    @Select("""
            select id, email, password, nickname, is_admin, role, profile_image_url, status_message, profile_public, status, created_at, updated_at
            from users
            order by created_at desc, id desc
            """)
    @ResultMap("UserResultMap")
    java.util.List<User> findAll();

    @Update("""
            update users
            set nickname = #{nickname},
                role = #{role},
                is_admin = case when #{role} = 'ROLE_ADMIN' then true else false end,
                status = #{status},
                profile_image_url = #{profileImageUrl},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateAdminFields(@Param("id") Long id, @Param("nickname") String nickname, @Param("role") String role,
                          @Param("status") String status, @Param("profileImageUrl") String profileImageUrl);
}
