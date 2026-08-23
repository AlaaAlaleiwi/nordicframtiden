package com.nordicframtiden.api;

import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
 @PreAuthorize("hasAnyRole('USER','STAFF','ADMIN')")
public class UserManagementController {

  private final UserService userService;

  public UserManagementController(UserService userService) {
    this.userService = userService;
  }

  // ---------- DTOs ----------

  public record UserResponse(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone,
      BigDecimal hourlyCost,
      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,
      Set<Permission> permissions, // ✅ NEW
      String password // only on create/reset
  ) {}

  public record CreateUserRequest(
      @NotBlank String fullName,
      @NotBlank @Email String email,
      @NotBlank String phone,
      Boolean enabled,
      BigDecimal hourlyCost,

      @NotNull Integer yearOfBirth,
      @NotBlank String countyCode,
      @NotBlank String municipalityCode,

      Set<Permission> permissions // ✅ NEW (used for STAFF)
  ) {}

  public record UpdateUserRequest(
      String fullName,
      @Email String email,
      String phone,
      Boolean enabled,
      BigDecimal hourlyCost,

      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,

      Set<Permission> permissions // ✅ NEW (optional; only applied to STAFF)
  ) {}

  private static UserResponse toResponse(UserService.DetailedUser u, String password) {
    return new UserResponse(
        u.id(),
        u.username(),
        u.enabled(),
        u.fullName(),
        u.email(),
        u.phone(),
        u.hourlyCost(),
        u.yearOfBirth(),
        u.countyCode(),
        u.municipalityCode(),
        u.permissions(),
        password
    );
  }

  private static UserResponse toResponse(UserService.UserRow u, String password) {
    return new UserResponse(
        u.id(),
        u.username(),
        u.enabled(),
        u.fullName(),
        u.email(),
        u.phone(),
        u.hourlyCost(),
        u.yearOfBirth(),
        u.countyCode(),
        u.municipalityCode(),
        u.permissions(),
        password
    );
  }

  // ---------- Endpoints ----------

  // ✅ Anyone logged-in can call /me
  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    var username = auth.getName();
    var user = userService.getDetailedByUsername(username);
    return ResponseEntity.ok(toResponse(user, null));
  }

  // ✅ STAFF/ADMIN can view user
  @GetMapping("/{id}")
  public UserResponse getOne(@PathVariable Long id) {
    var r = userService.getDetailedById(id);
    return toResponse(r, null);
  }

  // ✅ STAFF/ADMIN can list
  @GetMapping
  public List<UserResponse> list(@RequestParam Role role) {
    return userService.listDetailedByRole(role).stream()
        .map(r -> toResponse(r, null))
        .toList();
  }

  // ✅ STAFF/ADMIN can create users (your UI does)
  @PostMapping
  public UserResponse create(@RequestParam Role role, @RequestBody CreateUserRequest req) {
    boolean enabled = req.enabled() == null || req.enabled();

    var created = userService.createWithProfile(
        role,
        enabled,
        req.fullName(),
        req.email(),
        req.phone(),
        req.hourlyCost(),
        req.yearOfBirth(),
        req.countyCode(),
        req.municipalityCode(),
        req.permissions() // ✅ new
    );

    return toResponse(created, created.password());
  }

  // ✅ STAFF/ADMIN can update
  @PutMapping("/{id}")
  public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
    System.out.println(req);
    var updated = userService.updateWithProfile(
        id,
        req.fullName(),
        req.email(),
        req.phone(),
        req.enabled(),
        req.hourlyCost(),
        req.yearOfBirth(),
        req.countyCode(),
        req.municipalityCode(),
        req.permissions() // ✅ new
    );

    return toResponse(updated, null);
  }

  // ✅ ADMIN only delete
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    userService.deleteUser(id);
  }

  // ✅ ADMIN only reset-password
  @PostMapping("/{id}/reset-password")
  public UserResponse resetPassword(@PathVariable Long id) {
    var updated = userService.resetPassword(id);
    return toResponse(updated, updated.password());
  }
}