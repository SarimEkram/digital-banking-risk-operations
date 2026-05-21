package com.sarim.digitalbanking.admin.api;

import java.time.Instant;

public record PayeeItemResponse(
        Long payeeId,
        Long payeeUserId,
        String payeeEmail,
        String label,
        String status,
        Instant createdAt
) {}