package com.operation.seoul.casefile.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.casefile.dto.CaseFileResponse;
import com.operation.seoul.casefile.service.CaseFileService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/episodes")
@RequiredArgsConstructor
public class CaseFileController {
    private final CurrentUserResolver currentUserResolver;
    private final CaseFileService caseFileService;

    @GetMapping("/{episodeId}/case-file")
    public ResponseEntity<ApiResponse<CaseFileResponse>> getCaseFile(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("미션 메모입니다.", caseFileService.getCaseFile(episodeId, currentUserResolver.requireCurrentUser())));
    }
}