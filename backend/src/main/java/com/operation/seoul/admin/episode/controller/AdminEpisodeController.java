package com.operation.seoul.admin.episode.controller;

import com.operation.seoul.admin.episode.dto.AdminEpisodeDetailResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodeListResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodePublishReadinessResponse;
import com.operation.seoul.admin.episode.dto.AdminEpisodeUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminEvidenceUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminPartnerRewardUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminPlaceCandidateResponse;
import com.operation.seoul.admin.episode.dto.AdminPuzzleUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminRewardPayloadValidationRequest;
import com.operation.seoul.admin.episode.dto.AdminRewardPayloadValidationResponse;
import com.operation.seoul.admin.episode.dto.AdminSpotUpdateRequest;
import com.operation.seoul.admin.episode.dto.AdminSuspectUpdateRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftSaveRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.service.AdminEpisodeGeminiService;
import com.operation.seoul.admin.episode.service.AdminEpisodeService;
import com.operation.seoul.admin.episode.service.KakaoLocalCandidateService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/episodes")
@RequiredArgsConstructor
public class AdminEpisodeController {
    private final AdminEpisodeService adminEpisodeService;
    private final AdminEpisodeGeminiService adminEpisodeGeminiService;
    private final KakaoLocalCandidateService kakaoLocalCandidateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminEpisodeListResponse>>> getEpisodes() {
        return ResponseEntity.ok(ApiResponse.ok("관리자 에피소드 목록입니다.", adminEpisodeService.getEpisodes()));
    }

    @GetMapping("/place-candidates")
    public ResponseEntity<ApiResponse<List<AdminPlaceCandidateResponse>>> getPlaceCandidates(
            @RequestParam(value = "areaCode", defaultValue = "seoul") String areaCode) {
        return ResponseEntity.ok(ApiResponse.ok("TourAPI 장소 후보 목록입니다.", adminEpisodeService.getPlaceCandidates(areaCode)));
    }


    @GetMapping("/place-candidates/nearby")
    public ResponseEntity<ApiResponse<List<AdminPlaceCandidateResponse>>> getNearbyPlaceCandidates(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radius", defaultValue = "1500") Integer radius) {
        return ResponseEntity.ok(ApiResponse.ok("Kakao Local nearby place candidates.", kakaoLocalCandidateService.getNearbyCandidates(latitude, longitude, radius)));
    }
    @GetMapping("/{episodeId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> getEpisode(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("관리자 에피소드 상세입니다.", adminEpisodeService.getEpisode(episodeId)));
    }


