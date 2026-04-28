package com.operation.seoul.location.controller;

import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [Controller: 작전 지역 정보 관리 센터]
 * - 용도: 본부 데이터베이스에 저장된 모든 작전 구역(Region) 정보를 요원 단말기(FE)에 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/regions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;

    /**
     * [기능: 전체 작전 지역 목록 송출]
     * - HomeView의 미션 그리드 구성을 위한 원천 데이터를 반환합니다.
     * @return DB 내 모든 Region 리스트
     */
    @GetMapping
    public ResponseEntity<List<Region>> getAllRegions() {
        // DB의 모든 지역(중명전, 광화문 등)을 조회하여 반환
        return ResponseEntity.ok(regionRepository.findAll());
    }

    /**
     * [기능: 특정 구역 상세 데이터 송출]
     * @param id 지역 식별 번호
     */
    @GetMapping("/{id}")
    public ResponseEntity<Region> getRegionById(@PathVariable Long id) {
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 섹터의 정보가 존재하지 않습니다. ID: " + id));
        return ResponseEntity.ok(region);
    }
}