package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-key-that-is-at-least-32-bytes";
    private final JwtService jwtService = new JwtService(SECRET, "test-issuer", 60);

    @Test
    void generatedTokenIsExplicitlyAnAccessToken() {
        String token = jwtService.generateAccessToken("alice", Map.of());

        assertThat(jwtService.validateAccessToken(token).get("type")).isEqualTo("access");
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        Instant now = Instant.now();
        String refreshToken = Jwts.builder()
            .setIssuer("test-issuer")
            .setSubject("alice")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(3600)))
            .claim("type", "refresh")
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();

        assertThatThrownBy(() -> jwtService.validateAccessToken(refreshToken))
            .isInstanceOf(JwtException.class);
    }
}
