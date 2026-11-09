package com.nebula.store.error;

/**
 * Thrown when a requested resource does not exist (or is no longer visible).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
