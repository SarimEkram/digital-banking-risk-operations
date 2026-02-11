package com.sarim.digitalbanking.accounts.api;

public record AccountResponse(
        Long id,
        String currency,
        long balanceCents,
        String status
) {}
