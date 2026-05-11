package com.operation.seoul.location.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.game.domain.GameSession;
import com.operation.seoul.game.repository.GameSessionRepository;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.domain.Region;
import com.operation.seoul.location.dto.RegionCardResponse;
import com.operation.seoul.location.repository.MissionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<Region>> getAllRegions() {
        return ResponseEntity.ok(regionRepository.findAll());
    }

    @GetMapping("/cards")
    public ResponseEntity<List<RegionCardResponse>> getRegionCards(
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        Long effectiveUserId = currentUserResolver.resolveUserId(userId);
        List<RegionCardResponse> cards = regionRepository.findAll().stream()
                .map(region -> buildRegionCard(region, effectiveUserId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(cards);
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

    private RegionCardResponse buildRegionCard(Region region, Long userId) {
        Optional<Mission> finalMissionOpt = missionRepository.findByRegionId(region.getId()).stream()
                .filter(Mission::isFinal)
                .findFirst();

        RegionCardResponse.RegionCardResponseBuilder builder = RegionCardResponse.builder()
                .id(region.getId())
                .name(region.getName())
                .description(region.getDescription());

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
}
