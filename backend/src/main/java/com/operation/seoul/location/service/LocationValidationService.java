package com.operation.seoul.location.service;

import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [Service: GPS 검증 심판]
 * 용도: 유저의 GPS 좌표가 조작되지 않고 실제로 반경 안에 들어왔는지 수학적으로 검증
 * 호출: LocationController의 checkArrival() API에서 유저 좌표를 넘겨주며 호출
 */
@Service
@RequiredArgsConstructor
public class LocationValidationService {
    private final MissionRepository missionRepository;
    private static final double EARTH_RADIUS = 6371.0;

    public boolean verifyUserArrival(Long missionId, double userLat, double userLng) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("미션 없음: " + missionId));

        double targetLat = mission.getTargetLat();
        double targetLng = mission.getTargetLng();
        double radius = mission.getRadiusInMeters() != null ? mission.getRadiusInMeters() : 30.0;

        double dLat = Math.toRadians(targetLat - userLat);
        double dLng = Math.toRadians(targetLng - userLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(targetLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c * 1000;

        return distance <= radius;
    }
}