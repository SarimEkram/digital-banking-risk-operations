package com.sarim.digitalbanking.audit.api;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorEmail,
        Long affectedUserId,
        String affectedEmail,
        String action,
        String entityType,
        String entityId,
        String details,
        String correlationId,
        Instant createdAt
) {}