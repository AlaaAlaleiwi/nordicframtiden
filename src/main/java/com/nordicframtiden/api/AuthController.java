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

  /* ---------- LOGIN ---------- */
  @PostMapping("/login")
  public ResponseEntity<?> login(
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
        auth.getName(),
        Map.of("roles", roles)
    );

    String refreshToken = jwtService.generateRefreshToken(auth.getName());

    ResponseCookie accessCookie = ResponseCookie.from("ACCESS_TOKEN", accessToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ofMinutes(15))
        .build();

    ResponseCookie refreshCookie = ResponseCookie.from("REFRESH_TOKEN", refreshToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/auth/refresh")
        .maxAge(Duration.ofDays(7))
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

    return ResponseEntity.ok().build();
  }

  /* ---------- ME ---------- */
  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    return ResponseEntity.ok(new MeResponse(auth.getName(), roles));
  }

  /* ---------- REFRESH ---------- */
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return ResponseEntity.status(401).build();

    String refreshToken = null;
    for (Cookie c : cookies) {
      if ("REFRESH_TOKEN".equals(c.getName())) {
        refreshToken = c.getValue();
      }
    }

    if (refreshToken == null) return ResponseEntity.status(401).build();

    Claims claims = jwtService.parse(refreshToken).getBody();
    String username = claims.getSubject();

    String newAccess = jwtService.generateAccessToken(
        username,
        Map.of("roles", claims.get("roles"))
    );

    ResponseCookie cookie = ResponseCookie.from("ACCESS_TOKEN", newAccess)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ofMinutes(15))
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return ResponseEntity.ok().build();
  }

  /* ---------- LOGOUT ---------- */
  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletResponse response) {
    ResponseCookie clearAccess = ResponseCookie.from("ACCESS_TOKEN", "")
        .path("/")
        .maxAge(0)
        .build();

    ResponseCookie clearRefresh = ResponseCookie.from("REFRESH_TOKEN", "")
        .path("/auth/refresh")
        .maxAge(0)
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
    response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());

    return ResponseEntity.ok().build();
  }
}