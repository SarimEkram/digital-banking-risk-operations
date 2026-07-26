package com.sarim.digitalbanking.admin.api;

public record AdminStatsResponse(
        long pendingReviewCount,
        long heldAmountCents,
        long transfersTodayCount,
        long transfersTodayVolumeCents,
        long completedCount,
        long rejectedCount,
        long blockedCount,
        long activeUserCount,
        long systemBalanceCents
) {}