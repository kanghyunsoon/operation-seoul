package com.operation.seoul.location.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.community.dto.RegionReviewSummary;
import com.operation.seoul.community.repository.RegionReviewRepository;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.dto.RegionCardResponse;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import com.operation.seoul.location.service.OperationAreaResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;
    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final RegionReviewRepository regionReviewRepository;
    private final CurrentUserResolver currentUserResolver;
    private final OperationAreaResolver operationAreaResolver;

    /** 디버그/관리 목적으로 DB에 저장된 Region 원본 목록을 반환합니다. */
    @GetMapping
    public ResponseEntity<List<Region>> getAllRegions() {
        return ResponseEntity.ok(regionRepository.findAll());
    }

    /**
     * 홈 화면 작전 카드 목록입니다.
     * 권역 코드로 지역을 필터링하고, 최종 미션 클리어 기록이 있으면 점수/시간/거리 요약을 붙입니다.
     */
    @GetMapping("/cards")
    public ResponseEntity<List<RegionCardResponse>> getRegionCards(
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "areaCode", defaultValue = OperationAreaResolver.DEFAULT_AREA_CODE) String areaCode) {

        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        String normalizedAreaCode = operationAreaResolver.normalizeAreaCode(areaCode);
        List<RegionCardResponse> cards = regionRepository.findCardsByAreaCode(normalizedAreaCode).stream()
                .map(region -> buildRegionCard(region, effectiveUserId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(cards);
    }

    /** 브리핑 화면에서 작전명과 배경 설명을 불러올 때 사용합니다. */
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

    @PostMapping("/{id}/like")
    public ResponseEntity<RegionCardResponse> toggleRegionLike(
            @PathVariable Long id,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        Region region = requireRegion(id);
        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        if (regionRepository.countLikeByRegionIdAndUserId(id, effectiveUserId) > 0) {
            regionRepository.deleteRegionLike(id, effectiveUserId);
        } else {
            regionRepository.insertRegionLike(id, effectiveUserId);
        }
        return ResponseEntity.ok(buildRegionCard(region, effectiveUserId));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<RegionCardResponse> toggleRegionFavorite(
            @PathVariable Long id,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        Region region = requireRegion(id);
        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        if (regionRepository.countFavoriteByRegionIdAndUserId(id, effectiveUserId) > 0) {
            regionRepository.deleteRegionFavorite(id, effectiveUserId);
        } else {
            regionRepository.insertRegionFavorite(id, effectiveUserId);
        }
        return ResponseEntity.ok(buildRegionCard(region, effectiveUserId));
    }

    /**
     * Region 엔티티를 홈 카드 DTO로 변환합니다.
     * 최종 미션의 GameSession이 CLEARED일 때만 클리어 기록을 노출합니다.
     */
    private RegionCardResponse buildRegionCard(Region region, Long userId) {
        Optional<Mission> finalMissionOpt = missionRepository.findByRegionId(region.getId()).stream()
                .filter(Mission::isFinal)
                .findFirst();

        RegionCardResponse.RegionCardResponseBuilder builder = RegionCardResponse.builder()
                .id(region.getId())
                .areaCode(operationAreaResolver.normalizeAreaCode(region.getAreaCode()))
                .name(region.getName())
                .description(region.getDescription())
                .periodCode(region.getPeriodCode())
                .themeCode(region.getThemeCode())
                .likeCount(regionRepository.countLikesByRegionId(region.getId()))
                .liked(regionRepository.countLikeByRegionIdAndUserId(region.getId(), userId) > 0)
                .favoriteCount(regionRepository.countFavoritesByRegionId(region.getId()))
                .favorited(regionRepository.countFavoriteByRegionIdAndUserId(region.getId(), userId) > 0)
                .createdAt(region.getCreatedAt() == null ? null : region.getCreatedAt().toString());

        RegionReviewSummary reviewSummary = regionReviewRepository.findSummaryByRegionId(region.getId());
        if (reviewSummary != null) {
            builder.averageRating(reviewSummary.getAverageRating() == null ? 0.0 : reviewSummary.getAverageRating())
                    .reviewCount(reviewSummary.getReviewCount() == null ? 0 : reviewSummary.getReviewCount());
        }

        if (finalMissionOpt.isEmpty()) {
            return builder.cleared(false).build();
        }

        Mission finalMission = finalMissionOpt.get();
        Optional<GameSession> clearedSessionOpt = gameSessionRepository.findByUserIdAndMissionId(userId, finalMission.getId())
                .filter(session -> "CLEARED".equals(session.getStatus()));

        builder.cleared(clearedSessionOpt.isPresent());

        clearedSessionOpt.ifPresent(session -> builder
                .finalMissionId(finalMission.getId())
                .answerKeyword(finalMission.getAnswerKeyword())
                .score(session.getScore())
                .elapsedSeconds(session.getElapsedSeconds())
                .routeDistanceMeters(session.getRouteDistanceMeters()));

        return builder.build();
    }

    private Region requireRegion(Long id) {
        return regionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "작전을 찾을 수 없습니다."));
    }
}
