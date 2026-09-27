package com.nordicframtiden.pharmacy;

/** Thrown when a shift would give a user two shifts on the same day (HTTP 409). */
public class ShiftConflictException extends RuntimeException {
    public ShiftConflictException(String message) {
        super(message);
    }
}
