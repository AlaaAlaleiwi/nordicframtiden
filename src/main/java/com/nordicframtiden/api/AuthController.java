package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.admin.model.AdminProfileRepository;
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
    private final UserProfileRepository userProfiles;
    private final AdminProfileRepository adminProfiles;

    public AuthController(AuthenticationManager authManager,
                          JwtService jwtService,
                          AppUserRepository userRepo,
                          UserProfileRepository userProfiles,
                          AdminProfileRepository adminProfiles) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.userProfiles = userProfiles;
        this.adminProfiles = adminProfiles;
    }

    // ---------- DTOs ----------
    record LoginRequest(String username, String password) {}

    // include perms so frontend can render menus immediately
    record LoginResponse(String accessToken, String refreshToken, List<String> roles, List<String> perms) {}

    record RefreshRequest(String refreshToken) {}

    record MeResponse(String username, String fullName, List<String> roles, List<String> perms) {}

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
        return ResponseEntity.ok(tokens(user, roleNames, permNames));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            String username = jwtService.validateRefreshToken(request.refreshToken()).getSubject();
            AppUser user = userRepo.findByUsername(username).filter(AppUser::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Active user not found"));
            List<String> roles = user.getRoles().stream().map(Role::name).toList();
            List<String> perms = user.getPermissions().stream().map(Permission::name).toList();
            return ResponseEntity.ok(tokens(user, roles, perms));
        } catch (RuntimeException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
        String fullName = userProfiles.findByUserId(user.getId()).map(profile -> profile.getFullName())
                .or(() -> adminProfiles.findByUserId(user.getId()).map(profile -> profile.getFullName()))
                .filter(name -> !name.isBlank())
                .orElse(username);
        return ResponseEntity.ok(new MeResponse(username, fullName, roleNames, permNames));
    }

    private LoginResponse tokens(AppUser user, List<String> roles, List<String> perms) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("perms", perms);
        return new LoginResponse(
                jwtService.generateAccessToken(user.getUsername(), claims),
                jwtService.generateRefreshToken(user.getUsername()),
                roles,
                perms
        );
    }
}
