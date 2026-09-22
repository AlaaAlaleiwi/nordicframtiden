package com.nordicframtiden.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/notifications")
public class ChatNotificationController {
  private final ChatPushNotificationService notifications;
  private final FirebaseNotificationProperties properties;

  public ChatNotificationController(ChatPushNotificationService notifications,
                                    FirebaseNotificationProperties properties) {
    this.notifications = notifications;
    this.properties = properties;
  }

  public record SubscriptionRequest(
      @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9_-]+") String installationId) {}
  public record ConfigurationResponse(boolean enabled, String apiKey, String authDomain,
                                      String projectId, String storageBucket,
                                      String messagingSenderId, String appId, String vapidKey) {}

  @GetMapping("/config")
  public ConfigurationResponse config() {
    return new ConfigurationResponse(properties.isEnabled(), properties.getWebApiKey(),
        properties.getAuthDomain(), properties.getProjectId(), properties.getStorageBucket(),
        properties.getMessagingSenderId(), properties.getAppId(), properties.getVapidPublicKey());
  }

  @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void subscribe(Authentication authentication, @Valid @RequestBody SubscriptionRequest request) {
    notifications.register(authentication, request.installationId());
  }

  @DeleteMapping("/subscriptions/{installationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsubscribe(Authentication authentication, @PathVariable String installationId) {
    notifications.unregister(authentication, installationId);
  }
}
