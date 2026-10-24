package com.operation.seoul.user.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.user.dto.AdminUserUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceSelfLockoutTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminUserService service = new AdminUserService(userRepository);
    private final User admin = User.builder().id(1L).nickname("관리자").role("ROLE_ADMIN").status("ACTIVE").admin(true).build();
    private final User member = User.builder().id(2L).nickname("회원").role("ROLE_USER").status("ACTIVE").build();

    @Test
    void adminCannotDemoteOrSuspendThemselves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, member));

        ApiException demote = assertThrows(ApiException.class, () -> service.updateUser(1L, request("ROLE_USER", null), 1L));
        ApiException suspend = assertThrows(ApiException.class, () -> service.updateUser(1L, request(null, "SUSPENDED"), 1L));
        ApiException delete = assertThrows(ApiException.class, () -> service.softDeleteUser(1L, 1L));

        assertEquals("SELF_LOCKOUT", demote.getCode());
        assertEquals("SELF_LOCKOUT", suspend.getCode());
        assertEquals("SELF_LOCKOUT", delete.getCode());
        verify(userRepository, never()).updateAdminFields(anyLong(), any(), any(), any(), any());
        verify(userRepository, never()).softDeleteById(anyLong());
    }

    @Test
    void adminCanStillSuspendOtherUsers() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(userRepository.findAll()).thenReturn(List.of(admin, member));

        service.updateUser(2L, request(null, "SUSPENDED"), 1L);

        verify(userRepository).updateAdminFields(2L, "회원", "ROLE_USER", "SUSPENDED", null);
    }

    private AdminUserUpdateRequest request(String role, String status) {
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setRole(role);
        request.setStatus(status);
        return request;
    }
}
