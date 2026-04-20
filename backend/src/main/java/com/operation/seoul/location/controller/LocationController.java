package com.operation.seoul.location.controller;

import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.service.MissionService;
import com.operation.seoul.location.service.LocationValidationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [Controller: Location 모듈 진입점]
 * - 역할: 지도 기반 서비스 요청 처리 및 위치 검증 로직 매핑
 * - 베이스 경로: /api/v1
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 연동을 위한 교차 출처 자원 공유 허용
@RequiredArgsConstructor
public class LocationController {

    private final MissionService missionService;
    private final LocationValidationService locationValidationService;

    /**
     * [기능: 지역별 미션 리스트 조회]
     * - 수행 내용: 특정 지역 코드(regionId)를 기준으로 소속된 전체 미션 정보를 반환
     * - 매개 변수: Long regionId (지역 식별자)
     * - 반환 값: List<Mission> (미션 객체 리스트)
     */
    @GetMapping("/regions/{regionId}/missions")
    public List<Mission> getMissions(@PathVariable Long regionId) {
        return missionService.getMissionsByRegion(regionId);
    }

    /**
     * [기능: 미션 장소 도착 여부 검증]
     * - 수행 내용: 유저의 현재 좌표와 미션 목적지 좌표를 비교하여 반경 내 도착 여부 판별
     * - 매개 변수:
     * 1. Long missionId (검증 대상 미션 번호)
     * 2. LocationRequest request (유저의 현재 위도 및 경도 정보)
     * - 반환 값: ResponseEntity<Boolean> (도착 성공 시 true, 실패 시 false)
     */
    @PostMapping("/missions/{missionId}/arrive")
    public ResponseEntity<Boolean> checkArrival(
            @PathVariable Long missionId, @RequestBody LocationRequest request) {
        // LocationValidationService를 통해 위치 산술 검증 수행
        boolean isArrived = locationValidationService.verifyUserArrival(
                missionId,
                request.getUserLat(),
                request.getUserLng()
        );
        return ResponseEntity.ok(isArrived);
    }
}

/**
 * [DTO: 위치 정보 요청 규격]
 * - 용도: 프론트엔드에서 전송하는 유저의 GPS 좌표를 담는 객체
 */
@Data
class LocationRequest {
    /** 유저의 현재 위도 (Latitude) */
    private Double userLat;
    /** 유저의 현재 경도 (Longitude) */
    private Double userLng;
}