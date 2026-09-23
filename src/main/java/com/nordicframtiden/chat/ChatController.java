package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
  private final ChatService service;
  private final ChatRoomMemberRepository members;
  private final ChatMessageRepository messages;
  private final ChatReactionRepository reactions;
  private final AppUserRepository users;
  private final UserProfileRepository profiles;
  private final ChatPresence presence;

  public ChatController(ChatService service, ChatRoomMemberRepository members,
                        ChatMessageRepository messages, ChatReactionRepository reactions,
                        AppUserRepository users, UserProfileRepository profiles, ChatPresence presence) {
    this.service = service; this.members = members; this.messages = messages; this.reactions = reactions;
    this.users = users; this.profiles = profiles; this.presence = presence;
  }

  public record ParticipantDto(Long id, String username, String displayName, boolean online) {}
  public record RoomDto(Long id, String type, String name, String description, boolean privateChannel,
                        boolean member, boolean canManage, boolean owner, Long ownerUserId,
                        long unreadCount, List<ParticipantDto> participants,
                        List<Long> adminUserIds) {}
  public record ReactionDto(String emoji, long count, boolean mine) {}
  public record MessageDto(Long id, Long roomId, Long parentId, ParticipantDto sender, String body,
                           Instant createdAt, Instant editedAt, boolean deleted, long replyCount,
                           List<ReactionDto> reactions) {}
  public record CreateChannelRequest(@NotBlank @Size(max=80) String name, @Size(max=500) String description,
                                     boolean privateChannel, List<Long> memberIds) {}
  public record AddChannelMembersRequest(@NotEmpty List<@NotNull Long> userIds) {}
  public record AddChannelAdminsRequest(@NotEmpty List<@NotNull Long> userIds) {}
  public record DirectRequest(@NotNull Long userId) {}
  public record SendMessageRequest(Long parentId, @NotBlank @Size(max=4000) String body) {}
  public record EditMessageRequest(@NotBlank @Size(max=4000) String body) {}
  public record ReactionRequest(@NotBlank @Size(max=32) String emoji) {}
  public record ReadRequest(Long messageId) {}

  @GetMapping("/participants")
  public List<ParticipantDto> participants(Authentication auth) {
    Long me = service.current(auth).getId();
    return users.findAll().stream().filter(AppUser::isEnabled).filter(user -> !user.getId().equals(me))
        .map(this::participant).sorted(Comparator.comparing(ParticipantDto::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
  }

  @GetMapping("/rooms")
  public List<RoomDto> rooms(Authentication auth) {
    AppUser me = service.current(auth);
    return service.visibleRooms(auth).stream().map(room -> room(room, me)).toList();
  }

  @PostMapping("/channels") @ResponseStatus(HttpStatus.CREATED)
  public RoomDto createChannel(Authentication auth, @Valid @RequestBody CreateChannelRequest request) {
    return room(service.createChannel(auth, request.name(), request.description(), request.privateChannel(), request.memberIds()), service.current(auth));
  }

  @PostMapping("/direct")
  public RoomDto direct(Authentication auth, @Valid @RequestBody DirectRequest request) {
    return room(service.direct(auth, request.userId()), service.current(auth));
  }

  @PostMapping("/rooms/{roomId}/join") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void join(Authentication auth, @PathVariable Long roomId) { service.join(auth, roomId); }

  @PostMapping("/channels/{roomId}/members")
  public RoomDto addChannelMembers(Authentication auth, @PathVariable Long roomId,
                                   @Valid @RequestBody AddChannelMembersRequest request) {
    return room(service.addChannelMembers(auth, roomId, request.userIds()), service.current(auth));
  }

  @PostMapping("/channels/{roomId}/admins")
  public RoomDto addChannelAdmins(Authentication auth, @PathVariable Long roomId,
                                  @Valid @RequestBody AddChannelAdminsRequest request) {
    return room(service.addChannelAdmins(auth, roomId, request.userIds()), service.current(auth));
  }

  @DeleteMapping("/channels/{roomId}/admins/{userId}")
  public RoomDto removeChannelAdmin(Authentication auth, @PathVariable Long roomId,
                                    @PathVariable Long userId) {
    return room(service.removeChannelAdmin(auth, roomId, userId), service.current(auth));
  }

  @DeleteMapping("/channels/{roomId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteChannel(Authentication auth, @PathVariable Long roomId) {
    service.deleteChannel(auth, roomId);
  }

  @GetMapping("/rooms/{roomId}/messages")
  public List<MessageDto> messages(Authentication auth, @PathVariable Long roomId,
                                   @RequestParam(required=false) Long parentId,
                                   @RequestParam(defaultValue="50") int limit) {
    AppUser me = service.current(auth);
    return service.messages(auth, roomId, parentId, limit).stream().map(message -> message(message, me)).toList();
  }

  @PostMapping("/rooms/{roomId}/messages") @ResponseStatus(HttpStatus.CREATED)
  public MessageDto send(Authentication auth, @PathVariable Long roomId, @Valid @RequestBody SendMessageRequest request) {
    AppUser me = service.current(auth);
    return message(service.send(auth, roomId, request.parentId(), request.body()), me);
  }

  @PutMapping("/messages/{messageId}")
  public MessageDto edit(Authentication auth, @PathVariable Long messageId, @Valid @RequestBody EditMessageRequest request) {
    AppUser me = service.current(auth);
    return message(service.edit(auth, messageId, request.body()), me);
  }

  @DeleteMapping("/messages/{messageId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication auth, @PathVariable Long messageId) { service.delete(auth, messageId); }

  @PostMapping("/messages/{messageId}/reactions") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reaction(Authentication auth, @PathVariable Long messageId, @Valid @RequestBody ReactionRequest request) {
    service.toggleReaction(auth, messageId, request.emoji());
  }

  @PostMapping("/rooms/{roomId}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(Authentication auth, @PathVariable Long roomId, @RequestBody ReadRequest request) {
    service.markRead(auth, roomId, request.messageId());
  }

  private RoomDto room(ChatRoom room, AppUser me) {
    List<ParticipantDto> roomParticipants = members.findByRoomId(room.getId()).stream()
        .map(ChatRoomMember::getUser).map(this::participant).toList();
    boolean isMember = roomParticipants.stream().anyMatch(person -> person.id().equals(me.getId()));
    String displayName = room.getName();
    if (room.getType() == ChatRoom.Type.DIRECT) {
      displayName = roomParticipants.stream().filter(person -> !person.id().equals(me.getId()))
          .map(ParticipantDto::displayName).findFirst().orElse("Direct message");
    }
    List<ChatRoomMember> memberships = members.findByRoomId(room.getId());
    List<Long> adminUserIds = memberships.stream().filter(ChatRoomMember::isChannelAdmin)
        .map(member -> member.getUser().getId()).toList();
    boolean owner = room.getType() == ChatRoom.Type.CHANNEL && room.getCreatedBy().getId().equals(me.getId());
    boolean canManage = room.getType() == ChatRoom.Type.CHANNEL
        && (owner || adminUserIds.contains(me.getId()));
    return new RoomDto(room.getId(), room.getType().name(), displayName, room.getDescription(),
        room.isPrivateChannel(), isMember, canManage, owner, room.getCreatedBy().getId(),
        isMember ? service.unreadCount(room.getId(), me.getId()) : 0, roomParticipants, adminUserIds);
  }

  private MessageDto message(ChatMessage message, AppUser me) {
    Map<String, List<ChatReaction>> grouped = reactions.findByMessageId(message.getId()).stream()
        .collect(Collectors.groupingBy(ChatReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
    List<ReactionDto> reactionDtos = grouped.entrySet().stream()
        .map(entry -> new ReactionDto(entry.getKey(), entry.getValue().size(),
            entry.getValue().stream().anyMatch(reaction -> reaction.getUser().getId().equals(me.getId())))).toList();
    return new MessageDto(message.getId(), message.getRoom().getId(),
        message.getParent() == null ? null : message.getParent().getId(), participant(message.getSender()),
        message.getBody(), message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt() != null,
        messages.countByParentId(message.getId()), reactionDtos);
  }

  private ParticipantDto participant(AppUser user) {
    String displayName = profiles.findByUserId(user.getId()).map(profile -> profile.getFullName()).filter(name -> !name.isBlank())
        .orElse(user.getUsername());
    return new ParticipantDto(user.getId(), user.getUsername(), displayName, presence.isOnline(user.getUsername()));
  }
}
