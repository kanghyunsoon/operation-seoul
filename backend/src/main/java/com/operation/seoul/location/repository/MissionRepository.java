package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Mission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * [Repository: 미션 영속성 계층]
 * JPA Query Method 대신 MyBatis SQL을 명시해 과제 제약에 맞는 DB 접근을 수행합니다.
 */
@Mapper
public interface MissionRepository {

    /** 신규/기존 미션 저장을 한 곳에서 처리해 기존 서비스 코드의 `save` 호출을 유지합니다. */
    default Mission save(Mission mission) {
        if (mission.getId() == null) {
            insert(mission);
        } else {
            update(mission);
        }
        return mission;
    }

    default Optional<Mission> findById(Long id) {
        return Optional.ofNullable(findOneById(id));
    }

    /** 관리자 삭제 흐름에서 하위 미션 목록을 한 번에 지우기 위한 호환 메서드입니다. */
    default void deleteAll(List<Mission> missions) {
        if (missions == null || missions.isEmpty()) {
            return;
        }
        missions.stream()
                .map(Mission::getId)
                .filter(id -> id != null)
                .forEach(this::deleteById);
    }

    @Select("""
            select id, region_id, title, description, target_lat, target_lng,
                   radius_in_meters, vision_keyword, clue, answer_keyword,
                   chapter_id, is_final, real_story
            from mission
            where id = #{id}
            limit 1
            """)
    @Results(id = "MissionResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "regionId", column = "region_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "description", column = "description"),
            @Result(property = "targetLat", column = "target_lat"),
            @Result(property = "targetLng", column = "target_lng"),
            @Result(property = "radiusInMeters", column = "radius_in_meters"),
            @Result(property = "visionKeyword", column = "vision_keyword"),
            @Result(property = "clue", column = "clue"),
            @Result(property = "answerKeyword", column = "answer_keyword"),
            @Result(property = "chapterId", column = "chapter_id"),
            @Result(property = "finalMission", column = "is_final"),
            @Result(property = "realStory", column = "real_story")
    })
    Mission findOneById(Long id);

    /** 특정 지역에 귀속된 미션 목록을 지도 표시 순서가 흔들리지 않도록 id 오름차순으로 조회합니다. */
    @Select("""
            select id, region_id, title, description, target_lat, target_lng,
                   radius_in_meters, vision_keyword, clue, answer_keyword,
                   chapter_id, is_final, real_story
            from mission
            where region_id = #{regionId}
            order by id asc
            """)
    @ResultMap("MissionResultMap")
    List<Mission> findByRegionId(Long regionId);

    @Insert("""
            insert into mission (
                region_id, title, description, target_lat, target_lng,
                radius_in_meters, vision_keyword, clue, answer_keyword,
                chapter_id, is_final, real_story
            ) values (
                #{regionId}, #{title}, #{description}, #{targetLat}, #{targetLng},
                #{radiusInMeters}, #{visionKeyword}, #{clue}, #{answerKeyword},
                #{chapterId}, #{finalMission}, #{realStory}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Mission mission);

    @Update("""
            update mission
            set region_id = #{regionId},
                title = #{title},
                description = #{description},
                target_lat = #{targetLat},
                target_lng = #{targetLng},
                radius_in_meters = #{radiusInMeters},
                vision_keyword = #{visionKeyword},
                clue = #{clue},
                answer_keyword = #{answerKeyword},
                chapter_id = #{chapterId},
                is_final = #{finalMission},
                real_story = #{realStory}
            where id = #{id}
            """)
    int update(Mission mission);

    @Delete("delete from mission where id = #{id}")
    int deleteById(Long id);
}
