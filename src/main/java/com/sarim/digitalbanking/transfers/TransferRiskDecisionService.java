package com.sarim.digitalbanking.transfers;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TransferRiskDecisionService {

    private static final long RISK_HOLD_THRESHOLD_CENTS = 500_000L; // $5000.00

    private final TransferVelocityRiskService transferVelocityRiskService;
    private final TransferRepository transferRepository;

    public TransferRiskDecisionService(
            TransferVelocityRiskService transferVelocityRiskService,
            TransferRepository transferRepository
    ) {
        this.transferVelocityRiskService = transferVelocityRiskService;
        this.transferRepository = transferRepository;
    }

    public RiskHoldDecision evaluateRiskHoldDecision(Long actorUserId, long amountCents, Instant now) {
        boolean amountHold = amountCents >= RISK_HOLD_THRESHOLD_CENTS;

        TransferVelocityRiskService.VelocitySnapshot velocitySnapshot =
                transferVelocityRiskService.getSnapshot(actorUserId, amountCents, now);

        boolean cumulativeHold = velocitySnapshot.hold();

        boolean existingPendingHold =
                transferRepository.existsByFromAccount_User_IdAndStatus(actorUserId, TransferStatus.PENDING_REVIEW);

        if (!amountHold && !cumulativeHold && !existingPendingHold) {
            return new RiskHoldDecision(false, null, null);
        }

        StringBuilder reason = new StringBuilder();

        if (amountHold) {
            reason.append("single transfer amount meets or exceeds $5,000");
        }

        if (cumulativeHold) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append(velocitySnapshot.reason());
        }

        if (existingPendingHold) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append("existing outgoing transfer is still pending review");
        }

        int score = 0;
        int triggerCount = 0;

        if (amountHold) {
            score = Math.max(score, 90);
            triggerCount++;
        }

        if (cumulativeHold) {
            score = Math.max(score, velocitySnapshot.score() == null ? 85 : velocitySnapshot.score());
            triggerCount++;
        }

        if (existingPendingHold) {
            score = Math.max(score, 95);
            triggerCount++;
        }

        if (triggerCount >= 2) {
            score = Math.min(99, score + 3);
        }

        // TODO: Future implementation:
        // add automated review / AI-assisted risk scoring so low-risk holds can be auto-cleared
        // within a short review window instead of always requiring manual admin action.

        return new RiskHoldDecision(true, reason.toString(), score);
    }

    public record RiskHoldDecision(boolean hold, String reason, Integer score) {}
}