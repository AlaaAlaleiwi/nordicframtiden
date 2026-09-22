package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ChatPushNotificationService {
  private final ChatPushSubscriptionRepository subscriptions;
  private final AppUserRepository users;
  private final ChatPushSender sender;

  public ChatPushNotificationService(ChatPushSubscriptionRepository subscriptions,
                                     AppUserRepository users, ChatPushSender sender) {
    this.subscriptions = subscriptions;
    this.users = users;
    this.sender = sender;
  }

  @Transactional
  public ChatPushSubscription register(Authentication authentication, String installationId) {
    AppUser user = current(authentication);
    String cleanId = requireInstallationId(installationId);
    ChatPushSubscription subscription = subscriptions.findByFirebaseInstallationId(cleanId)
        .orElseGet(ChatPushSubscription::new);
    subscription.setUser(user);
    subscription.setFirebaseInstallationId(cleanId);
    subscription.setUpdatedAt(Instant.now());
    return subscriptions.save(subscription);
  }

  @Transactional
  public void unregister(Authentication authentication, String installationId) {
    AppUser user = current(authentication);
    subscriptions.deleteByFirebaseInstallationIdAndUserId(requireInstallationId(installationId), user.getId());
  }

  @Transactional(readOnly = true)
  public void notifyNewMessage(ChatMessage message) {
    subscriptions.findForRoomExceptSender(message.getRoom().getId(), message.getSender().getId())
        .forEach(subscription -> sender.send(subscription.getFirebaseInstallationId()));
  }

  private AppUser current(Authentication authentication) {
    if (authentication == null) throw new ChatAccessDeniedException();
    return users.findByUsername(authentication.getName()).filter(AppUser::isEnabled)
        .orElseThrow(ChatAccessDeniedException::new);
  }

  private static String requireInstallationId(String installationId) {
    String clean = installationId == null ? "" : installationId.trim();
    if (clean.isEmpty() || clean.length() > 255 || !clean.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("Invalid Firebase installation ID");
    }
    return clean;
  }
}
