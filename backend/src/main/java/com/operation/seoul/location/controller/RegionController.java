package com.operation.seoul.location.controller; // 패키지명 확인!

import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/regions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;

    @GetMapping("/{id}")
    public ResponseEntity<Region> getRegionById(@PathVariable Long id) {
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 작전 구역이 존재하지 않습니다. ID: " + id));
        return ResponseEntity.ok(region);
    }
}