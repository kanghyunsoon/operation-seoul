package com.operation.seoul.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long validityInMilliseconds = 3600000 * 24; // 24시간 유효

    /**
     * application-local.properties의 jwt.secret으로 HMAC 서명 키를 만듭니다.
     * JJWT는 충분히 긴 secret을 요구하므로 예시 파일에는 32자 이상을 명시해 두었습니다.
     */
    public JwtTokenProvider(@Value("${jwt.secret:operation-seoul-local-development-jwt-secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** 로그인 성공 시 이메일을 subject로 갖는 JWT를 생성합니다. */
    public String createToken(String email) {
        Claims claims = Jwts.claims().setSubject(email);
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key)
                .compact();
    }

    /** 파싱이 가능하고 만료되지 않은 토큰인지 확인합니다. */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 인증 필터가 사용자 조회에 사용할 subject 값을 꺼냅니다. */
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /** 서명 검증과 만료 검증을 포함해 claims를 파싱합니다. */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
