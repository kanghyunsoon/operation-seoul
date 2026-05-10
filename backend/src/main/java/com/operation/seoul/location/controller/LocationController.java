package com.operation.seoul.location.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
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
    private final CurrentUserResolver currentUserResolver;

    /**
     * [기능: 맵 뷰 데이터 로딩 - 힌트 및 목적지 조회]
     */
    @GetMapping("/regions/{regionId}/missions")
    public ResponseEntity<List<MissionResponse>> getMissions(
            @PathVariable Long regionId,
            @RequestParam(defaultValue = "1") Long userId
    ) {
        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        List<MissionResponse> response = missionService.getMissionBoard(regionId, effectiveUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * [기능: 미션 장소 도착 여부 검증]
     */
    @PostMapping("/missions/{missionId}/arrive")
    public ResponseEntity<Boolean> checkArrival(
            @PathVariable Long missionId,
            @RequestBody LocationRequest request,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        boolean effectiveIsAdmin = currentUserResolver.resolveIsAdmin(isAdmin);
        boolean isArrived = locationValidationService.verifyUserArrival(
                missionId,
                request.getUserLat(),
                request.getUserLng(),
                effectiveIsAdmin
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
