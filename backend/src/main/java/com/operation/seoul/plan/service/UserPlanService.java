package com.operation.seoul.plan.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.plan.dto.UserPlanRequest;
import com.operation.seoul.plan.dto.UserPlanResponse;
import com.operation.seoul.plan.repository.UserPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserPlanService {
    private static final Set<String> ALLOWED_STATUSES = Set.of("PLANNED", "DONE", "CANCELLED");

    private final UserPlanRepository planRepository;
    private final EpisodeRepository episodeRepository;

    public List<UserPlanResponse> getMyPlans(User user) {
        return planRepository.findByUserId(user.getId());
    }

    public UserPlanResponse createPlan(User user, UserPlanRequest request) {
        Long episodeId = requireEpisodeId(request);
        requirePublishedEpisode(episodeId);
        String status = normalizeStatus(request.getStatus(), "PLANNED");
        planRepository.upsert(user.getId(), episodeId, normalizePlannedAt(request.getPlannedAt()), normalizeMemo(request.getMemo()), status);
        return planRepository.findByUserIdAndEpisodeId(user.getId(), episodeId);
    }

    public UserPlanResponse updatePlan(User user, Long planId, UserPlanRequest request) {
        UserPlanResponse existing = planRepository.findByIdAndUserId(planId, user.getId());
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found.");
        }
        String status = normalizeStatus(request.getStatus(), existing.getStatus());
        LocalDateTime plannedAt = request.getPlannedAt() == null ? existing.getPlannedAt() : normalizePlannedAt(request.getPlannedAt());
        String memo = request.getMemo() == null ? existing.getMemo() : normalizeMemo(request.getMemo());
        planRepository.update(planId, user.getId(), plannedAt, memo, status);
        return planRepository.findByIdAndUserId(planId, user.getId());
    }

    public void deletePlan(User user, Long planId) {
        if (planRepository.delete(planId, user.getId()) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found.");
        }
    }

    private Long requireEpisodeId(UserPlanRequest request) {
        if (request == null || request.getEpisodeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_REQUIRED", "Episode is required.");
        }
        return request.getEpisodeId();
    }

    private void requirePublishedEpisode(Long episodeId) {
        Episode episode = episodeRepository.findEpisodeById(episodeId);
        if (episode == null || !"PUBLISHED".equals(episode.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "Episode not found.");
        }
    }

    private LocalDateTime normalizePlannedAt(LocalDateTime plannedAt) {
        return plannedAt == null ? LocalDateTime.now().plusDays(1).withSecond(0).withNano(0) : plannedAt.withSecond(0).withNano(0);
    }

    private String normalizeMemo(String memo) {
        if (!StringUtils.hasText(memo)) {
            return null;
        }
        String trimmed = memo.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String normalizeStatus(String rawStatus, String fallback) {
        String status = StringUtils.hasText(rawStatus) ? rawStatus.trim().toUpperCase() : fallback;
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN_STATUS", "Invalid plan status.");
        }
        return status;
    }
}
