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

  // Return a proper 500 (with a JSON body) for database-level failures so
  // clients never see them disguised as other status codes.
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Database constraint violation"));
  }
}
