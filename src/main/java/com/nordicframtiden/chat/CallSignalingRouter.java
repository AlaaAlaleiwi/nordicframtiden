package com.nordicframtiden.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class CallSignalingRouter {
  static final int MAX_PARTICIPANTS = 4;

  private final ChatRoomMemberRepository members;
  private final Map<UUID, ActiveCall> calls = new ConcurrentHashMap<>();

  CallSignalingRouter(ObjectMapper objectMapper, ChatRoomMemberRepository members) {
    this.members = members;
  }

  synchronized Route route(String username, JsonNode message) {
    String type = requiredText(message, "type");
    if (!type.startsWith("call.")) {
      throw new IllegalArgumentException("Unsupported WebSocket event");
    }

    UUID callId = UUID.fromString(requiredText(message, "callId"));
    long roomId = message.path("roomId").asLong(0);
    if (roomId <= 0) throw new IllegalArgumentException("A valid roomId is required");

    List<String> roomMembers = members.findUsernamesByRoomId(roomId);
    if (!roomMembers.contains(username)) throw new ChatAccessDeniedException();

    return switch (type) {
      case "call.invite" -> invite(username, callId, roomId, roomMembers, message);
      case "call.join" -> join(username, callId, roomId, message);
      case "call.leave" -> leave(username, callId, roomId, roomMembers, message);
      case "call.offer", "call.answer", "call.ice" ->
          direct(username, callId, roomId, message);
      case "call.mute" ->
          broadcastToParticipants(username, callId, roomId, message);
      case "call.decline" -> decline(username, callId, roomId, message);
      default -> throw new IllegalArgumentException("Unsupported call event");
    };
  }

  synchronized void disconnected(String username) {
    calls.entrySet().removeIf(entry -> {
      entry.getValue().participants.remove(username);
      return entry.getValue().participants.isEmpty();
    });
  }

  private Route invite(String username, UUID callId, long roomId,
                       List<String> roomMembers, JsonNode message) {
    ActiveCall activeCall = calls.computeIfAbsent(
        callId, ignored -> new ActiveCall(roomId, new LinkedHashSet<>()));
    requireRoom(activeCall, roomId);
    activeCall.participants.add(username);
    Set<String> recipients = new LinkedHashSet<>();
    String target = message.path("targetUsername").asText("").trim();
    if (!target.isEmpty()) {
      if (!roomMembers.contains(target) || target.equals(username)) throw new ChatAccessDeniedException();
      recipients.add(target);
    } else {
      recipients.addAll(roomMembers);
      recipients.remove(username);
    }
    return routeFor(username, recipients, message);
  }

  private Route join(String username, UUID callId, long roomId, JsonNode message) {
    ActiveCall activeCall = requiredCall(callId, roomId);
    if (!activeCall.participants.contains(username)
        && activeCall.participants.size() >= MAX_PARTICIPANTS) {
      throw new IllegalStateException("This call already has four participants");
    }
    Set<String> recipients = new LinkedHashSet<>(activeCall.participants);
    activeCall.participants.add(username);
    recipients.remove(username);
    return routeFor(username, recipients, message);
  }

  private Route leave(String username, UUID callId, long roomId,
                      List<String> roomMembers, JsonNode message) {
    ActiveCall activeCall = requiredCall(callId, roomId);
    Set<String> recipients = new LinkedHashSet<>(activeCall.participants);
    recipients.remove(username);
    activeCall.participants.remove(username);
    if (activeCall.participants.isEmpty()) {
      recipients.addAll(roomMembers);
      recipients.remove(username);
      calls.remove(callId);
    }
    return routeFor(username, recipients, message);
  }

  private Route decline(String username, UUID callId, long roomId, JsonNode message) {
    ActiveCall activeCall = requiredCall(callId, roomId);
    Set<String> recipients = new LinkedHashSet<>(activeCall.participants);
    recipients.remove(username);
    return routeFor(username, recipients, message);
  }

  private Route direct(String username, UUID callId, long roomId, JsonNode message) {
    ActiveCall activeCall = requiredParticipant(username, callId, roomId);
    String target = requiredText(message, "targetUsername");
    if (!activeCall.participants.contains(target)) throw new ChatAccessDeniedException();
    return routeFor(username, Set.of(target), message);
  }

  private Route broadcastToParticipants(String username, UUID callId, long roomId,
                                        JsonNode message) {
    ActiveCall activeCall = requiredParticipant(username, callId, roomId);
    Set<String> recipients = new LinkedHashSet<>(activeCall.participants);
    recipients.remove(username);
    return routeFor(username, recipients, message);
  }

  private ActiveCall requiredParticipant(String username, UUID callId, long roomId) {
    ActiveCall activeCall = requiredCall(callId, roomId);
    if (!activeCall.participants.contains(username)) throw new ChatAccessDeniedException();
    return activeCall;
  }

  private ActiveCall requiredCall(UUID callId, long roomId) {
    ActiveCall activeCall = calls.get(callId);
    if (activeCall == null) throw new IllegalArgumentException("Call is no longer active");
    requireRoom(activeCall, roomId);
    return activeCall;
  }

  private void requireRoom(ActiveCall activeCall, long roomId) {
    if (activeCall.roomId != roomId) throw new ChatAccessDeniedException();
  }

  private Route routeFor(String username, Set<String> recipients, JsonNode message) {
    ObjectNode event = message.deepCopy();
    event.put("fromUsername", username);
    return new Route(recipients, event);
  }

  private String requiredText(JsonNode node, String field) {
    String value = node.path(field).asText("").trim();
    if (value.isEmpty()) throw new IllegalArgumentException(field + " is required");
    return value;
  }

  record Route(Set<String> recipients, ObjectNode event) {}
  private record ActiveCall(long roomId, Set<String> participants) {}
}
