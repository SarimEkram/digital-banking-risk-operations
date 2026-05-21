package com.sarim.digitalbanking.admin.api;

import java.time.Instant;

public record RecentRiskItem(
        Long transferId,
        String status,
        String riskDecision,
        Integer riskScore,
        String riskReasons,
        long amountCents,
        String currency,
        Instant createdAt
) {}