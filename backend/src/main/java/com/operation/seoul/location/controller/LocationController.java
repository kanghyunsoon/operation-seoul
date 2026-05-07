package com.operation.seoul.location.controller;

import com.operation.seoul.location.dto.MissionResponse;
import com.operation.seoul.location.service.LocationValidationService;
import com.operation.seoul.location.service.MissionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [Controller: 위치 및 미션 정보 인터페이스 계층]
 * 맵 뷰에서 핀을 그리기 위한 데이터 요청 및 유저의 실제 GPS 도달 여부를 판별합니다.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class LocationController {

    private final MissionService missionService;
    private final LocationValidationService locationValidationService;

    /**
     * [기능: 맵 뷰 데이터 로딩 - 힌트 및 목적지 조회]
     */
    @GetMapping("/regions/{regionId}/missions")
    public ResponseEntity<List<MissionResponse>> getMissions(
            @PathVariable Long regionId,
            @RequestParam(defaultValue = "1") Long userId
    ) {
        List<MissionResponse> response = missionService.getMissionBoard(regionId, userId);
        return ResponseEntity.ok(response);
    }

    /**[기능: 미션 장소 도착 여부 검증]
     - 수행 내용: 유저의 현재 좌표와 미션 목적지 좌표를 비교하여 반경 내 도착 여부 판정
     - 🚨 수정 사항: 관리자 여부(isAdmin) 파라미터 추가 및 서비스 전달 */
    @PostMapping("/missions/{missionId}/arrive")
    public ResponseEntity<Boolean> checkArrival(
            @PathVariable Long missionId,
            @RequestBody LocationRequest request,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) { // 🚨 관리자 파라미터 추가

        // LocationValidationService에 isAdmin 값을 함께 넘겨줍니다.
        // 서비스 내부 로직에 의해 isAdmin이 true이면 거리 계산 없이 무조건 true가 반환됩니다.
        boolean isArrived = locationValidationService.verifyUserArrival(
                missionId,
                request.getUserLat(),
                request.getUserLng(),
                isAdmin // 🚨 서비스로 전달
        );
        return ResponseEntity.ok(isArrived);
    }
}

/**
 * [DTO: 위치 정보 요청 규격]
 */
@Data
class LocationRequest {
    private Double userLat;
    private Double userLng;
}