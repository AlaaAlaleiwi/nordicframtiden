package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
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
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;
  private final AppUserRepository userRepo;

  public AuthController(AuthenticationManager authManager, JwtService jwtService, AppUserRepository userRepo) {
    this.authManager = authManager;
    this.jwtService = jwtService;
    this.userRepo = userRepo;
  }

  // ---------- DTOs ----------
  record LoginRequest(String username, String password) {}

  // include perms so frontend can render menus immediately
  record LoginResponse(String accessToken, List<String> roles, List<String> perms) {}

  record MeResponse(String username, List<String> roles, List<String> perms) {}

  // ---------- Cookie config ----------
  private static final String SAME_SITE = "None";
  private static final boolean SECURE = true; // set false in local HTTP if needed
  private static final Duration ACCESS_TTL = Duration.ofMinutes(60);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);

  // ✅ Append Partitioned for Safari / third-party cookie reliability
  private static String withPartitioned(ResponseCookie c) {
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

  // ---------- Helpers ----------
  private AppUser loadUser(String username) {
    return userRepo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  private static List<String> roleStrings(AppUser u) {
    Set<Role> roles = (u.getRoles() == null) ? Set.of() : u.getRoles();
    // Store enum names without ROLE_ prefix (filter will normalize)
    return roles.stream().map(Enum::name).sorted().toList();
  }

  private static List<String> permStrings(AppUser u) {
    Set<Permission> perms = (u.getPermissions() == null) ? Set.of() : u.getPermissions();
    // Store enum names without PERM_ prefix (filter will normalize)
    return perms.stream().map(Enum::name).sorted().toList();
  }

  private static Map<String, Object> buildClaims(List<String> roles, List<String> perms) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", roles);
    claims.put("perms", perms);
    return claims;
  }

  private static String getCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (name.equals(c.getName())) return c.getValue();
    }
    return null;
  }

  // ---------- Endpoints ----------

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody LoginRequest req,
      HttpServletResponse response
  ) {
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password())
    );

    // ✅ load from DB to include permissions
    AppUser u = loadUser(auth.getName());

    List<String> roles = roleStrings(u);
    List<String> perms = permStrings(u);

    Map<String, Object> claims = buildClaims(roles, perms);

    String accessToken = jwtService.generateAccessToken(auth.getName(), claims);
    String refreshToken = jwtService.generateRefreshToken(auth.getName(), claims);

    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(accessCookie(accessToken)));
    response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(refreshCookie(refreshToken)));

    return ResponseEntity.ok(new LoginResponse(accessToken, roles, perms));
  }

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    if (auth == null) return ResponseEntity.status(401).build();

    AppUser u = loadUser(auth.getName());
    List<String> roles = roleStrings(u);
    List<String> perms = permStrings(u);

    return ResponseEntity.ok(new MeResponse(auth.getName(), roles, perms));
  }

  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = getCookie(request, "REFRESH_TOKEN");
    if (refreshToken == null) return ResponseEntity.status(401).build();
    if (!jwtService.isRefreshToken(refreshToken)) return ResponseEntity.status(401).build();

    Claims claims = jwtService.parse(refreshToken).getBody();
    String username = claims.getSubject();

    // ✅ DB-based (permissions changes take effect immediately)
    AppUser u = loadUser(username);
    List<String> roles = roleStrings(u);
    List<String> perms = permStrings(u);

    String newAccess = jwtService.generateAccessToken(username, buildClaims(roles, perms));
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