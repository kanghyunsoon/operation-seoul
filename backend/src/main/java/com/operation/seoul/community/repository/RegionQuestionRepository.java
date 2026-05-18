package com.operation.seoul.community.repository;

import com.operation.seoul.community.domain.RegionAnswer;
import com.operation.seoul.community.domain.RegionQuestion;
import com.operation.seoul.community.dto.RegionAnswerResponse;
import com.operation.seoul.community.dto.RegionQuestionResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RegionQuestionRepository {

    @Select("""
            select q.id, q.region_id, q.user_id, q.title, q.content, q.created_at, q.updated_at
            from region_question q
            where q.id = #{id}
              and q.region_id = #{regionId}
            limit 1
            """)
    @Results(id = "RegionQuestionResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    RegionQuestion findQuestionByIdAndRegionId(@Param("id") Long id, @Param("regionId") Long regionId);

    @Select("""
            select a.id, a.question_id, a.user_id, a.content, a.created_at, a.updated_at
            from region_answer a
            where a.id = #{id}
              and a.question_id = #{questionId}
            limit 1
            """)
    @Results(id = "RegionAnswerResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "questionId", column = "question_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    RegionAnswer findAnswerByIdAndQuestionId(@Param("id") Long id, @Param("questionId") Long questionId);

    @Select("""
            select q.id,
                   q.region_id,
                   q.user_id,
                   u.nickname as author_nickname,
                   q.title,
                   q.content,
                   q.created_at,
                   q.updated_at,
                   (select count(*)
                    from region_question_like ql
                    where ql.question_id = q.id) as like_count,
                   exists (
                    select 1
                    from region_question_like my_like
                    where my_like.question_id = q.id
                      and my_like.user_id = #{userId}
                   ) as liked
            from region_question q
            join users u on u.id = q.user_id
            where q.region_id = #{regionId}
            order by q.created_at desc, q.id desc
            """)
    @Results(id = "RegionQuestionResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "title", column = "title"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "likeCount", column = "like_count"),
            @Result(property = "liked", column = "liked")
    })
    List<RegionQuestionResponse> findQuestionResponsesByRegionId(@Param("regionId") Long regionId, @Param("userId") Long userId);

    @Select("""
            select a.id,
                   a.question_id,
                   a.user_id,
                   u.nickname as author_nickname,
                   a.content,
                   a.created_at,
                   a.updated_at
            from region_answer a
            join users u on u.id = a.user_id
            where a.question_id = #{questionId}
            order by a.created_at asc, a.id asc
            """)
    @Results(id = "RegionAnswerResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "questionId", column = "question_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<RegionAnswerResponse> findAnswerResponsesByQuestionId(Long questionId);

    @Insert("""
            insert into region_question (region_id, user_id, title, content)
            values (#{regionId}, #{userId}, #{title}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertQuestion(RegionQuestion question);

    @Update("""
            update region_question
            set title = #{title},
                content = #{content},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateQuestion(RegionQuestion question);

    @Delete("delete from region_question where id = #{id}")
    int deleteQuestionById(@Param("id") Long id);

    @Insert("""
            insert into region_answer (question_id, user_id, content)
            values (#{questionId}, #{userId}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAnswer(RegionAnswer answer);

    @Update("""
            update region_answer
            set content = #{content},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateAnswer(RegionAnswer answer);

    @Delete("delete from region_answer where id = #{id}")
    int deleteAnswerById(@Param("id") Long id);

    @Select("""
            select count(*)
            from region_question_like
            where question_id = #{questionId}
              and user_id = #{userId}
            """)
    int countLikeByQuestionIdAndUserId(@Param("questionId") Long questionId, @Param("userId") Long userId);

    @Insert("""
            insert ignore into region_question_like (question_id, user_id)
            values (#{questionId}, #{userId})
            """)
    int insertQuestionLike(@Param("questionId") Long questionId, @Param("userId") Long userId);

    @Delete("""
            delete from region_question_like
            where question_id = #{questionId}
              and user_id = #{userId}
            """)
    int deleteQuestionLike(@Param("questionId") Long questionId, @Param("userId") Long userId);
}
