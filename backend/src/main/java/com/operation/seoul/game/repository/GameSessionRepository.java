package com.operation.seoul.game.repository;

import com.operation.seoul.game.domain.GameSession;
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

/**
 * [Repository: GameSession 데이터 영속성 계층]
 * JPA를 사용하지 않고 MyBatis Mapper SQL로 게임 진행 상태를 저장/조회합니다.
 */
@Mapper
public interface GameSessionRepository {

    /** 신규/기존 세션 저장을 한 곳에서 처리해 기존 서비스 코드의 `save` 호출을 유지합니다. */
    default GameSession save(GameSession session) {
        if (session.getId() == null) {
            insert(session);
        } else {
            update(session);
        }
        return session;
    }

    /** 특정 유저가 특정 미션을 진행 중인지 확인하거나 기존 세이브 데이터를 불러옵니다. */
    default Optional<GameSession> findByUserIdAndMissionId(Long userId, Long missionId) {
        return Optional.ofNullable(findOneByUserIdAndMissionId(userId, missionId));
    }

    @Select("""
            select id, user_id, mission_id, status, extracted_log, started_at,
                   cleared_at, elapsed_seconds, route_distance_meters, score
            from game_session
            where user_id = #{userId}
              and mission_id = #{missionId}
            limit 1
            """)
    @Results(id = "GameSessionResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "missionId", column = "mission_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "extractedLog", column = "extracted_log"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "clearedAt", column = "cleared_at"),
            @Result(property = "elapsedSeconds", column = "elapsed_seconds"),
            @Result(property = "routeDistanceMeters", column = "route_distance_meters"),
            @Result(property = "score", column = "score")
    })
    GameSession findOneByUserIdAndMissionId(@Param("userId") Long userId, @Param("missionId") Long missionId);

    @Select("""
            select id, user_id, mission_id, status, extracted_log, started_at,
                   cleared_at, elapsed_seconds, route_distance_meters, score
            from game_session
            where id = #{id}
            limit 1
            """)
    @ResultMap("GameSessionResultMap")
    GameSession findOneById(Long id);

    default Optional<GameSession> findById(Long id) {
        return Optional.ofNullable(findOneById(id));
    }

    @Insert("""
            insert into game_session (
                user_id, mission_id, status, extracted_log, started_at,
                cleared_at, elapsed_seconds, route_distance_meters, score
            ) values (
                #{userId}, #{missionId}, #{status}, #{extractedLog}, #{startedAt},
                #{clearedAt}, #{elapsedSeconds}, #{routeDistanceMeters}, #{score}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GameSession session);

    @Update("""
            update game_session
            set user_id = #{userId},
                mission_id = #{missionId},
                status = #{status},
                extracted_log = #{extractedLog},
                started_at = #{startedAt},
                cleared_at = #{clearedAt},
                elapsed_seconds = #{elapsedSeconds},
                route_distance_meters = #{routeDistanceMeters},
                score = #{score}
            where id = #{id}
            """)
    int update(GameSession session);
}
