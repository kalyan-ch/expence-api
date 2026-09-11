package com.expence.expenceApi.exception;

/**
 * A request that conflicts with state already in the database.
 *
 * <p>Three uses: a unique-name clash (case-insensitive), a delete that is blocked because the
 * row is still referenced, and an edit that would change a transfer leg directly instead of
 * through the transfer endpoint.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
