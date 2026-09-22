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
  private final long refreshTokenDays;

  @org.springframework.beans.factory.annotation.Autowired
  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.accessTokenMinutes}") long accessTokenMinutes,
      @Value("${app.jwt.refreshTokenDays:30}") long refreshTokenDays
  ) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.accessTokenMinutes = accessTokenMinutes;
    this.refreshTokenDays = refreshTokenDays;
  }

  public JwtService(String secret, String issuer, long accessTokenMinutes) {
    this(secret, issuer, accessTokenMinutes, 30);
  }

  public String generateAccessToken(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant exp = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .addClaims(claims) // roles + perms go here
        .claim("type", "access")
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateRefreshToken(String subject) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plus(refreshTokenDays, ChronoUnit.DAYS)))
        .claim("type", "refresh")
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

  public Claims validateAccessToken(String token) {
    Claims claims = parse(token).getBody();
    if (!"access".equals(claims.get("type"))) {
      throw new JwtException("Token is not an access token");
    }
    return claims;
  }

  public Claims validateRefreshToken(String token) {
    Claims claims = parse(token).getBody();
    if (!"refresh".equals(claims.get("type"))) {
      throw new JwtException("Token is not a refresh token");
    }
    return claims;
  }
}
