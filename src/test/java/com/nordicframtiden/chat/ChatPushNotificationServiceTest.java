package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPushNotificationServiceTest {

  @Test
  void registersInstallationForAuthenticatedUser() {
    ChatPushSubscriptionRepository subscriptions = mock(ChatPushSubscriptionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatPushSender sender = mock(ChatPushSender.class);
    AppUser user = user(7L, "anna");
    when(users.findByUsername("anna")).thenReturn(Optional.of(user));
    when(subscriptions.findByFirebaseInstallationId("fid-123"))
        .thenReturn(Optional.empty());
    when(subscriptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    ChatPushNotificationService service = new ChatPushNotificationService(subscriptions, users, sender);

    ChatPushSubscription saved = service.register(authentication("anna"), "fid-123");

    assertThat(saved.getUser()).isSameAs(user);
    assertThat(saved.getFirebaseInstallationId()).isEqualTo("fid-123");
  }

  @Test
  void sendsNewMessageNotificationToOtherRoomMembersOnly() {
    ChatPushSubscriptionRepository subscriptions = mock(ChatPushSubscriptionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatPushSender sender = mock(ChatPushSender.class);
    ChatPushNotificationService service = new ChatPushNotificationService(subscriptions, users, sender);
    ChatRoom room = new ChatRoom();
    room.setId(12L);
    ChatMessage message = new ChatMessage();
    message.setId(99L);
    message.setRoom(room);
    message.setSender(user(7L, "anna"));
    message.setBody("Hello team");
    when(subscriptions.findForRoomExceptSender(12L, 7L))
        .thenReturn(List.of(subscription("fid-erik"), subscription("fid-sara")));

    service.notifyNewMessage(message);

    verify(sender).send("fid-erik");
    verify(sender).send("fid-sara");
  }

  private static ChatPushSubscription subscription(String fid) {
    ChatPushSubscription subscription = new ChatPushSubscription();
    subscription.setFirebaseInstallationId(fid);
    return subscription;
  }

  private static UsernamePasswordAuthenticationToken authentication(String username) {
    return new UsernamePasswordAuthenticationToken(username, null);
  }

  private static AppUser user(Long id, String username) {
    AppUser user = new AppUser();
    user.setId(id);
    user.setUsername(username);
    user.setEnabled(true);
    return user;
  }
}
