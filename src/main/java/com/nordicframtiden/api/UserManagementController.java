package com.nordicframtiden.api;

import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

  private final UserService userService;

  public UserManagementController(UserService userService) {
    this.userService = userService;
  }

  public record UserResponse(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone
  ) {}

  public record CreateUserRequest(
      @NotBlank String fullName,
      @NotBlank @Email String email,
      @NotBlank String phone,
      Boolean enabled
  ) {}

  public record UpdateUserRequest(
      String fullName,
      @Email String email,
      String phone,
      Boolean enabled
  ) {}

  // GET /api/users?role=USER
  @GetMapping
  public List<UserResponse> list(@RequestParam Role role) {
    return userService.listDetailedByRole(role).stream()
        .map(r -> new UserResponse(r.id(), r.username(), r.enabled(), r.fullName(), r.email(), r.phone()))
        .toList();
  }

  // POST /api/users?role=USER
  @PostMapping
  public UserResponse create(@RequestParam Role role, @RequestBody CreateUserRequest req) {
    boolean enabled = req.enabled() == null || req.enabled();

    var created = userService.createWithProfile(
        role,
        enabled,
        req.fullName(),
        req.email(),
        req.phone()
    );

    return new UserResponse(created.id(), created.username(), created.enabled(), created.fullName(), created.email(), created.phone());
  }

  // PUT /api/users/{id}
  @PutMapping("/{id}")
  public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
    var updated = userService.updateWithProfile(
        id,
        req.fullName(),
        req.email(),
        req.phone(),
        req.enabled()
    );

    return new UserResponse(updated.id(), updated.username(), updated.enabled(), updated.fullName(), updated.email(), updated.phone());
  }

  // DELETE /api/users/{id}
  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    userService.deleteUser(id);
  }
}