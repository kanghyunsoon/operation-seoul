package com.operation.seoul.favorite.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.favorite.domain.EpisodeFavorite;
import com.operation.seoul.favorite.dto.EpisodeFavoriteResponse;
import com.operation.seoul.favorite.repository.EpisodeFavoriteRepository;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EpisodeFavoriteService {
    private final EpisodeFavoriteRepository favoriteRepository;
    private final EpisodeRepository episodeRepository;

    public EpisodeFavoriteResponse addFavorite(Long episodeId, User user) {
        requirePublishedEpisode(episodeId);
        favoriteRepository.insertIgnore(user.getId(), episodeId);
        EpisodeFavorite favorite = favoriteRepository.findByUserIdAndEpisodeId(user.getId(), episodeId);
        return favoriteRepository.findPublishedFavoritesByUserId(user.getId()).stream()
                .filter(item -> episodeId.equals(item.getEpisodeId()))
                .findFirst()
                .map(this::markFavorited)
                .orElseGet(() -> EpisodeFavoriteResponse.builder()
                        .favoriteId(favorite == null ? null : favorite.getId())
                        .episodeId(episodeId)
                        .favorited(true)
                        .build());
    }

    public void removeFavorite(Long episodeId, User user) {
        favoriteRepository.delete(user.getId(), episodeId);
    }

    public List<EpisodeFavoriteResponse> getMyFavorites(User user) {
        return favoriteRepository.findPublishedFavoritesByUserId(user.getId()).stream()
                .map(this::markFavorited)
                .toList();
    }

    private Episode requirePublishedEpisode(Long episodeId) {
        Episode episode = episodeRepository.findEpisodeById(episodeId);
        if (episode == null || !"PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "\uACF5\uAC1C\uB41C \uC5D0\uD53C\uC18C\uB4DC\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
        }
        return episode;
    }

    private EpisodeFavoriteResponse markFavorited(EpisodeFavoriteResponse response) {
        response.setFavorited(true);
        return response;
    }
}