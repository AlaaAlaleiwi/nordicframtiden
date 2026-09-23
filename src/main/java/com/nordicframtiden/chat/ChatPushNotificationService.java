package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class ChatPushNotificationService {
  private final ChatPushSubscriptionRepository subscriptions;
  private final AppUserRepository users;
  private final ChatPushSender sender;
  private final UserProfileRepository profiles;

  public ChatPushNotificationService(ChatPushSubscriptionRepository subscriptions,
                                     AppUserRepository users, ChatPushSender sender,
                                     UserProfileRepository profiles) {
    this.subscriptions = subscriptions;
    this.users = users;
    this.sender = sender;
    this.profiles = profiles;
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

  @Transactional(readOnly = true)
  public void notifyIncomingCall(Set<String> usernames, String caller, String callId, long roomId) {
    if (usernames.isEmpty()) return;
    String callerName = users.findByUsername(caller)
        .flatMap(user -> profiles.findByUserId(user.getId()))
        .map(profile -> profile.getFullName())
        .filter(name -> !name.isBlank())
        .orElse(caller);
    var data = Map.of(
        "title", "Incoming audio call",
        "body", callerName + " is calling",
        "url", "/chat",
        "type", "call.invite",
        "callId", callId,
        "roomId", Long.toString(roomId));
    subscriptions.findByUserUsernameIn(usernames)
        .forEach(subscription -> sender.send(subscription.getFirebaseInstallationId(), data));
  }

  @Transactional(readOnly = true)
  public void notifyChannelDeleted(Set<String> usernames, String channelName) {
    if (usernames.isEmpty()) return;
    var data = Map.of(
        "title", "Channel deleted",
        "body", "#" + channelName + " has been deleted",
        "url", "/chat",
        "type", "channel.deleted");
    subscriptions.findByUserUsernameIn(usernames)
        .forEach(subscription -> sender.send(subscription.getFirebaseInstallationId(), data));
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
