package com.nordicframtiden.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements ChatEventPublisher, ChatPresence {
  private final ObjectMapper objectMapper;
  private final ChatRoomMemberRepository members;
  private final CallSignalingRouter callRouter;
  private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public ChatWebSocketHandler(ObjectMapper objectMapper, ChatRoomMemberRepository members,
                              CallSignalingRouter callRouter) {
    this.objectMapper = objectMapper;
    this.members = members;
    this.callRouter = callRouter;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    if (session.getPrincipal() == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
    String username = session.getPrincipal().getName();
    sessions.computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    broadcastPresence(username, true);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    if (session.getPrincipal() == null) return;
    String username = session.getPrincipal().getName();
    Set<WebSocketSession> userSessions = sessions.get(username);
    if (userSessions != null) {
      userSessions.remove(session);
      if (userSessions.isEmpty()) {
        sessions.remove(username);
        for (var route : callRouter.disconnected(username)) {
          sendTo(route.recipients(), route.event());
        }
        broadcastPresence(username, false);
      }
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    if (session.getPrincipal() == null) return;
    try {
      var route = callRouter.route(
          session.getPrincipal().getName(),
          objectMapper.readTree(message.getPayload()));
      sendTo(route.recipients(), route.event());
    } catch (RuntimeException | IOException error) {
      sendTo(
          Set.of(session.getPrincipal().getName()),
          Map.of("type", "call.error", "message", error.getMessage()));
    }
  }

  @Override
  public void publish(Long roomId, String type, Object payload) {
    sendTo(members.findUsernamesByRoomId(roomId), Map.of("type", type, "roomId", roomId, "payload", payload));
  }

  @Override
  public boolean isOnline(String username) {
    return sessions.containsKey(username);
  }

  private void broadcastPresence(String username, boolean online) {
    sendTo(sessions.keySet(), Map.of("type", "presence.changed", "username", username, "online", online));
  }

  private void sendTo(Iterable<String> usernames, Object event) {
    try {
      TextMessage message = new TextMessage(objectMapper.writeValueAsString(event));
      for (String username : usernames) {
        for (WebSocketSession session : sessions.getOrDefault(username, Set.of())) {
          if (!session.isOpen()) continue;
          try { synchronized (session) { session.sendMessage(message); } } catch (IOException ignored) { }
        }
      }
    } catch (IOException ignored) { }
  }
}
