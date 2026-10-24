package com.operation.seoul.auth.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.SocialAccountRepository;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthServiceAccountLinkTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final OAuthService service;

    OAuthServiceAccountLinkTest() throws Exception {
        service = new OAuthService(userRepository, socialAccountRepository, mock(AuthService.class),
                new BCryptPasswordEncoder(), "", "", "", "");
    }

    @Test
    void socialLoginDoesNotAttachToEmailOnlyAccount() {
        User emailAccount = User.builder().id(5L).email("victim@example.com").build();
        when(socialAccountRepository.findUser("google", "g-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(emailAccount));
        when(socialAccountRepository.countByUserId(5L)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.findOrCreateUser("google", "g-1", "victim@example.com", "피해자", null));

        assertEquals("EMAIL_ACCOUNT_EXISTS", exception.getCode());
        verify(socialAccountRepository, never()).insert(anyLong(), anyString(), anyString());
    }

    @Test
    void socialLoginLinksAccountThatAlreadyUsesAnotherProvider() {
        User kakaoAccount = User.builder().id(6L).email("user@example.com").profileImageUrl("/p.png").build();
        when(socialAccountRepository.findUser("google", "g-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(kakaoAccount));
        when(socialAccountRepository.countByUserId(6L)).thenReturn(1);

        User linked = service.findOrCreateUser("google", "g-2", "user@example.com", "사용자", null);

        assertSame(kakaoAccount, linked);
        verify(socialAccountRepository).insert(6L, "google", "g-2");
    }
}
