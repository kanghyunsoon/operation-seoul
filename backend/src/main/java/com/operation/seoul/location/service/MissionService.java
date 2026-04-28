package com.operation.seoul.location.service;

import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**[Service: 미션 데이터 관리 및 비즈니스 인터페이스]
 용도: 미션 관련 데이터 조회 요청 처리 및 도메인 로직 실행
 특징: 생성자 주입(RequiredArgsConstructor)을 통한 Repository 의존성 관리 */
@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;

    /**특정 지역 소속 미션 리스트 조회
     @param regionId 조회하고자 하는 지역의 고유 식별 번호
     @return 해당 지역 내 배치된 Mission 엔티티 객체 리스트
     기능 상세:
     1. 컨트롤러로부터 전달받은 지역 ID를 검증 및 Repository 계층으로 전달
     2. 검색된 미션 데이터를 리스트 형태로 반환하여 지도 UI 구성의 원천 데이터로 사용
     3. 호출 위치: 주로 LocationController 내 지도 데이터 로딩 API에서 참조     */
    public List<Mission> getMissionsByRegion(Long regionId) {
        return missionRepository.findByRegionId(regionId);
    }

    public Mission getMissionById(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 작전 정보가 없습니다. ID: " + missionId));
    }
}