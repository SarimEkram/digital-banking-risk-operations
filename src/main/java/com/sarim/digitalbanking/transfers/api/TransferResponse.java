package com.sarim.digitalbanking.transfers.api;

import java.time.Instant;

public record TransferResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        long amountCents,
        String currency,
        String status,
        Instant createdAt
) {}
