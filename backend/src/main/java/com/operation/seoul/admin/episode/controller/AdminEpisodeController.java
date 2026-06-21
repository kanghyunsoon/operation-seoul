package com.operation.seoul.admin.episode.controller;

import com.operation.seoul.admin.episode.dto.*;
import com.operation.seoul.admin.episode.service.AdminEpisodeAuditService;
import com.operation.seoul.admin.episode.service.AdminEpisodeGeminiService;
import com.operation.seoul.admin.episode.service.AdminEpisodeService;
import com.operation.seoul.admin.episode.service.KakaoLocalCandidateService;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/episodes")
@RequiredArgsConstructor
public class AdminEpisodeController {
    private final AdminEpisodeService adminEpisodeService;
    private final AdminEpisodeGeminiService adminEpisodeGeminiService;
    private final KakaoLocalCandidateService kakaoLocalCandidateService;
    private final AdminEpisodeAuditService adminEpisodeAuditService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminEpisodeListResponse>>> getEpisodes() {
        return ResponseEntity.ok(ApiResponse.ok("관리자 에피소드 목록입니다.", adminEpisodeService.getEpisodes()));
    }

    @GetMapping("/place-candidates")
    public ResponseEntity<ApiResponse<List<AdminPlaceCandidateResponse>>> getPlaceCandidates(
            @RequestParam(value = "areaCode", defaultValue = "seoul") String areaCode
    ) {
        return ResponseEntity.ok(ApiResponse.ok("TourAPI 장소 후보 목록입니다.", adminEpisodeService.getPlaceCandidates(areaCode)));
    }

