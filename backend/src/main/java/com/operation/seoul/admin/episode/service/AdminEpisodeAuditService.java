package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.domain.AdminEpisodeAuditLog;
import com.operation.seoul.admin.episode.dto.AdminEpisodeAuditLogResponse;
import com.operation.seoul.admin.episode.repository.AdminEpisodeAuditRepository;
import com.operation.seoul.auth.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEpisodeAuditService {
    private final AdminEpisodeAuditRepository auditRepository;

    public void record(
            User actor,
            Long episodeId,
            String episodeTitle,
            String action,
            String targetType,
            Long targetId,
            String summary
    ) {
        if (actor == null || actor.getId() == null) {
            log.error("admin_audit_missing_actor action={} episodeId={}", action, episodeId);
            return;
        }
        try {
            AdminEpisodeAuditLog auditLog = new AdminEpisodeAuditLog();
            auditLog.setEpisodeId(episodeId);
            auditLog.setEpisodeTitle(trim(episodeTitle, 255));
            auditLog.setActorUserId(actor.getId());
            auditLog.setActorEmail(trim(actor.getEmail(), 255));
            auditLog.setActorNickname(trim(actor.getNickname(), 255));
            auditLog.setAction(trim(action, 64));
            auditLog.setTargetType(trim(targetType, 64));
            auditLog.setTargetId(targetId);
            auditLog.setSummary(trim(summary, 1000));
            auditLog.setRequestId(trim(MDC.get("requestId"), 100));
            auditRepository.insert(auditLog);
        } catch (Exception exception) {
            log.error(
                    "admin_audit_write_failed action={} episodeId={} actorUserId={}",
                    action,
                    episodeId,
                    actor.getId(),
                    exception
            );
        }
    }

    public List<AdminEpisodeAuditLogResponse> getEpisodeAuditLogs(Long episodeId, int requestedLimit) {
        int limit = Math.min(200, Math.max(1, requestedLimit));
        return auditRepository.findByEpisodeId(episodeId, limit).stream()
                .map(item -> AdminEpisodeAuditLogResponse.builder()
                        .auditId(item.getId())
                        .episodeId(item.getEpisodeId())
                        .episodeTitle(item.getEpisodeTitle())
                        .actorUserId(item.getActorUserId())
                        .actorEmail(item.getActorEmail())
                        .actorNickname(item.getActorNickname())
                        .action(item.getAction())
                        .targetType(item.getTargetType())
                        .targetId(item.getTargetId())
                        .summary(item.getSummary())
                        .requestId(item.getRequestId())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
