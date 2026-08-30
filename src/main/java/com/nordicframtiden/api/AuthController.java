package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // ---------- Endpoints ----------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
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
        return ResponseEntity.ok(new LoginResponse(accessToken, roleNames, permNames));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
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
