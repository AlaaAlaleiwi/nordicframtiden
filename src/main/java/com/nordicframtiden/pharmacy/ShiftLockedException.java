package com.nordicframtiden.pharmacy;

/**
 * Thrown when a past shift would be changed/removed, or a new shift would be
 * created on a day that has already passed (ShiftLockPolicy). Mapped to
 * HTTP 409 by ApiExceptionHandler.
 */
public class ShiftLockedException extends RuntimeException {
    public ShiftLockedException(String message) {
        super(message);
    }
}
