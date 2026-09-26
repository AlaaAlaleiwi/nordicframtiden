package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

  @Test
  void rejectsMessageFromUserOutsideRoom() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    AppUser user = user(7L, "anna");

    when(users.findByUsername("anna")).thenReturn(Optional.of(user));
    when(rooms.findById(12L)).thenReturn(Optional.of(new ChatRoom()));
    when(members.existsByRoomIdAndUserId(12L, 7L)).thenReturn(false);

    assertThatThrownBy(() -> service.send(authentication("anna"), 12L, null, "Hello"))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void trimsAndPersistsMessageFromMember() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    AppUser user = user(7L, "anna");
    ChatRoom room = new ChatRoom();
    room.setId(12L);

    when(users.findByUsername("anna")).thenReturn(Optional.of(user));
    when(rooms.findById(12L)).thenReturn(Optional.of(room));
    when(members.existsByRoomIdAndUserId(12L, 7L)).thenReturn(true);
    when(messages.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
      ChatMessage saved = invocation.getArgument(0);
      saved.setId(99L);
      return saved;
    });

    ChatMessage saved = service.send(authentication("anna"), 12L, null, "  Hello team  ");

    assertThat(saved.getBody()).isEqualTo("Hello team");
    assertThat(saved.getSender()).isSameAs(user);
    assertThat(saved.getRoom()).isSameAs(room);
    org.mockito.Mockito.verify(notifications).notifyNewMessage(saved);
  }

  @Test
  void onlyAuthorCanEditMessage() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    AppUser author = user(7L, "anna");
    AppUser other = user(8L, "erik");
    ChatMessage message = new ChatMessage();
    message.setId(99L);
    message.setSender(author);

    when(users.findByUsername("erik")).thenReturn(Optional.of(other));
    when(messages.findById(99L)).thenReturn(Optional.of(message));

    assertThatThrownBy(() -> service.edit(authentication("erik"), 99L, "Changed"))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void messagesAfterReturnsOnlyMessagesNewerThanCursor() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    AppUser user = user(7L, "anna");

    when(users.findByUsername("anna")).thenReturn(Optional.of(user));
    when(members.existsByRoomIdAndUserId(12L, 7L)).thenReturn(true);
    when(messages.findByRoomIdAndParentIsNullAndIdAfterOrderByIdAsc(12L, 40L, PageRequest.of(0, 50)))
        .thenReturn(List.of(message(41L), message(42L)));

    var delta = service.messagesAfter(authentication("anna"), 12L, null, 40L, 50);

    assertThat(delta).extracting(ChatMessage::getId).containsExactly(41L, 42L);
  }

  @Test
  void messagesAfterWithoutCursorFallsBackToNewestPage() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);

    when(users.findByUsername("anna")).thenReturn(Optional.of(user(7L, "anna")));
    when(members.existsByRoomIdAndUserId(12L, 7L)).thenReturn(true);
    when(messages.findByRoomIdAndParentIsNullOrderByIdDesc(12L, PageRequest.of(0, 50)))
        .thenReturn(new java.util.ArrayList<>(List.of(message(41L), message(42L))));

    assertThat(service.messagesAfter(authentication("anna"), 12L, null, null, 50))
        .extracting(ChatMessage::getId).containsExactly(42L, 41L);
  }

  @Test
  void memberCanFetchSingleMessageById() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    ChatMessage message = message(99L);
    ChatRoom room = new ChatRoom();
    room.setId(12L);
    message.setRoom(room);

    when(users.findByUsername("anna")).thenReturn(Optional.of(user(7L, "anna")));
    when(messages.findById(99L)).thenReturn(Optional.of(message));
    when(members.existsByRoomIdAndUserId(12L, 7L)).thenReturn(true);

    assertThat(service.messageForUser(authentication("anna"), 99L)).isSameAs(message);
  }

  @Test
  void nonMemberCannotFetchSingleMessageById() {
    ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    ChatReactionRepository reactions = mock(ChatReactionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    ChatEventPublisher events = mock(ChatEventPublisher.class);
    ChatPushNotificationService notifications = mock(ChatPushNotificationService.class);
    ChatService service = new ChatService(rooms, members, messages, reactions, users, events, notifications);
    ChatMessage message = message(99L);
    ChatRoom room = new ChatRoom();
    room.setId(12L);
    message.setRoom(room);

    when(users.findByUsername("erik")).thenReturn(Optional.of(user(8L, "erik")));
    when(messages.findById(99L)).thenReturn(Optional.of(message));
    when(members.existsByRoomIdAndUserId(12L, 8L)).thenReturn(false);

    assertThatThrownBy(() -> service.messageForUser(authentication("erik"), 99L))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  private static ChatMessage message(Long id) {
    ChatMessage message = new ChatMessage();
    message.setId(id);
    return message;
  }

  private static UsernamePasswordAuthenticationToken authentication(String username) {
    return new UsernamePasswordAuthenticationToken(username, null);
  }

  private static AppUser user(Long id, String username) {
    AppUser user = new AppUser();
    user.setId(id);
    user.setUsername(username);
    return user;
  }
}
