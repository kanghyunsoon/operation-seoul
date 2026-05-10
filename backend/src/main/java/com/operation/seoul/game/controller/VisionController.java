package com.operation.seoul.game.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.game.service.VisionAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class VisionController {

    private final VisionAiService visionAiService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{missionId}/vision")
    public ResponseEntity<?> verifyVision(
            @PathVariable Long missionId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {

        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        boolean effectiveIsAdmin = currentUserResolver.resolveIsAdmin(isAdmin);
        return ResponseEntity.ok(visionAiService.verifyAndRecordMission(missionId, image, effectiveUserId, effectiveIsAdmin));
    }
}
