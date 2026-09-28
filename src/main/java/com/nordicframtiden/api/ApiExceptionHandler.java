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

  /** 409 for rule violations the client should show (e.g. one shift per user per day). */
  @ExceptionHandler(com.nordicframtiden.pharmacy.ShiftConflictException.class)
  public ResponseEntity<?> handleShiftConflict(com.nordicframtiden.pharmacy.ShiftConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  /** 409: new shifts beyond the payroll window while a deletion request is open. */
  @ExceptionHandler(com.nordicframtiden.gdpr.DeletionShiftBlockException.class)
  public ResponseEntity<?> handleDeletionShiftBlock(com.nordicframtiden.gdpr.DeletionShiftBlockException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  /** 409: past shifts are locked (cannot be changed or removed). */
  @ExceptionHandler(com.nordicframtiden.pharmacy.ShiftLockedException.class)
  public ResponseEntity<?> handleShiftLocked(com.nordicframtiden.pharmacy.ShiftLockedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  /** 409: direct user deletion blocked by the payroll deletion policy. */
  @ExceptionHandler(com.nordicframtiden.gdpr.UserDeletionBlockedException.class)
  public ResponseEntity<?> handleUserDeletionBlocked(com.nordicframtiden.gdpr.UserDeletionBlockedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  // Return a proper 500 (with a JSON body) for database-level failures so
  // clients never see them disguised as other status codes.
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Database constraint violation"));
  }
}
