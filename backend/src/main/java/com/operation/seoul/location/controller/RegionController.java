package com.operation.seoul.location.controller;

import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** [Controller: 지역 정보 인터페이스 계층]
 용도: 외부 시스템 또는 프론트엔드 앱의 지역 데이터 요청 처리
 특징: RESTful 설계 원칙에 따른 단일 자원 조회 기능 제공 */
@RestController
@RequestMapping("/api/v1/regions")
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 도메인 허용 설정
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;

    /**특정 작전 구역 상세 정보 조회
     @param id 조회하고자 하는 지역의 고유 식별 번호 (Path Variable)
     @return 성공 시 200 OK와 함께 Region 객체 반환, 실패 시 예외 메시지 송출
     기능 상세:
     1. 전달받은 ID를 기반으로 영속성 계층(Repository)에서 데이터 조회
     2. 데이터 부재 시 "해당 작전 구역이 존재하지 않습니다" 메시지와 함께 런타임 예외 발생
     3. 유효한 데이터 확보 시 ResponseEntity 표준 규격으로 데이터 패키징 후 반환 */
    @GetMapping("/{id}")
    public ResponseEntity<Region> getRegionById(@PathVariable Long id) {
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 작전 구역이 존재하지 않습니다. ID: " + id));
        return ResponseEntity.ok(region);
    }
}