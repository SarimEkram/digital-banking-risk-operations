package com.sarim.digitalbanking.admin.api;

public record AdminAccountLookupResponse(
        Long userId,
        String email,
        Long accountId,
        String currency,
        String status,
        long balanceCents
) {}
