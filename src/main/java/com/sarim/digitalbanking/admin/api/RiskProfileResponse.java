package com.sarim.digitalbanking.admin.api;

import java.util.List;

public record RiskProfileResponse(
        Long totalHeldTransfers,
        Long totalBlockedTransfers,
        Long totalRejectedTransfers,
        Double averageRiskScore,
        List<RecentRiskItem> recentRiskReasons
) {}