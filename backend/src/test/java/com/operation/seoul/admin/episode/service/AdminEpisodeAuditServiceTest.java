package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.domain.AdminEpisodeAuditLog;
import com.operation.seoul.admin.episode.repository.AdminEpisodeAuditRepository;
import com.operation.seoul.auth.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminEpisodeAuditServiceTest {
    private final AdminEpisodeAuditRepository repository = mock(AdminEpisodeAuditRepository.class);
    private final AdminEpisodeAuditService service = new AdminEpisodeAuditService(repository);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsActorAndRequestIdWithoutSensitivePayload() {
        MDC.put("requestId", "request-12345678");
        User actor = User.builder()
                .id(7L)
                .email("admin@example.com")
                .nickname("운영자")
                .role("ROLE_ADMIN")
                .build();

        service.record(actor, 12L, "정동 사건", "UPDATE_PUZZLE", "PUZZLE", 44L, "퍼즐 설정을 수정했습니다.");

        verify(repository).insert(any(AdminEpisodeAuditLog.class));
    }

    @Test
    void clampsAuditQueryLimitAndMapsResponse() {
        AdminEpisodeAuditLog item = new AdminEpisodeAuditLog();
        item.setId(1L);
        item.setEpisodeId(12L);
        item.setActorUserId(7L);
        item.setActorEmail("admin@example.com");
        item.setAction("PUBLISH_EPISODE");
        item.setTargetType("EPISODE");
        item.setSummary("게시했습니다.");
        item.setCreatedAt(LocalDateTime.of(2026, 6, 10, 12, 0));
        when(repository.findByEpisodeId(12L, 200)).thenReturn(List.of(item));

        var responses = service.getEpisodeAuditLogs(12L, 500);

        assertEquals(1, responses.size());
        assertEquals("PUBLISH_EPISODE", responses.get(0).getAction());
        assertNull(responses.get(0).getRequestId());
    }
}
