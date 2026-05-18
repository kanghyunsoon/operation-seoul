package com.operation.seoul.game.repository;

import com.operation.seoul.game.domain.ClearReport;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClearReportRepository {

    @Select("""
            select id, user_id, mission_id, report, clue_explanations_json, created_at, updated_at
            from clear_report
            where user_id = #{userId}
              and mission_id = #{missionId}
            limit 1
            """)
    @Results(id = "ClearReportResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "missionId", column = "mission_id"),
            @Result(property = "report", column = "report"),
            @Result(property = "clueExplanationsJson", column = "clue_explanations_json"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    ClearReport findByUserIdAndMissionId(@Param("userId") Long userId, @Param("missionId") Long missionId);

    @Insert("""
            insert into clear_report (user_id, mission_id, report, clue_explanations_json)
            values (#{userId}, #{missionId}, #{report}, #{clueExplanationsJson})
            on duplicate key update
                report = values(report),
                clue_explanations_json = values(clue_explanations_json),
                updated_at = current_timestamp
            """)
    int upsert(ClearReport report);
}
