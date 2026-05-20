package com.sarim.digitalbanking.admin.api;

public record AccountDetailsResponse(
        Long accountId,
        String accountType,
        String currency,
        String status,
        long balanceCents
) {}