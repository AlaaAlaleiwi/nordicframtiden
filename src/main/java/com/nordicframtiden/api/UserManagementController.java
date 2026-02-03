package com.nordicframtiden.api;

import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
      String password // returned only on create
  ) {}

  public record CreateUserRequest(
      @NotBlank String fullName,
      @NotBlank @Email String email,
      @NotBlank String phone,
      Boolean enabled,
      BigDecimal hourlyCost,

      @NotNull Integer yearOfBirth,
      @NotBlank String countyCode,
      @NotBlank String municipalityCode
  ) {}

  public record UpdateUserRequest(
      String fullName,
      @Email String email,
      String phone,
      Boolean enabled,
      BigDecimal hourlyCost,

      Integer yearOfBirth,
      String countyCode,
      String municipalityCode
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
        password
    );
  }

  // ---------- Endpoints ----------

  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('USER','STAFF','ADMIN')")
  public UserResponse me(Authentication auth) {
    var username = auth.getName();
    var user = userService.getDetailedByUsername(username);
    return toResponse(user, null);
  }

  @GetMapping("/{id}")
  public UserResponse getOne(@PathVariable Long id) {
    var r = userService.getDetailedById(id);
    return toResponse(r, null);
  }

  // GET /api/users?role=USER
  @GetMapping
  public List<UserResponse> list(@RequestParam Role role) {
    return userService.listDetailedByRole(role).stream()
        .map(r -> toResponse(r, null))
        .toList();
  }

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
        req.municipalityCode()
    );

    // password returned only here
    return toResponse(created, created.password());
  }

  @PutMapping("/{id}")
  public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {

    var updated = userService.updateWithProfile(
        id,
        req.fullName(),
        req.email(),
        req.phone(),
        req.enabled(),
        req.hourlyCost(),
        req.yearOfBirth(),
        req.countyCode(),
        req.municipalityCode()
    );

    return toResponse(updated, null);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    userService.deleteUser(id);
  }
}