    @GetMapping("/place-candidates/nearby")
    public ResponseEntity<ApiResponse<List<AdminPlaceCandidateResponse>>> getNearbyPlaceCandidates(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radius", defaultValue = "1500") Integer radius
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Kakao Local 주변 후보 목록입니다.",
                kakaoLocalCandidateService.getNearbyCandidates(latitude, longitude, radius)
        ));
    }

    @GetMapping("/{episodeId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> getEpisode(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("관리자 에피소드 상세입니다.", adminEpisodeService.getEpisode(episodeId)));
    }

    @GetMapping("/{episodeId}/audit-logs")
    public ResponseEntity<ApiResponse<List<AdminEpisodeAuditLogResponse>>> getEpisodeAuditLogs(
            @PathVariable Long episodeId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        adminEpisodeService.getEpisode(episodeId);
        return ResponseEntity.ok(ApiResponse.ok(
                "관리자 변경 이력입니다.",
                adminEpisodeAuditService.getEpisodeAuditLogs(episodeId, limit)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createEpisode(
            @RequestBody(required = false) AdminEpisodeUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.createEpisode(request);
        audit(response, "CREATE_EPISODE", "EPISODE", response.getId(), "새 미션 파일 초안을 생성했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("미션 파일 초안을 생성했습니다.", response));
    }

    @DeleteMapping("/{episodeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEpisode(@PathVariable Long episodeId) {
        AdminEpisodeDetailResponse existing = adminEpisodeService.getEpisode(episodeId);
        adminEpisodeService.deleteEpisode(episodeId);
        audit(existing, "DELETE_EPISODE", "EPISODE", episodeId, "미션 파일과 연결 데이터를 삭제했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("미션 파일을 삭제했습니다."));
    }

    @GetMapping("/{episodeId}/publish-readiness")
    public ResponseEntity<ApiResponse<AdminEpisodePublishReadinessResponse>> getPublishReadiness(
            @PathVariable Long episodeId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "공개 준비도 점검 결과입니다.",
                adminEpisodeService.getPublishReadiness(episodeId)
        ));
    }

    @PutMapping("/{episodeId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateEpisode(
            @PathVariable Long episodeId,
            @RequestBody AdminEpisodeUpdateRequest request
    ) {
        AdminEpisodeDetailResponse existing = adminEpisodeService.getEpisode(episodeId);
        AdminEpisodeDetailResponse response = adminEpisodeService.updateEpisode(episodeId, request);
        String action = statusAction(existing.getStatus(), response.getStatus());
        audit(response, action, "EPISODE", episodeId, statusSummary(action));
        return ResponseEntity.ok(ApiResponse.ok("에피소드 정보를 수정했습니다.", response));
    }

    @PostMapping("/{episodeId}/spots")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createSpot(
            @PathVariable Long episodeId,
            @RequestBody AdminSpotUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.createSpot(episodeId, request);
        audit(response, "CREATE_SPOT", "SPOT", newestSpotId(response), "조사 장소를 추가했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("조사 장소를 추가했습니다.", response));
    }

    @DeleteMapping("/{episodeId}/spots/{spotId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteSpot(
            @PathVariable Long episodeId,
            @PathVariable Long spotId
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.deleteSpot(episodeId, spotId);
        audit(response, "DELETE_SPOT", "SPOT", spotId, "조사 장소와 연결 퍼즐을 삭제했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("조사 장소를 삭제했습니다.", response));
    }

    @PutMapping("/{episodeId}/spots/{spotId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateSpot(
            @PathVariable Long episodeId,
            @PathVariable Long spotId,
            @RequestBody AdminSpotUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.updateSpot(episodeId, spotId, request);
        audit(response, "UPDATE_SPOT", "SPOT", spotId, "장소 좌표, 공개 역할 또는 현장 검수 정보를 수정했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("조사 장소 정보를 수정했습니다.", response));
    }

    @PutMapping("/{episodeId}/puzzles/{puzzleId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updatePuzzle(
            @PathVariable Long episodeId,
            @PathVariable Long puzzleId,
            @RequestBody AdminPuzzleUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.updatePuzzle(episodeId, puzzleId, request);
        audit(response, "UPDATE_PUZZLE", "PUZZLE", puzzleId, "퍼즐, 힌트 또는 보상 설정을 수정했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("퍼즐 정보를 수정했습니다.", response));
    }

    @PostMapping("/{episodeId}/suspects")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createSuspect(
            @PathVariable Long episodeId,
            @RequestBody AdminSuspectUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.createSuspect(episodeId, request);
        audit(response, "CREATE_SUSPECT", "SUSPECT", newestSuspectId(response), "용의자 카드를 추가했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("용의자 카드를 추가했습니다.", response));
    }

    @PutMapping("/{episodeId}/suspects/{suspectId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateSuspect(
            @PathVariable Long episodeId,
            @PathVariable Long suspectId,
            @RequestBody AdminSuspectUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.updateSuspect(episodeId, suspectId, request);
        audit(response, "UPDATE_SUSPECT", "SUSPECT", suspectId, "용의자 카드와 이미지 프롬프트를 수정했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("용의자 카드를 수정했습니다.", response));
    }

    @DeleteMapping("/{episodeId}/suspects/{suspectId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteSuspect(
            @PathVariable Long episodeId,
            @PathVariable Long suspectId
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.deleteSuspect(episodeId, suspectId);
        audit(response, "DELETE_SUSPECT", "SUSPECT", suspectId, "용의자 카드를 삭제했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("용의자 카드를 삭제했습니다.", response));
    }

    @PostMapping("/{episodeId}/evidences")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createEvidence(
            @PathVariable Long episodeId,
            @RequestBody AdminEvidenceUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.createEvidence(episodeId, request);
        audit(response, "CREATE_EVIDENCE", "EVIDENCE", newestEvidenceId(response), "증거 카드를 추가했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("증거 카드를 추가했습니다.", response));
    }

    @PutMapping("/{episodeId}/evidences/{evidenceId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateEvidence(
            @PathVariable Long episodeId,
            @PathVariable Long evidenceId,
            @RequestBody AdminEvidenceUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.updateEvidence(episodeId, evidenceId, request);
        audit(response, "UPDATE_EVIDENCE", "EVIDENCE", evidenceId, "증거 카드와 이미지 프롬프트를 수정했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("증거 카드를 수정했습니다.", response));
    }

    @DeleteMapping("/{episodeId}/evidences/{evidenceId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteEvidence(
            @PathVariable Long episodeId,
            @PathVariable Long evidenceId
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.deleteEvidence(episodeId, evidenceId);
        audit(response, "DELETE_EVIDENCE", "EVIDENCE", evidenceId, "증거 카드를 삭제했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("증거 카드를 삭제했습니다.", response));
    }

    @PutMapping("/{episodeId}/partner-rewards/{rewardId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updatePartnerReward(
            @PathVariable Long episodeId,
            @PathVariable Long rewardId,
            @RequestBody AdminPartnerRewardUpdateRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.updatePartnerReward(episodeId, rewardId, request);
        audit(response, "UPDATE_PARTNER_REWARD", "PARTNER_REWARD", rewardId, "예정 리워드 정보를 수정했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("예정 리워드를 수정했습니다.", response));
    }

    @PostMapping("/{episodeId}/reward-payload/validate")
    public ResponseEntity<ApiResponse<AdminRewardPayloadValidationResponse>> validateRewardPayload(
            @PathVariable Long episodeId,
            @RequestBody AdminRewardPayloadValidationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "reward_payload 검증 결과입니다.",
                adminEpisodeService.validateRewardPayload(episodeId, request)
        ));
    }

    @PostMapping("/ai-draft")
    public ResponseEntity<ApiResponse<AiEpisodeDraftResponse>> createAiDraft(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("규칙 기반 AI 초안을 생성했습니다.", adminEpisodeService.createAiDraft(request)));
    }

    @PostMapping("/ai-draft/enrich-site-data")
    public ResponseEntity<ApiResponse<AiEpisodeDraftRequest>> enrichSiteData(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "현장 근거 보강을 완료했습니다. 운영 공개 전 실제 현장 검수는 별도로 필요합니다.",
                adminEpisodeService.enrichSiteData(request)
        ));
    }

    @PostMapping("/ai-draft/gemini")
    public ResponseEntity<ApiResponse<AiEpisodeDraftResponse>> createGeminiDraft(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "범죄 미스터리 에피소드 초안을 생성했습니다.",
                adminEpisodeGeminiService.createGeminiDraft(request)
        ));
    }

    @PostMapping("/ai-draft/plan")
    public ResponseEntity<ApiResponse<AiEpisodePlanResponse>> createGeminiPlan(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Gemini가 에피소드 장르와 최종 정답 키워드를 제안했습니다.",
                adminEpisodeGeminiService.createAnswerPlan(request)
        ));
    }

    @PostMapping("/ai-draft/validate")
    public ResponseEntity<ApiResponse<AiEpisodeDraftValidationResponse>> validateAiDraft(
            @RequestBody AiEpisodeDraftValidationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "AI 에피소드 초안 검증 결과입니다.",
                adminEpisodeGeminiService.validateDraft(request)
        ));
    }

    @PostMapping("/ai-draft/save")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> saveAiDraft(
            @RequestBody AiEpisodeDraftSaveRequest request
    ) {
        AdminEpisodeDetailResponse response = adminEpisodeService.saveAiDraft(request);
        audit(response, "SAVE_AI_DRAFT", "EPISODE", response.getId(), "AI 초안을 DRAFT 미션 파일로 저장했습니다.");
        return ResponseEntity.ok(ApiResponse.ok("AI 에피소드 초안을 DRAFT로 저장했습니다.", response));
    }

    private void audit(
            AdminEpisodeDetailResponse episode,
            String action,
            String targetType,
            Long targetId,
            String summary
    ) {
        User actor = currentUserResolver.requireCurrentUser();
        adminEpisodeAuditService.record(
                actor,
                episode == null ? null : episode.getId(),
                episode == null ? null : episode.getTitle(),
                action,
                targetType,
                targetId,
                summary
        );
    }

    private String statusAction(String before, String after) {
        if (!"PUBLISHED".equals(before) && "PUBLISHED".equals(after)) return "PUBLISH_EPISODE";
        if (!"ARCHIVED".equals(before) && "ARCHIVED".equals(after)) return "ARCHIVE_EPISODE";
        if (!"DRAFT".equals(before) && "DRAFT".equals(after)) return "REOPEN_EPISODE";
        return "UPDATE_EPISODE";
    }

    private String statusSummary(String action) {
        return switch (action) {
            case "PUBLISH_EPISODE" -> "공개 준비도 검증 후 미션 파일을 게시했습니다.";
            case "ARCHIVE_EPISODE" -> "미션 파일을 보관 상태로 전환했습니다.";
            case "REOPEN_EPISODE" -> "미션 파일을 DRAFT 상태로 되돌렸습니다.";
            default -> "에피소드 핵심 정보와 운영 설정을 수정했습니다.";
        };
    }

    private Long newestSpotId(AdminEpisodeDetailResponse response) {
        return response.getSpots().stream()
                .map(AdminEpisodeDetailResponse.Spot::getSpotId)
                .max(Long::compareTo)
                .orElse(null);
    }

    private Long newestSuspectId(AdminEpisodeDetailResponse response) {
        return response.getSuspects().stream()
                .map(AdminEpisodeDetailResponse.Suspect::getSuspectId)
                .max(Long::compareTo)
                .orElse(null);
    }

    private Long newestEvidenceId(AdminEpisodeDetailResponse response) {
        return response.getEvidences().stream()
                .map(AdminEpisodeDetailResponse.Evidence::getEvidenceId)
                .max(Long::compareTo)
                .orElse(null);
    }
}
