package com.sarim.digitalbanking.idempotency;

import jakarta.servlet.http.HttpServletRequest;

public final class IdempotencyKeyUtil {
    private IdempotencyKeyUtil() {}

    public static final String HEADER = "Idempotency-Key";
    public static final String ATTR = "idempotencyKey";

    // keep it permissive: supports UUIDs and other client-generated keys
    // tighten later if you want.
    public static String requireAndStore(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        if (raw == null) throw new MissingIdempotencyKeyException();

        String key = raw.trim();
        if (key.isEmpty()) throw new MissingIdempotencyKeyException();

        if (key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be <= 128 characters");
        }

        // basic safe charset (UUIDs, tokens, etc.)
        if (!key.matches("^[A-Za-z0-9._:-]{1,128}$")) {
            throw new IllegalArgumentException("Idempotency-Key contains invalid characters");
        }

        request.setAttribute(ATTR, key);
        return key;
    }
}
