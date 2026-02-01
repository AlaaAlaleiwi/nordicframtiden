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

  private static final String SAME_SITE = "None";
  private static final boolean SECURE = true;
  private static final Duration ACCESS_TTL = Duration.ofMinutes(60);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);

  // ✅ Append Partitioned for Safari / third-party cookie reliability
  private static String withPartitioned(ResponseCookie c) {
    // Partitioned cookies REQUIRE Secure + SameSite=None
    return c.toString() + "; Partitioned";
  }

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

  // ✅ what frontend expects
  record LoginResponse(String accessToken, List<String> roles) {}
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody LoginRequest req,
      HttpServletResponse response
  ) {
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password())
    );

    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority) // e.g. ROLE_ADMIN
        .toList();

    String accessToken = jwtService.generateAccessToken(
        auth.getName(), Map.of("roles", roles)
    );
    String refreshToken = jwtService.generateRefreshToken(
        auth.getName(), Map.of("roles", roles)
    );

    // ✅ Optional: keep cookies too (fine for Chrome; Safari may ignore sometimes)
    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(accessCookie(accessToken)));
    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(refreshCookie(refreshToken)));

    // ✅ IMPORTANT: return JSON body for Safari-proof client auth
    return ResponseEntity.ok(new LoginResponse(accessToken, roles));
  }
 

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    if (auth == null) return ResponseEntity.status(401).build();

    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    return ResponseEntity.ok(new MeResponse(auth.getName(), roles));
  }

  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
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

    String newAccess = jwtService.generateAccessToken(username, Map.of("roles", roles));

    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(accessCookie(newAccess)));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(clearAccessCookie()));
    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(clearRefreshCookie()));
    return ResponseEntity.ok().build();
  }
}