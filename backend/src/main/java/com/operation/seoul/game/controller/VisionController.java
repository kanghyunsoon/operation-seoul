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
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image) {

        // 🚨 팩트체크: 현재 백엔드에는 Google Cloud Vision API를 호출하여
        // 이미지에서 텍스트를 추출하는 진짜 로직이 없습니다!

        // 프론트엔드 UI 테스트를 위해 무조건 정답("붉은색 문")이 추출되었다고 가짜(Mock) 데이터를 넣습니다.
        String mockExtractedText = "여기에 붉은색 문이 있습니다.";

        // VisionAiService는 추출된 텍스트에 목표 키워드가 포함되었는지 판별합니다.
        boolean isSuccess = visionAiService.validateKeyword(missionId, mockExtractedText);

        if (isSuccess) {
            return ResponseEntity.ok(Map.of("success", true, "keyword", "붉은색 문"));
        } else {
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}