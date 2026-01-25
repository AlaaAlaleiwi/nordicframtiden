package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Component
public class JwtService {

  private final Key key;
  private final String issuer;
  private final long accessTokenMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.accessTokenMinutes}") long accessTokenMinutes
  ) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.accessTokenMinutes = accessTokenMinutes;
  }

  public String generateAccessToken(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant exp = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .addClaims(claims)
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public Jws<Claims> parse(String token) throws JwtException {
    return Jwts.parserBuilder()
        .requireIssuer(issuer)
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token);
  }

  public String getSubject(String token) {
    return parse(token).getBody().getSubject();
  }
}