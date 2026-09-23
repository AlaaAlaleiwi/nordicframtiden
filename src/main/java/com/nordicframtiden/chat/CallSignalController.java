package com.nordicframtiden.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/calls")
class CallSignalController {
  private final ChatWebSocketHandler sockets;
  private final ObjectMapper objectMapper;

  CallSignalController(ChatWebSocketHandler sockets, ObjectMapper objectMapper) {
    this.sockets = sockets;
    this.objectMapper = objectMapper;
  }

  record TerminalSignalRequest(@NotNull Long roomId, String message) {}

  @PostMapping("/{callId}/join")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void join(Authentication authentication, @PathVariable UUID callId,
            @Valid @RequestBody TerminalSignalRequest request) {
    var signal = objectMapper.createObjectNode();
    signal.put("type", "call.join");
    signal.put("callId", callId.toString());
    signal.put("roomId", request.roomId());
    sockets.routeCallSignal(authentication.getName(), signal);
  }

  @PostMapping("/{callId}/decline")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void decline(Authentication authentication, @PathVariable UUID callId,
               @Valid @RequestBody TerminalSignalRequest request) {
    var signal = objectMapper.createObjectNode();
    signal.put("type", "call.decline");
    signal.put("callId", callId.toString());
    signal.put("roomId", request.roomId());
    if (request.message() != null && !request.message().isBlank()) {
      signal.put("message", request.message().trim());
    }
    sockets.routeCallSignal(authentication.getName(), signal);
  }

  @PostMapping("/{callId}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void leave(Authentication authentication, @PathVariable UUID callId,
             @Valid @RequestBody TerminalSignalRequest request) {
    var signal = objectMapper.createObjectNode();
    signal.put("type", "call.leave");
    signal.put("callId", callId.toString());
    signal.put("roomId", request.roomId());
    sockets.routeCallSignal(authentication.getName(), signal);
  }
}
