package com.nordicframtiden.api;

import com.nordicframtiden.admin.AdminService;
import com.nordicframtiden.security.repo.AppUserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.nordicframtiden.security.model.Role;
import java.util.List;

@RestController
@RequestMapping("/api/admins")
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {

    private final AdminService adminService;
    private final AppUserRepository repo;

    public AdminManagementController(AdminService adminService,
                                     AppUserRepository repo) {
        this.adminService = adminService;
        this.repo = repo;
    }

    /* =========================
       DTOs
       ========================= */

    // No password → generated server‑side
    public record CreateAdminRequest(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank String phone,
            Boolean enabled
    ) {}

    public record UpdateAdminRequest(
            String username,
            String fullName,
            @Email String email,
            String phone,
            Boolean enabled
    ) {}

    public record AdminResponse(
            Long id,
            String username,
            boolean enabled,
            String fullName,
            String email,
            String phone,
            String password
    ) {}

    public record AdminStats(
            long admins,
            long users,
            long staff
    ) {}

    public record ResetPasswordResponse(
            Long id,
            String username,
            String password
    ) {}

    /* =========================
       ENDPOINTS
       ========================= */
    
    @GetMapping("/me")
    public ResponseEntity<AdminResponse> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var username = auth.getName();
        var user = adminService.getDetailedUser(username);
        return ResponseEntity.ok(new AdminResponse(
                user.id(),
                user.username(),
                user.enabled(),
                user.fullName(),
                user.email(),
                user.phone(),
                null
        ));
    }

    @GetMapping
    public List<AdminResponse> list() {
        return adminService.listAdminsDetailed().stream()
                .map(a -> new AdminResponse(
                        a.id(),
                        a.username(),
                        a.enabled(),
                        a.fullName(),
                        a.email(),
                        a.phone(),
                        null
                ))
                .toList();
    }

    @PostMapping("/create")
    public AdminResponse create(@RequestBody CreateAdminRequest req) {
        boolean enabled = req.enabled() == null || req.enabled();

        var created = adminService.createAdminWithProfile(
                enabled,
                req.fullName(),
                req.email(),
                req.phone()
        );

        return new AdminResponse(
                created.id(),
                created.username(),
                created.enabled(),
                created.fullName(),
                created.email(),
                created.phone(),
                created.password()
        );
    }
    @GetMapping("/stats")
  public AdminStats stats() {
    return new AdminStats(
        repo.countByRole(Role.ADMIN),
        repo.countByRole(Role.USER),
        repo.countByRole(Role.STAFF)
    );
  }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        var updated = adminService.resetAdminPassword(id);
        return new ResetPasswordResponse(updated.id(), updated.username(), updated.password());
    }
}
