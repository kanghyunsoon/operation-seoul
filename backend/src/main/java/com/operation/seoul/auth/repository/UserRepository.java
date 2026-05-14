package com.operation.seoul.auth.repository;

import com.operation.seoul.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 사용자 계정 영속성 계층입니다.
 * JPA 금지 제약에 맞춰 Spring Data Repository가 아니라 MyBatis Mapper로 직접 SQL을 선언합니다.
 */
@Mapper
public interface UserRepository {

    /** 로그인 및 JWT 인증 필터에서 이메일로 사용자를 찾습니다. */
    default Optional<User> findByEmail(String email) {
        return Optional.ofNullable(findOneByEmail(email));
    }

    /** 저장 후 생성된 id를 도메인 객체에 다시 채우기 위해 MySQL generated key를 사용합니다. */
    default User save(User user) {
        if (user.getId() == null) {
            insert(user);
        } else {
            update(user);
        }
        return user;
    }

    @Select("""
            select id, email, password, nickname, is_admin
            from users
            where email = #{email}
            limit 1
            """)
    @Results(id = "UserResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "email", column = "email"),
            @Result(property = "password", column = "password"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "admin", column = "is_admin")
    })
    User findOneByEmail(String email);

    @Select("""
            select id, email, password, nickname, is_admin
            from users
            where id = #{id}
            limit 1
            """)
    @ResultMap("UserResultMap")
    User findOneById(Long id);

    default Optional<User> findById(Long id) {
        return Optional.ofNullable(findOneById(id));
    }

    @Insert("""
            insert into users (email, password, nickname, is_admin)
            values (#{email}, #{password}, #{nickname}, #{admin})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("""
            update users
            set email = #{email},
                password = #{password},
                nickname = #{nickname},
                is_admin = #{admin}
            where id = #{id}
            """)
    int update(User user);
}
