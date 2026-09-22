package com.nordicframtiden.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.nordicframtiden.chat.ChatAccessDeniedException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ChatAccessDeniedException.class)
  public ResponseEntity<?> handleChatAccessDenied(ChatAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArg(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
  }
}
