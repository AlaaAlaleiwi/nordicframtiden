package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;

  public AuthController(AuthenticationManager authManager, JwtService jwtService) {
    this.authManager = authManager;
    this.jwtService = jwtService;
  }

  record LoginRequest(String username, String password) {}
  record MeResponse(String username, List<String> roles) {}

  // ✅ One place for cookie settings
  private static final String SAME_SITE = "None"; // ✅ cross-site required
  private static final boolean SECURE = true;     // ✅ must be true on https
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);

  private ResponseCookie accessCookie(String token) {
    return ResponseCookie.from("ACCESS_TOKEN", token)
        .httpOnly(true)
        .secure(SECURE)
        .sameSite(SAME_SITE)
        .path("/")
        .maxAge(ACCESS_TTL)
        .build();
  }

  private ResponseCookie refreshCookie(String token) {
    return ResponseCookie.from("REFRESH_TOKEN", token)
        .httpOnly(true)
        .secure(SECURE)
        .sameSite(SAME_SITE)
        .path("/auth/refresh")
        .maxAge(REFRESH_TTL)
        .build();
  }

  private ResponseCookie clearAccessCookie() {
    return ResponseCookie.from("ACCESS_TOKEN", "")
        .httpOnly(true)
        .secure(SECURE)
        .sameSite(SAME_SITE)
        .path("/")
        .maxAge(0)
        .build();
  }

  private ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from("REFRESH_TOKEN", "")
        .httpOnly(true)
        .secure(SECURE)
        .sameSite(SAME_SITE)
        .path("/auth/refresh")
        .maxAge(0)
        .build();
  }

  /* =========================
     LOGIN
     ========================= */
  @PostMapping("/login")
  public ResponseEntity<Void> login(
      @RequestBody LoginRequest req,
      HttpServletResponse response
  ) {
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password())
    );

    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    String accessToken = jwtService.generateAccessToken(
        auth.getName(), Map.of("roles", roles)
    );
    String refreshToken = jwtService.generateRefreshToken(
        auth.getName(), Map.of("roles", roles)
    );

    response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());

    return ResponseEntity.ok().build();
  }

  /* =========================
     ME
     ========================= */
  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    if (auth == null) return ResponseEntity.status(401).build();

    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    return ResponseEntity.ok(new MeResponse(auth.getName(), roles));
  }

  /* =========================
     REFRESH
     ========================= */
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return ResponseEntity.status(401).build();

    String refreshToken = null;
    for (Cookie c : cookies) {
      if ("REFRESH_TOKEN".equals(c.getName())) {
        refreshToken = c.getValue();
        break;
      }
    }

    if (refreshToken == null) return ResponseEntity.status(401).build();
    if (!jwtService.isRefreshToken(refreshToken)) return ResponseEntity.status(401).build();

    Claims claims = jwtService.parse(refreshToken).getBody();
    String username = claims.getSubject();
    Object roles = claims.get("roles");

    String newAccess = jwtService.generateAccessToken(
        username, Map.of("roles", roles)
    );

    response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(newAccess).toString());
    return ResponseEntity.ok().build();
  }

  /* =========================
     LOGOUT
     ========================= */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, clearAccessCookie().toString());
    response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
    return ResponseEntity.ok().build();
  }
}