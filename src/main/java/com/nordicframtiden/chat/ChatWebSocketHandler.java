package com.nordicframtiden.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements ChatEventPublisher, ChatPresence {
  private final ObjectMapper objectMapper;
  private final ChatRoomMemberRepository members;
  private final CallSignalingRouter callRouter;
  private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
  // Prunes silently-dead sockets (sleep/wake, crashes, network switches).
  // Without this, call signals and presence events are routed into dead
  // sessions and never reach the (reconnected) client.
  private final ScheduledExecutorService livenessPinger = Executors.newSingleThreadScheduledExecutor();

  public ChatWebSocketHandler(ObjectMapper objectMapper, ChatRoomMemberRepository members,
                              CallSignalingRouter callRouter) {
    this.objectMapper = objectMapper;
    this.members = members;
    this.callRouter = callRouter;
  }

  @PostConstruct
  void startLivenessPinger() {
    livenessPinger.scheduleAtFixedRate(this::pingAllSessions, 30, 30, TimeUnit.SECONDS);
  }

  @PreDestroy
  void stopLivenessPinger() {
    livenessPinger.shutdownNow();
  }

  private void pingAllSessions() {
    for (var entry : sessions.entrySet()) {
      for (WebSocketSession session : Set.copyOf(entry.getValue())) {
        try {
          synchronized (session) { session.sendMessage(new PingMessage()); }
        } catch (Exception ignored) {
          dropSession(entry.getKey(), session);
        }
      }
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    if (session.getPrincipal() != null) {
      dropSession(session.getPrincipal().getName(), session);
    }
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
    removeSession(session.getPrincipal().getName(), session);
  }

  /** Removes one session; runs the user-level disconnect path on the last one. */
  private synchronized void removeSession(String username, WebSocketSession session) {
    Set<WebSocketSession> userSessions = sessions.get(username);
    if (userSessions == null) return;
    userSessions.remove(session);
    if (!userSessions.isEmpty()) return;
    sessions.remove(username);
    for (var route : callRouter.disconnected(username)) {
      sendTo(route.recipients(), route.event());
    }
    broadcastPresence(username, false);
  }

  private void dropSession(String username, WebSocketSession session) {
    removeSession(username, session);
    try { session.close(CloseStatus.SESSION_NOT_RELIABLE); } catch (IOException ignored) { }
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

  void routeCallSignal(String username, com.fasterxml.jackson.databind.JsonNode signal) {
    try {
      var route = callRouter.route(username, signal);
      sendTo(route.recipients(), route.event());
    } catch (RuntimeException error) {
      sendTo(Set.of(username), Map.of("type", "call.error", "message", error.getMessage()));
      throw error;
    }
  }

  java.util.List<CallSignalingRouter.ActiveChannelCall> activeChannelCalls(String username) {
    return callRouter.activeChannelCalls(username);
  }

  java.util.Map<String, CallSignalingRouter.UserCallState> activeCallStates(String username) {
    return callRouter.activeCallStates(username);
  }

  @Override
  public void publish(Long roomId, String type, Object payload) {
    sendTo(members.findUsernamesByRoomId(roomId), Map.of("type", type, "roomId", roomId, "payload", payload));
  }

  @Override
  public void publishTo(Set<String> usernames, Long roomId, String type, Object payload) {
    sendTo(usernames, Map.of("type", type, "roomId", roomId, "payload", payload));
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
