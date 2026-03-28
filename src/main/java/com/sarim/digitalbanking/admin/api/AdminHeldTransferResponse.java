package com.sarim.digitalbanking.admin.api;

import java.time.Instant;

public record AdminHeldTransferResponse(
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
        String toEmail
) {}