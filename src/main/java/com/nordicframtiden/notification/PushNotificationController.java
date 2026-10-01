package com.nordicframtiden.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/devices")
public class PushNotificationController {
  private final PushNotificationService notifications;

  public PushNotificationController(PushNotificationService notifications) {
    this.notifications = notifications;
  }

  public record DeviceRequest(
      @NotBlank @Size(min = 32, max = 200) @Pattern(regexp = "[0-9A-Fa-f]+") String token,
      @Pattern(regexp = "sandbox|production") String environment) {}

  @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
  public void register(Authentication authentication, @Valid @RequestBody DeviceRequest request) {
    notifications.register(authentication, request.token(), request.environment());
  }

  @DeleteMapping("/{token}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(Authentication authentication, @PathVariable String token) {
    notifications.unregister(authentication, token);
  }
}
