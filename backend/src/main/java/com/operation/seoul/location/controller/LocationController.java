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
     * 유저의 진행 상태를 기반으로, 현재 지역에 그려야 할 마커(미션) 정보를 반환합니다.
     * * @param regionId 지역 식별자
     * @param userId 유저 식별자 (임시로 헤더나 파라미터로 받음. 추후 JWT Security 파싱으로 교체 권장)
     * @return 힌트 3개 + (조건 만족 시) 최종 목적지가 포함된 DTO 리스트
     */
    @GetMapping("/regions/{regionId}/missions")
    public ResponseEntity<List<MissionResponse>> getMissions(
            @PathVariable Long regionId,
            @RequestParam(defaultValue = "1") Long userId // TODO: 추후 @AuthenticationPrincipal 로 토큰에서 추출
    ) {
        // 비즈니스 로직 호출: 유저의 힌트 수집 상태에 따라 해금/마스킹된 데이터 반환
        List<MissionResponse> response = missionService.getMissionBoard(regionId, userId);
        return ResponseEntity.ok(response);
    }

    // ... (기존 checkArrival 로직 유지) ...
}