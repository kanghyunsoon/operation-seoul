package com.operation.seoul.game.controller;

import com.operation.seoul.game.service.MissionFactory;
import com.operation.seoul.game.service.TourApiService;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AdminMissionController {

    private final TourApiService tourApiService;
    private final MissionFactory missionFactory;
    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;

    /**
     * 1단계: 관리자 화면에서 특정 좌표 주변의 역사적/관광 목적지 후보 스캔
     */
    @GetMapping("/candidates")
    public ResponseEntity<?> getTourCandidates(@RequestParam double lat, @RequestParam double lng) {
        log.info("🔍 주변 관광지 스캔 시작... lat={}, lng={}", lat, lng);
        try {
            List<Map<String, String>> spots = tourApiService.getNearbyTouristSpots(lng, lat, 2000);
            if (spots.isEmpty()) {
                return ResponseEntity.badRequest().body("주변에 가용 가능한 작전지가 없습니다.");
            }
            return ResponseEntity.ok(spots);
        } catch (Exception e) {
            log.error("후보지 스캔 실패", e);
            return ResponseEntity.internalServerError().body("후보지 스캔 실패: " + e.getMessage());
        }
    }

    /**
     * 2단계: 선택된 장소를 최종 목적지로 삼아 카카오API + 제미나이 시나리오 생성 후 DB 저장
     */
    @PostMapping("/generate-selected")
    public ResponseEntity<?> generateFromSelectedSpot(@RequestBody Map<String, Object> spotData) {
        log.info("🎯 선택된 목적지 기반 작전 수립 개시: {}", spotData.get("title"));
        try {
            Map<String, Object> result = missionFactory.createAiMission(spotData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("🚨 작전 생성 실패", e);
            return ResponseEntity.internalServerError().body("작전 수립 실패: " + e.getMessage());
        }
    }

    /**
     * 3단계: 💡 관리자 권한으로 특정 작전(Region) 및 하위 미션 영구 삭제
     */
    @DeleteMapping("/regions/{regionId}")
    @Transactional
    public ResponseEntity<?> deleteRegion(@PathVariable Long regionId) {
        log.info("🗑️ 작전 파기 명령 수신. Region ID: {}", regionId);
        try {
            // 1. 해당 Region에 종속된 Mission들을 찾아 먼저 삭제 (무결성 유지)
            List<Mission> missionsToDelete = missionRepository.findAll().stream()
                    .filter(m -> m.getRegionId().equals(regionId))
                    .collect(Collectors.toList());
            missionRepository.deleteAll(missionsToDelete);

            // 2. 부모 Region 삭제
            regionRepository.deleteById(regionId);

            log.info("✅ 작전 데이터 영구 삭제 완료.");
            return ResponseEntity.ok(Map.of("message", "삭제 성공"));
        } catch (Exception e) {
            log.error("🚨 작전 삭제 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "삭제 실패: " + e.getMessage()));
        }
    }
}