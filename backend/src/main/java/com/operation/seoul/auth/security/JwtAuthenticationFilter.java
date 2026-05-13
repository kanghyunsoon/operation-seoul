package com.operation.seoul.auth.security;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * 모든 요청에서 Authorization 헤더를 확인하고, 유효한 JWT가 있으면 SecurityContext에 User를 적재합니다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    /** `Bearer <token>` 형식의 헤더에서 실제 토큰 문자열만 분리합니다. */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    /** 토큰 subject로 사용자 엔티티를 조회해 이후 컨트롤러/서비스에서 현재 사용자로 사용할 수 있게 합니다. */
    private void authenticate(String token) {
        String email = jwtTokenProvider.getSubject(token);
        userRepository.findByEmail(email).ifPresent(user -> {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    buildAuthorities(user)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
    }

    /** Spring Security 권한 규칙과 맞추기 위해 ROLE_ 접두사가 붙은 authority를 생성합니다. */
    private List<SimpleGrantedAuthority> buildAuthorities(User user) {
        String role = user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(role));
    }
}
