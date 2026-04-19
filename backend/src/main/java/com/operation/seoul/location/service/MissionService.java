package com.operation.seoul.location.service;

import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [Service: 미션 데이터 관리 반장]
 * 용도: DB에서 단순히 데이터를 꺼내서 컨트롤러로 전달하는 역할
 * 호출: LocationController에서 지도를 그릴 때 호출
 */
@Service
@RequiredArgsConstructor
public class MissionService {
    private final MissionRepository missionRepository;

    // 컨트롤러에서 요청한 지역 ID로 미션 목록을 가져옴
    public List<Mission> getMissionsByRegion(Long regionId) {
        return missionRepository.findByRegionId(regionId);
    }
}