package com.operation.seoul.casefile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.casefile.domain.CaseEvidence;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.dto.CaseFileResponse;
import com.operation.seoul.casefile.repository.CaseFileRepository;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseFileServiceTest {
    private final CaseFileRepository repository = mock(CaseFileRepository.class);
    private final CaseFileService service = new CaseFileService(repository, new ObjectMapper());

    @Test
    void suspectRelatedClueCountIgnoresLockedEvidence() {
        Episode episode = new Episode();
        episode.setId(1L);
        episode.setTitle("테스트 사건");
        episode.setStatus("PUBLISHED");
        when(repository.findEpisode(1L)).thenReturn(episode);

        UserEpisodeProgress progress = new UserEpisodeProgress();
        progress.setUserId(7L);
        progress.setEpisodeId(1L);
        progress.setCompletedSpotIds("[100]");
        progress.setUnlockedEvidenceIds("[22]");
        progress.setStatus("IN_PROGRESS");
        when(repository.findProgress(7L, 1L)).thenReturn(progress);
        when(repository.findStartSpotId(1L)).thenReturn(100L);

        when(repository.findSuspects(1L)).thenReturn(List.of(suspect(1L), suspect(2L)));
        // 범인(1번)에게 잠긴 자료 3개가 몰려 있어도 공개된 자료만 세야 한다.
        when(repository.findEvidences(1L)).thenReturn(List.of(
                evidence(21L, 1L, false),
                evidence(22L, 1L, false),
                evidence(23L, 1L, false),
                evidence(24L, 1L, false),
                evidence(31L, 2L, true)
        ));

        CaseFileResponse response = service.getCaseFile(1L, User.builder().id(7L).role("ROLE_USER").build());

        Map<Long, Integer> counts = response.getSuspects().stream()
                .collect(Collectors.toMap(CaseFileResponse.Suspect::getSuspectId, CaseFileResponse.Suspect::getRelatedClueCount));
        assertEquals(1, counts.get(1L));
        assertEquals(1, counts.get(2L));
    }

    private CaseSuspect suspect(Long id) {
        CaseSuspect suspect = new CaseSuspect();
        suspect.setId(id);
        suspect.setEpisodeId(1L);
        suspect.setDisplayName("용의자" + id);
        return suspect;
    }

    private CaseEvidence evidence(Long id, Long suspectId, boolean unlockedByDefault) {
        CaseEvidence evidence = new CaseEvidence();
        evidence.setId(id);
        evidence.setEpisodeId(1L);
        evidence.setTitle("자료" + id);
        evidence.setType("NOTE");
        evidence.setRelatedSuspectId(suspectId);
        evidence.setUnlockedByDefault(unlockedByDefault);
        return evidence;
    }
}