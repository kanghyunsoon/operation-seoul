package com.operation.seoul.game.repository;

import com.operation.seoul.game.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [Repository: GameSession 데이터 영속성 계층]
 * - 역할: 게임 진행 상태(GameSession) 엔티티에 대한 데이터베이스 액세스 제공
 * - 상속: JpaRepository (기본 CRUD 메서드 포함)
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    /**
     * [기능: 유저 및 미션별 활성 세션 조회]
     * - 수행 내용: 특정 유저가 특정 미션을 진행 중인지 확인하거나 기존 세이브 데이터를 불러옴
     * - 매개 변수:
     * 1. Long userId (유저 고유 식별자)
     * 2. Long missionId (미션 고유 식별자)
     * - 반환 값: Optional<GameSession> (조건에 맞는 세션이 존재할 경우 해당 객체 반환)
     * - 쿼리 설명: SELECT * FROM game_session WHERE user_id = ? AND mission_id = ?
     */
    Optional<GameSession> findByUserIdAndMissionId(Long userId, Long missionId);
}