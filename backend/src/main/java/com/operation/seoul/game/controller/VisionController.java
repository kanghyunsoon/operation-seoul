package com.operation.seoul.game.controller;

import com.operation.seoul.game.service.VisionAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class VisionController {

    private final VisionAiService visionAiService;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(@PathVariable Long missionId, @RequestParam("image") MultipartFile image) {
        // 🚨 가짜(Mock) 데이터를 제거하고 실제 Vision API 서비스로 이미지를 넘깁니다.
        boolean isSuccess = visionAiService.validateKeyword(missionId, image);

        if (isSuccess) {
            return ResponseEntity.ok(Map.of("success", true, "keyword", "판독 성공"));
        } else {
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}