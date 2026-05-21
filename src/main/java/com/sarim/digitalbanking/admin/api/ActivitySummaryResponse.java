package com.sarim.digitalbanking.admin.api;

import java.time.Instant;

public record ActivitySummaryResponse(
        Long accountId,
        String accountType,
        String currency,
        long currentBalanceCents,
        Instant accountCreatedAt,
        Long totalTransfersSent,
        long totalAmountSentCents,
        Long totalTransfersReceived,
        long totalAmountReceivedCents,
        Instant lastActivityAt
) {}