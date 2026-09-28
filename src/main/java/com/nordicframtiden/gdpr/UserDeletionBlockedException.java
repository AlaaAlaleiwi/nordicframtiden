package com.nordicframtiden.gdpr;

/**
 * Thrown when an admin tries to delete a user directly while the user has
 * shifts in the current month (they work now and get paid next month).
 * Mapped to HTTP 409 by ApiExceptionHandler; the message points the admin to
 * the deletion-requests flow, which schedules the deletion after the last
 * payroll month.
 */
public class UserDeletionBlockedException extends RuntimeException {
  public UserDeletionBlockedException(String message) {
    super(message);
  }
}
