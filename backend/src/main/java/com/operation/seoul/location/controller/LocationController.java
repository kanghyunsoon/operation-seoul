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
 * 용도: 프론트엔드(Vue)의 HTTP 요청(GET, POST)을 가장 먼저 받아 Service로 넘김
 * 경로: 모든 API는 기본적으로 http://localhost:8080/api/v1 으로 시작
 */
@RestController
@RequestMapping("/api/v1") // 기본 경로 고정
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class LocationController {
    private final MissionService missionService;
    private final LocationValidationService locationValidationService;

    @GetMapping("/regions/{regionId}/missions")
    public List<Mission> getMissions(@PathVariable Long regionId) {
        return missionService.getMissionsByRegion(regionId);
    }

    @PostMapping("/missions/{missionId}/arrive")
    public ResponseEntity<Boolean> checkArrival(
            @PathVariable Long missionId, @RequestBody LocationRequest request) {
        boolean isArrived = locationValidationService.verifyUserArrival(missionId, request.getUserLat(), request.getUserLng());
        return ResponseEntity.ok(isArrived);
    }
}

@Data
class LocationRequest {
    private Double userLat;
    private Double userLng;
}