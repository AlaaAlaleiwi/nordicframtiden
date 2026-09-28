package com.nordicframtiden.gdpr;

/**
 * Thrown when a shift would be created/moved for a user whose deletion
 * request is open, and the shift starts after the block window (current +
 * next month, kept for payroll). Mapped to HTTP 409 by ApiExceptionHandler.
 */
public class DeletionShiftBlockException extends RuntimeException {
  public DeletionShiftBlockException(String message) {
    super(message);
  }
}
