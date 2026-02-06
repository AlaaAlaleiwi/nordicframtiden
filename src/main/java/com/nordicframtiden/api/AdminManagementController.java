package com.nordicframtiden.api;

import com.nordicframtiden.admin.AdminService;
import com.nordicframtiden.api.UserManagementController.UserResponse;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admins")
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {

  private final AdminService adminService;
  private final AppUserRepository repo;

  public AdminManagementController(
      AdminService adminService,
      AppUserRepository repo
  ) {
    this.adminService = adminService;
    this.repo = repo;
  }

  /* =========================
     DTOs
     ========================= */

  // No password → generated server-side
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

  /* =========================
     ENDPOINTS
     ========================= */
 @GetMapping("/me")
  @PreAuthorize("hasAnyRole('ADMIN')")
  public AdminResponse me(Authentication auth) {
    var username = auth.getName();
    var user = adminService.getDetailedUser(username);
    return new AdminResponse(
            user.id(),
            user.username(),
            user.enabled(),
            user.fullName(),
            user.email(),
            user.phone(),
            null
        );
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


  @PutMapping("/{id}")
  public AdminResponse update(
      @PathVariable Long id,
      @RequestBody UpdateAdminRequest req
  ) {

    var updated = adminService.updateAdminWithProfile(
        id,
        req.username(),
        req.enabled(),
        req.fullName(),
        req.email(),
        req.phone()
    );

    return new AdminResponse(
        updated.id(),
        updated.username(),
        updated.enabled(),
        updated.fullName(),
        updated.email(),
        updated.phone(),
        null
    );
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    adminService.deleteAdmin(id);
  }

  /* =========================
     STATS
     ========================= */

  @GetMapping("/stats")
  public AdminStats stats() {
    return new AdminStats(
        repo.countByRole(Role.ADMIN),
        repo.countByRole(Role.USER),
        repo.countByRole(Role.STAFF)
    );
  }
}