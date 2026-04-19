package com.operation.seoul.game.repository;

import com.operation.seoul.game.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [Repository: GameSession DB 접근]
 * 용도: 게임 세션 테이블에서 데이터를 생성, 조회, 수정합니다.
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    // 유저 ID와 미션 ID를 조합해서 "현재 이 유저가 진행 중인 세이브 파일"을 찾아옵니다.
    Optional<GameSession> findByUserIdAndMissionId(Long userId, Long missionId);
}