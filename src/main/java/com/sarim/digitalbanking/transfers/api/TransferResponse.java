package com.sarim.digitalbanking.transfers.api;

import java.time.Instant;

public record TransferResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        long amountCents,
        String currency,
        String status,

        String riskDecision,
        Integer riskScore,
        String riskReasons,

        Instant createdAt,

        String fromEmail,
        String toEmail,
        String direction,
        String counterpartyEmail
) {}