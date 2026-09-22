package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ChatService {
  private final ChatRoomRepository rooms;
  private final ChatRoomMemberRepository members;
  private final ChatMessageRepository messages;
  private final ChatReactionRepository reactions;
  private final AppUserRepository users;
  private final ChatEventPublisher events;
  private final ChatPushNotificationService notifications;

  public ChatService(ChatRoomRepository rooms, ChatRoomMemberRepository members,
                     ChatMessageRepository messages, ChatReactionRepository reactions,
                     AppUserRepository users, ChatEventPublisher events,
                     ChatPushNotificationService notifications) {
    this.rooms = rooms;
    this.members = members;
    this.messages = messages;
    this.reactions = reactions;
    this.users = users;
    this.events = events;
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public List<ChatRoom> visibleRooms(Authentication auth) {
    return rooms.findVisibleTo(current(auth).getId());
  }

  @Transactional
  public ChatRoom createChannel(Authentication auth, String name, String description,
                                boolean privateChannel, List<Long> memberIds) {
    AppUser creator = current(auth);
    String cleanName = requireText(name, 80, "Channel name").toLowerCase().replace(' ', '-');
    ChatRoom room = new ChatRoom();
    room.setType(ChatRoom.Type.CHANNEL);
    room.setName(cleanName);
    room.setDescription(cleanOptional(description, 500));
    room.setPrivateChannel(privateChannel);
    room.setCreatedBy(creator);
    room = rooms.save(room);

    LinkedHashSet<Long> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
    ids.add(creator.getId());
    for (Long id : ids) addMember(room, users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found")));
    return room;
  }

  @Transactional
  public ChatRoom direct(Authentication auth, Long otherUserId) {
    AppUser me = current(auth);
    if (me.getId().equals(otherUserId)) throw new IllegalArgumentException("Cannot message yourself");
    AppUser other = users.findById(otherUserId).filter(AppUser::isEnabled)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return rooms.findDirectBetween(me.getId(), otherUserId).orElseGet(() -> {
      ChatRoom room = new ChatRoom();
      room.setType(ChatRoom.Type.DIRECT);
      room.setPrivateChannel(true);
      room.setCreatedBy(me);
      room = rooms.save(room);
      addMember(room, me);
      addMember(room, other);
      return room;
    });
  }

  @Transactional
  public void join(Authentication auth, Long roomId) {
    AppUser user = current(auth);
    ChatRoom room = room(roomId);
    if (room.getType() != ChatRoom.Type.CHANNEL || room.isPrivateChannel()) throw new ChatAccessDeniedException();
    if (!members.existsByRoomIdAndUserId(roomId, user.getId())) addMember(room, user);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> messages(Authentication auth, Long roomId, Long parentId, int limit) {
    AppUser user = current(auth);
    requireMember(roomId, user.getId());
    int safeLimit = Math.max(1, Math.min(limit, 100));
    if (parentId != null) {
      ChatMessage parent = message(parentId);
      if (!parent.getRoom().getId().equals(roomId)) throw new IllegalArgumentException("Thread is not in room");
      return messages.findByParentIdOrderByIdAsc(parentId);
    }
    List<ChatMessage> result = messages.findByRoomIdAndParentIsNullOrderByIdDesc(roomId, PageRequest.of(0, safeLimit));
    return result.reversed();
  }

  @Transactional
  public ChatMessage send(Authentication auth, Long roomId, Long parentId, String body) {
    AppUser user = current(auth);
    ChatRoom room = room(roomId);
    requireMember(roomId, user.getId());
    ChatMessage parent = parentId == null ? null : message(parentId);
    if (parent != null && (!parent.getRoom().getId().equals(roomId) || parent.getParent() != null)) {
      throw new IllegalArgumentException("Invalid thread parent");
    }
    ChatMessage result = new ChatMessage();
    result.setRoom(room);
    result.setSender(user);
    result.setParent(parent);
    result.setBody(requireText(body, 4000, "Message"));
    result = messages.save(result);
    events.publish(roomId, "message.created", result.getId());
    notifications.notifyNewMessage(result);
    return result;
  }

  @Transactional
  public ChatMessage edit(Authentication auth, Long messageId, String body) {
    AppUser user = current(auth);
    ChatMessage message = message(messageId);
    if (!message.getSender().getId().equals(user.getId()) || message.getDeletedAt() != null) throw new ChatAccessDeniedException();
    message.setBody(requireText(body, 4000, "Message"));
    message.setEditedAt(Instant.now());
    message = messages.save(message);
    events.publish(message.getRoom().getId(), "message.updated", message.getId());
    return message;
  }

  @Transactional
  public void delete(Authentication auth, Long messageId) {
    AppUser user = current(auth);
    ChatMessage message = message(messageId);
    if (!message.getSender().getId().equals(user.getId())) throw new ChatAccessDeniedException();
    message.setDeletedAt(Instant.now());
    message.setBody("Message deleted");
    messages.save(message);
    events.publish(message.getRoom().getId(), "message.deleted", message.getId());
  }

  @Transactional
  public void toggleReaction(Authentication auth, Long messageId, String emoji) {
    AppUser user = current(auth);
    ChatMessage message = message(messageId);
    requireMember(message.getRoom().getId(), user.getId());
    String cleanEmoji = requireText(emoji, 32, "Emoji");
    var existing = reactions.findByMessageIdAndUserIdAndEmoji(messageId, user.getId(), cleanEmoji);
    if (existing.isPresent()) reactions.delete(existing.get());
    else {
      ChatReaction reaction = new ChatReaction();
      reaction.setMessage(message); reaction.setUser(user); reaction.setEmoji(cleanEmoji);
      reactions.save(reaction);
    }
    events.publish(message.getRoom().getId(), "reaction.changed", messageId);
  }

  @Transactional
  public void markRead(Authentication auth, Long roomId, Long messageId) {
    AppUser user = current(auth);
    ChatRoomMember member = members.findByRoomIdAndUserId(roomId, user.getId()).orElseThrow(ChatAccessDeniedException::new);
    if (messageId != null && !message(messageId).getRoom().getId().equals(roomId)) throw new IllegalArgumentException("Message is not in room");
    member.setLastReadMessageId(messageId);
    members.save(member);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long roomId, Long userId) {
    return members.findByRoomIdAndUserId(roomId, userId)
        .map(member -> messages.countUnread(roomId, member.getLastReadMessageId() == null ? 0 : member.getLastReadMessageId(), userId))
        .orElse(0L);
  }

  public AppUser current(Authentication auth) {
    return users.findByUsername(auth.getName()).filter(AppUser::isEnabled)
        .orElseThrow(() -> new ChatAccessDeniedException());
  }

  private void addMember(ChatRoom room, AppUser user) {
    ChatRoomMember member = new ChatRoomMember(); member.setRoom(room); member.setUser(user); members.save(member);
  }
  private void requireMember(Long roomId, Long userId) {
    if (!members.existsByRoomIdAndUserId(roomId, userId)) throw new ChatAccessDeniedException();
  }
  private ChatRoom room(Long id) { return rooms.findById(id).orElseThrow(() -> new IllegalArgumentException("Room not found")); }
  private ChatMessage message(Long id) { return messages.findById(id).orElseThrow(() -> new IllegalArgumentException("Message not found")); }
  private static String requireText(String value, int max, String field) {
    String clean = value == null ? "" : value.trim();
    if (clean.isEmpty() || clean.length() > max) throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
    return clean;
  }
  private static String cleanOptional(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String clean = value.trim();
    if (clean.length() > max) throw new IllegalArgumentException("Value is too long");
    return clean;
  }
}
