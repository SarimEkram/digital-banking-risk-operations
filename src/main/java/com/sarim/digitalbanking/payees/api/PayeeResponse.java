package com.sarim.digitalbanking.payees.api;

import java.time.Instant;

public record PayeeResponse(
        Long id,
        String email,
        String label,
        String status,
        Instant createdAt
) {}
