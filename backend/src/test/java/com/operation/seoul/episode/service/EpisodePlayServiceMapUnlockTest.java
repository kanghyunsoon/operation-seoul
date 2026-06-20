package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.casefile.repository.CaseFileRepository;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.MissionSpot;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import com.operation.seoul.episode.dto.EpisodeMapResponse;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.favorite.repository.EpisodeFavoriteRepository;
import com.operation.seoul.location.service.OperationAreaResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpisodePlayServiceMapUnlockTest {
    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final EpisodePlayService service = new EpisodePlayService(
            episodeRepository,
            mock(CaseFileRepository.class),
            mock(EpisodeFavoriteRepository.class),
            new ObjectMapper(),
            mock(OperationAreaResolver.class),
            mock(MinigameProofValidator.class),
            mock(MinigameRetryVariantFactory.class),
            mock(PuzzleAttemptGuard.class)
    );

    @Test
    void hidesFinalPlaceUntilAllInvestigationSpotsAreCompleted() {
        mockMapState(progressWithCompleted(List.of()));

        EpisodeMapResponse response = service.getMap(1L, user());

        assertFalse(response.getFinalDestinationUnlocked());
        assertEquals(9, response.getSpots().size());
        assertTrue(response.getSpots().stream().noneMatch(spot -> spot.isFinalPlace()));
        assertTrue(response.getSpots().stream().noneMatch(spot -> "FINAL".equals(spot.getPublicMarkerType())));
    }

    @Test
    void revealsFinalPlaceAsFinalMarkerAfterEightInvestigationSpotsAreCompleted() {
        mockMapState(progressWithCompleted(List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)));

        EpisodeMapResponse response = service.getMap(1L, user());

        assertTrue(response.getFinalDestinationUnlocked());
        assertEquals(10, response.getSpots().size());
        var finalMarker = response.getSpots().stream()
                .filter(spot -> spot.isFinalPlace())
                .findFirst()
                .orElseThrow();
        assertEquals(10L, finalMarker.getSpotId());
        assertEquals("FINAL", finalMarker.getPublicMarkerType());
    }

    private void mockMapState(UserEpisodeProgress progress) {
        when(episodeRepository.findEpisodeById(1L)).thenReturn(episode());
        when(episodeRepository.findProgress(7L, 1L)).thenReturn(progress);
        when(episodeRepository.findSpotsByEpisodeId(1L)).thenReturn(spots());
    }

    private Episode episode() {
        Episode episode = new Episode();
        episode.setId(1L);
        episode.setTitle("테스트 사건");
        episode.setStatus("PUBLISHED");
        return episode;
    }

    private User user() {
        return User.builder()
                .id(7L)
                .role("ROLE_USER")
                .build();
    }

    private UserEpisodeProgress progressWithCompleted(List<Long> completedIds) {
        UserEpisodeProgress progress = new UserEpisodeProgress();
        progress.setId(11L);
        progress.setUserId(7L);
        progress.setEpisodeId(1L);
        progress.setVisitedSpotIds("[]");
        progress.setCompletedSpotIds(completedIds.toString());
        progress.setCollectedAnswerClues("[]");
        progress.setCollectedDestinationClues("[]");
        progress.setCollectedStoryClues("[]");
        progress.setUnlockedSuspectIds("[]");
        progress.setClearedSuspectIds("[]");
        progress.setUnlockedEvidenceIds("[]");
        progress.setStatus("IN_PROGRESS");
        return progress;
    }

    private List<MissionSpot> spots() {
        List<MissionSpot> spots = new ArrayList<>();
        spots.add(spot(1L, "START", "START", "START", false));
        for (long id = 2L; id <= 9L; id++) {
            spots.add(spot(id, "ANSWER_HINT", "ANSWER_HINT", "ANSWER_HINT", false));
        }
        spots.add(spot(10L, "FINAL", "FINAL_PLACE", "ANSWER_HINT", true));
        return spots;
    }

    private MissionSpot spot(Long id, String markerType, String clueRole, String publicMarkerType, boolean finalPlace) {
        MissionSpot spot = new MissionSpot();
        spot.setId(id);
        spot.setEpisodeId(1L);
        spot.setPlaceName("장소 " + id);
        spot.setAddress("서울");
        spot.setLatitude(37.5 + id / 1000.0);
        spot.setLongitude(127.0 + id / 1000.0);
        spot.setMarkerType(markerType);
        spot.setClueRole(clueRole);
        spot.setPublicMarkerType(publicMarkerType);
        spot.setStoryText("현장 기록 " + id);
        spot.setArrivalRadius(80.0);
        spot.setFinalPlace(finalPlace);
        return spot;
    }
}
