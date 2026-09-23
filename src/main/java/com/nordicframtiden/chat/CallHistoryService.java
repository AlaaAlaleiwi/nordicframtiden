package com.nordicframtiden.chat;

import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CallHistoryService {
  private final CallHistoryRepository history;
  private final ChatRoomRepository rooms;
  private final AppUserRepository users;
  private final ChatRoomMemberRepository members;

  CallHistoryService(CallHistoryRepository history, ChatRoomRepository rooms,
                     AppUserRepository users, ChatRoomMemberRepository members) {
    this.history = history;
    this.rooms = rooms;
    this.users = users;
    this.members = members;
  }

  @Transactional
  public void started(UUID callId, long roomId, String callerUsername) {
    if (history.existsById(callId)) return;
    CallHistory item = new CallHistory();
    item.setCallId(callId);
    item.setRoom(rooms.findById(roomId).orElseThrow());
    item.setCaller(users.findByUsername(callerUsername).orElseThrow());
    history.save(item);
  }

  @Transactional
  public void answered(UUID callId) {
    history.findById(callId).ifPresent(item -> {
      if (item.getAnsweredAt() == null) item.setAnsweredAt(Instant.now());
      item.setOutcome("COMPLETED");
    });
  }

  @Transactional
  public void ended(UUID callId, String outcome) {
    history.findById(callId).ifPresent(item -> {
      item.setEndedAt(Instant.now());
      if (item.getAnsweredAt() == null) item.setOutcome(outcome);
      else item.setOutcome("COMPLETED");
    });
  }

  @Transactional(readOnly = true)
  public List<Item> list(String username) {
    return history.findForUser(username, PageRequest.of(0, 100)).stream()
        .map(item -> new Item(
            item.getCallId(), item.getRoom().getId(), displayName(item, username),
            item.getRoom().getType().name(), item.getCaller().getUsername(),
            item.getStartedAt(), item.getAnsweredAt(), item.getEndedAt(),
            item.getEndedAt() != null && item.getAnsweredAt() != null
                ? Duration.between(item.getAnsweredAt(), item.getEndedAt()).toSeconds() : 0,
            item.getOutcome()))
        .toList();
  }

  private String displayName(CallHistory item, String username) {
    if (item.getRoom().getType() == ChatRoom.Type.CHANNEL) {
      return item.getRoom().getName();
    }
    return members.findUsernamesByRoomId(item.getRoom().getId()).stream()
        .filter(member -> !member.equals(username))
        .findFirst()
        .orElse(item.getCaller().getUsername());
  }

  public record Item(UUID callId, Long roomId, String roomName, String roomType,
                     String callerUsername, Instant startedAt, Instant answeredAt,
                     Instant endedAt, long durationSeconds, String outcome) {}
}
