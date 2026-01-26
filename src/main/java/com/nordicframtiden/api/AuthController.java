package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;
  private final PasswordEncoder encoder;

  public AuthController(AuthenticationManager authManager, JwtService jwtService, PasswordEncoder encoder) {
    this.authManager = authManager;
    this.jwtService = jwtService;
    this.encoder = encoder;
  }

  record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  record TokenResponse(String accessToken) {
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req) {

    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password()));

    String accessToken = jwtService.generateAccessToken(
        auth.getName(),
        Map.of("roles", auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList()));

    String refreshToken = jwtService.generateRefreshToken(auth.getName());

    ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
        .httpOnly(true)
        .secure(false) // true in prod
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofMinutes(15))
        .build();

    ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .path("/auth/refresh")
        .maxAge(Duration.ofDays(30))
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(Map.of("ok", true));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout() {

    ResponseCookie clearAccess = ResponseCookie.from("accessToken", "")
        .path("/")
        .maxAge(0)
        .build();

    ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "")
        .path("/auth/refresh")
        .maxAge(0)
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
        .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
        .build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest request) {

    String refreshToken = null;

    if (request.getCookies() != null) {
      for (Cookie c : request.getCookies()) {
        if ("refreshToken".equals(c.getName())) {
          refreshToken = c.getValue();
          break;
        }
      }
    }

    if (refreshToken == null) {
      return ResponseEntity.status(401).build();
    }

    Claims claims = jwtService.parse(refreshToken).getBody();
    String username = claims.getSubject();

    String newAccessToken = jwtService.generateAccessToken(
        username,
        Map.of("roles", claims.get("roles", List.class)));

    ResponseCookie accessCookie = ResponseCookie.from("accessToken", newAccessToken)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofMinutes(15))
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
        .body(Map.of("ok", true));
  }
}