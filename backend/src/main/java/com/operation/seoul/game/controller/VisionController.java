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

    /**
     * 과거 프론트 호출 경로와의 호환용 Vision endpoint입니다.
     * 신규 화면은 `/api/v1/sessions/{missionId}/vision`을 사용하므로 추후 하나로 통합할 수 있습니다.
     */
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
