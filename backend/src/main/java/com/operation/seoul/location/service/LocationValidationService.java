package com.operation.seoul.location.service;

import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [Service: 위치 정보 수학적 검증 계층]
 * 용도: 사용자 단말기에서 수집된 GPS 데이터의 유효성 및 미션 장소 도달 여부 판정
 * 특징: 하버사인 공식(Haversine Formula)을 통한 정밀 거리 연산 수행
 */
@Service
@RequiredArgsConstructor
public class LocationValidationService {

    private final MissionRepository missionRepository;

    /**
     * 지구 평균 반지름 (단위: km)
     * 거리 연산을 위한 상수 데이터
     */
    private static final double EARTH_RADIUS = 6371.0;

    /**
     * 사용자 미션 장소 도착 여부 최종 검증
     * * @param missionId 검증 대상 미션 식별자
     * @param userLat   사용자 실시간 위도
     * @param userLng   사용자 실시간 경도
     * @return 반경 내 존재 시 true, 이외 false 반환
     */
    public boolean verifyUserArrival(Long missionId, double userLat, double userLng) {
        // 1. 대상 미션 데이터 인출 및 예외 처리
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("미션 없음: " + missionId));

        // 2. 미션 설정 좌표 및 유효 반경 설정
        double targetLat = mission.getTargetLat();
        double targetLng = mission.getTargetLng();
        // 반경 미설정 시 기본값 30.0m 적용
        double radius = mission.getRadiusInMeters() != null ? mission.getRadiusInMeters() : 30.0;

        // 3. 하버사인 공식을 이용한 거리 계산 프로세스
        // 위도/경도 차이 산출 및 라디안 변환
        double dLat = Math.toRadians(targetLat - userLat);
        double dLng = Math.toRadians(targetLng - userLng);

        // 구면 삼각법 기반 사이 거리 산출
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(targetLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 최종 직선 거리 산출 (단위: 미터)
        double distance = EARTH_RADIUS * c * 1000;

        // 4. 허용 반경 내 진입 여부 판정 결과 반환
        return distance <= radius;
    }
}