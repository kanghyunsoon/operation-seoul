package com.operation.seoul.ranking.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.ranking.dto.RankingEntryResponse;
import com.operation.seoul.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {
    private final RankingRepository rankingRepository;

    public List<RankingEntryResponse> getRankings(Long episodeId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 100));
        List<RankingEntryResponse> entries = rankingRepository.findRankings(episodeId, safeLimit);
        for (int index = 0; index < entries.size(); index += 1) {
            entries.get(index).setRankNo(index + 1);
        }
        return entries;
    }

    public List<RankingEntryResponse> getMyClears(User user, Long episodeId) {
        return rankingRepository.findMyClears(user.getId(), episodeId);
    }
}
