package com.sarim.digitalbanking.transfers;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class TransferCursorCodec {

    public String encode(Instant createdAt, Long id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public long[] decode(String cursor) {
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );

            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("bad cursor");
            }

            long createdAtMillis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);

            return new long[]{createdAtMillis, id};
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
        }
    }
}