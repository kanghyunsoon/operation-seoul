package com.operation.seoul.location.service;

import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.dto.MissionResponse;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [Service: 미션 및 힌트 해금 비즈니스 로직]
 * 특정 지역의 단서(미션) 목록을 조회하고, 유저의 진행 상태에 따라 최종 목적지 해금 여부를 결정합니다.
 */
@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;

    /**
     * 유저의 진행 상황을 반영한 지역별 미션(힌트) 목록 조회
     * * @param regionId 조회할 지역 ID
     * @param userId 요청을 보낸 유저 ID (인증 토큰에서 추출)
     * @return 해금 및 마스킹 처리가 완료된 MissionResponse 리스트
     */
    public List<MissionResponse> getMissionBoard(Long regionId, Long userId) {
        // 1. 해당 지역(챕터)의 모든 미션(힌트 + 최종목적지) 조회
        List<Mission> missions = missionRepository.findByRegionId(regionId);

        // 2. 유저가 이 지역에서 완전히 클리어(CLEARED)한 서브 힌트(isFinal = false)의 개수를 계산
        long clearedHintsCount = missions.stream()
                .filter(m -> !m.isFinal()) // 최종 목적지가 아닌 서브 힌트만 필터링
                .filter(m -> isMissionClearedByUser(m.getId(), userId)) // 유저가 클리어했는지 확인
                .count();

        // 3. 서브 힌트가 3개 이상 클리어되었다면 최종 목적지 해금 처리
        boolean isFinalUnlocked = (clearedHintsCount >= 3);

        // 4. Mission 엔티티를 프론트엔드 전달용 DTO로 변환하여 반환
        return missions.stream().map(mission -> {
            String status = getUserSessionStatus(mission.getId(), userId);

            // 서브 힌트는 기본적으로 항상 해금(true), 최종 목적지는 조건부 해금(isFinalUnlocked)
            boolean unlocked = !mission.isFinal() || isFinalUnlocked;

            return MissionResponse.of(mission, status, unlocked);
        }).collect(Collectors.toList());
    }

    /**
     * 내부 헬퍼 메서드: 특정 미션에 대한 유저의 상태값을 반환 (없으면 null)
     */
    private String getUserSessionStatus(Long missionId, Long userId) {
        return gameSessionRepository.findByUserIdAndMissionId(userId, missionId)
                .map(GameSession::getStatus)
                .orElse(null); // 한 번도 시도하지 않은 미션은 상태값이 없음
    }

    /**
     * 내부 헬퍼 메서드: 유저가 해당 미션을 완전히 클리어(CLEARED) 했는지 검증
     */
    private boolean isMissionClearedByUser(Long missionId, Long userId) {
        return "CLEARED".equals(getUserSessionStatus(missionId, userId));
    }
}