package com.sarim.digitalbanking.transfers.api;

import java.time.Instant;

public record TransferResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        long amountCents,
        String currency,
        String status,
        Instant createdAt,

        // NEW (for Activity UI)
        String fromEmail,
        String toEmail,
        String direction,          // "SENT" | "RECEIVED" | "UNKNOWN"
        String counterpartyEmail   // who you sent to / received from
) {}
