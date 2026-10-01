package com.nordicframtiden.notification;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class PushNotificationService {
  private final ApnsDeviceTokenRepository tokens;
  private final AppUserRepository users;
  private final ApnsSender sender;

  public PushNotificationService(ApnsDeviceTokenRepository tokens, AppUserRepository users, ApnsSender sender) {
    this.tokens = tokens;
    this.users = users;
    this.sender = sender;
  }

  @Transactional
  public void register(Authentication authentication, String rawToken, String environment) {
    AppUser user = current(authentication);
    String token = requireToken(rawToken);
    String cleanEnvironment = "production".equalsIgnoreCase(environment) ? "production" : "sandbox";
    ApnsDeviceToken device = tokens.findByToken(token).orElseGet(ApnsDeviceToken::new);
    device.setUser(user);
    device.setToken(token);
    device.setEnvironment(cleanEnvironment);
    device.setUpdatedAt(Instant.now());
    tokens.save(device);
  }

  @Transactional
  public void unregister(Authentication authentication, String rawToken) {
    tokens.deleteByTokenAndUserId(requireToken(rawToken), current(authentication).getId());
  }

  @Transactional(readOnly = true)
  public void notifyUser(Long userId, String type, String title, String body, Map<String, Object> data) {
    var payload = new java.util.HashMap<String, Object>(data);
    payload.put("type", type);
    tokens.findByUserId(userId).forEach(device -> sender.send(device, title, body, payload));
  }

  private AppUser current(Authentication authentication) {
    if (authentication == null) throw new IllegalArgumentException("Authentication required");
    return users.findByUsername(authentication.getName()).filter(AppUser::isEnabled)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  private static String requireToken(String rawToken) {
    String token = rawToken == null ? "" : rawToken.trim().toLowerCase();
    if (token.length() < 32 || token.length() > 200 || !token.matches("[0-9a-f]+")) {
      throw new IllegalArgumentException("Invalid APNs device token");
    }
    return token;
  }
}
