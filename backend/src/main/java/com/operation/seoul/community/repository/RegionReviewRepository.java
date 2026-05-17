package com.operation.seoul.community.repository;

import com.operation.seoul.community.domain.RegionReview;
import com.operation.seoul.community.dto.RegionReviewResponse;
import com.operation.seoul.community.dto.RegionReviewSummary;
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
public interface RegionReviewRepository {

    @Select("""
            select r.id, r.region_id, r.user_id, r.rating, r.content, r.created_at, r.updated_at
            from region_review r
            where r.id = #{id}
              and r.region_id = #{regionId}
            limit 1
            """)
    @Results(id = "RegionReviewResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "rating", column = "rating"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    RegionReview findByIdAndRegionId(@Param("id") Long id, @Param("regionId") Long regionId);

    @Select("""
            select r.id, r.region_id, r.user_id, r.rating, r.content, r.created_at, r.updated_at
            from region_review r
            where r.region_id = #{regionId}
              and r.user_id = #{userId}
            limit 1
            """)
    @ResultMap("RegionReviewResultMap")
    RegionReview findByRegionIdAndUserId(@Param("regionId") Long regionId, @Param("userId") Long userId);

    @Select("""
            select r.id,
                   r.region_id,
                   r.user_id,
                   u.nickname as author_nickname,
                   r.rating,
                   r.content,
                   r.created_at,
                   r.updated_at,
                   gs.elapsed_seconds as clear_elapsed_seconds,
                   gs.score as clear_score
            from region_review r
            join users u on u.id = r.user_id
            left join mission fm on fm.region_id = r.region_id and fm.is_final = true
            left join game_session gs on gs.user_id = r.user_id
                and gs.mission_id = fm.id
                and gs.status = 'CLEARED'
            where r.region_id = #{regionId}
            order by
                case when #{sort} = 'rating_desc' then r.rating end desc,
                case when #{sort} = 'rating_asc' then r.rating end asc,
                case when #{sort} = 'clear_time' then gs.elapsed_seconds end asc,
                r.created_at desc,
                r.id desc
            """)
    @Results(id = "RegionReviewResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "authorNickname", column = "author_nickname"),
            @Result(property = "rating", column = "rating"),
            @Result(property = "content", column = "content"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "clearElapsedSeconds", column = "clear_elapsed_seconds"),
            @Result(property = "clearScore", column = "clear_score")
    })
    List<RegionReviewResponse> findResponsesByRegionId(@Param("regionId") Long regionId, @Param("sort") String sort);

    @Select("""
            select region_id,
                   coalesce(avg(rating), 0) as average_rating,
                   count(*) as review_count
            from region_review
            where region_id = #{regionId}
            group by region_id
            """)
    @Results(id = "RegionReviewSummaryMap", value = {
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "averageRating", column = "average_rating"),
            @Result(property = "reviewCount", column = "review_count")
    })
    RegionReviewSummary findSummaryByRegionId(Long regionId);

    @Insert("""
            insert into region_review (region_id, user_id, rating, content)
            values (#{regionId}, #{userId}, #{rating}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RegionReview review);

    @Update("""
            update region_review
            set rating = #{rating},
                content = #{content},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int update(RegionReview review);

    @Delete("delete from region_review where id = #{id}")
    int deleteById(Long id);
}
