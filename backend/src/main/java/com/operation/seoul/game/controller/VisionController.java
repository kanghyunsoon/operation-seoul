package com.operation.seoul.game.controller;

import com.operation.seoul.game.service.VisionAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/missions")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class VisionController {

    private final VisionAiService visionAiService;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        return ResponseEntity.ok(visionAiService.verifyAndRecordMission(missionId, image, userId, isAdmin));
    }
}
