package com.operation.seoul.location.controller;

import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/regions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;

    @GetMapping
    public ResponseEntity<List<Region>> getAllRegions() {
        return ResponseEntity.ok(regionRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRegionById(@PathVariable Long id) {
        Optional<Region> regionOpt = regionRepository.findById(id);

        // 💡 데이터가 없으면 서버를 터뜨리지 않고 404 상태코드와 메시지를 반환합니다.
        if (regionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "해당 섹터의 정보가 영구 파기되었거나 존재하지 않습니다. ID: " + id));
        }

        return ResponseEntity.ok(regionOpt.get());
    }
}