    @GetMapping("/{episodeId}/publish-readiness")
    public ResponseEntity<ApiResponse<AdminEpisodePublishReadinessResponse>> getPublishReadiness(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok("Episode publish readiness result.", adminEpisodeService.getPublishReadiness(episodeId)));
    }
    @PutMapping("/{episodeId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateEpisode(
            @PathVariable Long episodeId,
            @RequestBody AdminEpisodeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("에피소드 정보가 수정되었습니다.", adminEpisodeService.updateEpisode(episodeId, request)));
    }


    @PostMapping("/{episodeId}/spots")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createSpot(
            @PathVariable Long episodeId,
            @RequestBody AdminSpotUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Spot has been added.", adminEpisodeService.createSpot(episodeId, request)));
    }

    @DeleteMapping("/{episodeId}/spots/{spotId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteSpot(
            @PathVariable Long episodeId,
            @PathVariable Long spotId) {
        return ResponseEntity.ok(ApiResponse.ok("Spot has been deleted.", adminEpisodeService.deleteSpot(episodeId, spotId)));
    }

    @PostMapping("/{episodeId}/suspects")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createSuspect(
            @PathVariable Long episodeId,
            @RequestBody AdminSuspectUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Suspect card has been added.", adminEpisodeService.createSuspect(episodeId, request)));
    }

    @DeleteMapping("/{episodeId}/suspects/{suspectId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteSuspect(
            @PathVariable Long episodeId,
            @PathVariable Long suspectId) {
        return ResponseEntity.ok(ApiResponse.ok("Suspect card has been deleted.", adminEpisodeService.deleteSuspect(episodeId, suspectId)));
    }

    @PostMapping("/{episodeId}/evidences")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> createEvidence(
            @PathVariable Long episodeId,
            @RequestBody AdminEvidenceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Evidence card has been added.", adminEpisodeService.createEvidence(episodeId, request)));
    }

    @DeleteMapping("/{episodeId}/evidences/{evidenceId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> deleteEvidence(
            @PathVariable Long episodeId,
            @PathVariable Long evidenceId) {
        return ResponseEntity.ok(ApiResponse.ok("Evidence card has been deleted.", adminEpisodeService.deleteEvidence(episodeId, evidenceId)));
    }
    @PutMapping("/{episodeId}/spots/{spotId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateSpot(
            @PathVariable Long episodeId,
            @PathVariable Long spotId,
            @RequestBody AdminSpotUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("장소 정보가 수정되었습니다.", adminEpisodeService.updateSpot(episodeId, spotId, request)));
    }

    @PutMapping("/{episodeId}/puzzles/{puzzleId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updatePuzzle(
            @PathVariable Long episodeId,
            @PathVariable Long puzzleId,
            @RequestBody AdminPuzzleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("퍼즐 정보가 수정되었습니다.", adminEpisodeService.updatePuzzle(episodeId, puzzleId, request)));
    }

    @PutMapping("/{episodeId}/suspects/{suspectId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateSuspect(
            @PathVariable Long episodeId,
            @PathVariable Long suspectId,
            @RequestBody AdminSuspectUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("용의자 카드가 수정되었습니다.", adminEpisodeService.updateSuspect(episodeId, suspectId, request)));
    }

    @PutMapping("/{episodeId}/evidences/{evidenceId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updateEvidence(
            @PathVariable Long episodeId,
            @PathVariable Long evidenceId,
            @RequestBody AdminEvidenceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("증거 카드가 수정되었습니다.", adminEpisodeService.updateEvidence(episodeId, evidenceId, request)));
    }

    @PutMapping("/{episodeId}/partner-rewards/{rewardId}")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> updatePartnerReward(
            @PathVariable Long episodeId,
            @PathVariable Long rewardId,
            @RequestBody AdminPartnerRewardUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("리워드 placeholder가 수정되었습니다.", adminEpisodeService.updatePartnerReward(episodeId, rewardId, request)));
    }

    @PostMapping("/{episodeId}/reward-payload/validate")
    public ResponseEntity<ApiResponse<AdminRewardPayloadValidationResponse>> validateRewardPayload(
            @PathVariable Long episodeId,
            @RequestBody AdminRewardPayloadValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("reward_payload 검증 결과입니다.", adminEpisodeService.validateRewardPayload(episodeId, request)));
    }

    @PostMapping("/ai-draft")
    public ResponseEntity<ApiResponse<AiEpisodeDraftResponse>> createAiDraft(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("AI 에피소드 초안이 생성되었습니다.", adminEpisodeService.createAiDraft(request)));
    }


    @PostMapping("/ai-draft/gemini")
    public ResponseEntity<ApiResponse<AiEpisodeDraftResponse>> createGeminiDraft(@RequestBody AiEpisodeDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Gemini episode draft has been created. Review it before saving.", adminEpisodeGeminiService.createGeminiDraft(request)));
    }

    @PostMapping("/ai-draft/validate")
    public ResponseEntity<ApiResponse<AiEpisodeDraftValidationResponse>> validateAiDraft(@RequestBody AiEpisodeDraftValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("AI episode draft validation result.", adminEpisodeGeminiService.validateDraft(request)));
    }
    @PostMapping("/ai-draft/save")
    public ResponseEntity<ApiResponse<AdminEpisodeDetailResponse>> saveAiDraft(@RequestBody AiEpisodeDraftSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("AI 에피소드 초안이 DRAFT로 저장되었습니다.", adminEpisodeService.saveAiDraft(request)));
    }
}
