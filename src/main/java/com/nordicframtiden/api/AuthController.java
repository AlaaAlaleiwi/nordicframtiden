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
import org.springframework.http.HttpStatus;
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

    public AuthController(AuthenticationManager authManager,
                          JwtService jwtService,
                          AppUserRepository userRepo) {
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

    // ✅ Append Partitioned for Safari / third‑party cookie reliability
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

    // ---------- Endpoints ----------
@Deprecated
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req,
                                   HttpServletResponse response) {
        // Authenticate credentials
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        // At this point authentication succeeded – fetch the full user entity
        AppUser user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Build JWT claims
        Map<String, Object> claims = new HashMap<>();
        List<String> roleNames = user.getRoles().stream()
                .map(Role::name)
                .toList();
        List<String> permNames = user.getPermissions().stream()
                .map(Permission::name)
                .toList();
        claims.put("roles", roleNames);
        claims.put("perms", permNames);

        // Generate tokens using the correct JwtService signature
        String accessToken = jwtService.generateAccessToken(user.getUsername(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getUsername(),
                Collections.emptyMap());

        // Set cookies
        response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(accessCookie(accessToken)));
        response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(refreshCookie(refreshToken)));

        // Return body containing the token and the role/perm lists for immediate UI use
        return ResponseEntity.ok(new LoginResponse(accessToken, roleNames, permNames));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request,
                                    HttpServletResponse response) {
        // Extract refresh token from cookie
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return ResponseEntity.status(401).build();
        }
        String refreshToken = Arrays.stream(cookies)
                .filter(c -> "REFRESH_TOKEN".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        // Validate and parse token
        Claims claims = jwtService.validateRefreshToken(refreshToken);
        String username = claims.getSubject();

        // Load user to rebuild role/perm claims
        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Map<String, Object> newClaims = new HashMap<>();
        List<String> roleNames = user.getRoles().stream()
                .map(Role::name)
                .toList();
        List<String> permNames = user.getPermissions().stream()
                .map(Permission::name)
                .toList();
        newClaims.put("roles", roleNames);
        newClaims.put("perms", permNames);

        // Issue new access token
        String newAccessToken = jwtService.generateAccessToken(username, newClaims);
        response.addHeader(HttpHeaders.SET_COOKIE,
                withPartitioned(accessCookie(newAccessToken)));

        return ResponseEntity.ok(new LoginResponse(newAccessToken, roleNames, permNames));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(clearAccessCookie()));
        response.addHeader(HttpHeaders.SET_COOKIE, withPartitioned(clearRefreshCookie()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<String> roleNames = user.getRoles().stream()
                .map(Role::name)
                .toList();
        List<String> permNames = user.getPermissions().stream()
                .map(Permission::name)
                .toList();
        return ResponseEntity.ok(new MeResponse(username, roleNames, permNames));
    }
}