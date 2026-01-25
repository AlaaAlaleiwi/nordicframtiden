package com.nordicframtiden.api;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.service.AdminService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admins")
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {

  private final AdminService adminService;

  public AdminManagementController(AdminService adminService) {
    this.adminService = adminService;
  }

  // DTOs
  public record CreateAdminRequest(
      @NotBlank String username,
      @NotBlank @Size(min = 8) String password,
      Boolean enabled
  ) {}

  public record UpdateAdminRequest(
      String username,
      @Size(min = 8) String password, // optional; if present must be >= 8
      Boolean enabled
  ) {}

  public record AdminResponse(Long id, String username, boolean enabled) {
    static AdminResponse from(AppUser u) {
      return new AdminResponse(u.getId(), u.getUsername(), u.isEnabled());
    }
  }

  @PostMapping
  public AdminResponse create(@RequestBody CreateAdminRequest req) {
    boolean enabled = req.enabled() == null || req.enabled();
    AppUser created = adminService.createAdmin(req.username(), req.password(), enabled);
    return AdminResponse.from(created);
  }

  @PutMapping("/{id}")
  public AdminResponse update(@PathVariable Long id, @RequestBody UpdateAdminRequest req) {
    AppUser updated = adminService.updateAdmin(id, req.username(), req.password(), req.enabled());
    return AdminResponse.from(updated);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    adminService.deleteAdmin(id);
  }
}