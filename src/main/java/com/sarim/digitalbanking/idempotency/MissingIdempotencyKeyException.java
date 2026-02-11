package com.sarim.digitalbanking.idempotency;

public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException() {
        super("Idempotency-Key header is required");
    }
}
