package com.operation.seoul.favorite.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.favorite.dto.EpisodeFavoriteResponse;
import com.operation.seoul.favorite.service.EpisodeFavoriteService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EpisodeFavoriteController {
    private final EpisodeFavoriteService favoriteService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/episodes/{episodeId}/favorite")
    public ResponseEntity<ApiResponse<EpisodeFavoriteResponse>> addFavorite(@PathVariable Long episodeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "\uAD00\uC2EC \uC5D0\uD53C\uC18C\uB4DC\uC5D0 \uCD94\uAC00\uD588\uC2B5\uB2C8\uB2E4.",
                favoriteService.addFavorite(episodeId, currentUserResolver.requireCurrentUser())
        ));
    }

    @DeleteMapping("/episodes/{episodeId}/favorite")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(@PathVariable Long episodeId) {
        favoriteService.removeFavorite(episodeId, currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("\uAD00\uC2EC \uC5D0\uD53C\uC18C\uB4DC\uC5D0\uC11C \uC81C\uAC70\uD588\uC2B5\uB2C8\uB2E4.", null));
    }

    @GetMapping("/users/me/favorites")
    public ResponseEntity<ApiResponse<List<EpisodeFavoriteResponse>>> getMyFavorites() {
        return ResponseEntity.ok(ApiResponse.ok(
                "\uAD00\uC2EC \uC5D0\uD53C\uC18C\uB4DC \uBAA9\uB85D\uC785\uB2C8\uB2E4.",
                favoriteService.getMyFavorites(currentUserResolver.requireCurrentUser())
        ));
    }
}