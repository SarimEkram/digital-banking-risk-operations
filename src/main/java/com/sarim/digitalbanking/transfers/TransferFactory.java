package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import org.springframework.stereotype.Component;

@Component
public class TransferFactory {

    public TransferEntity newHeldTransfer(
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency,
            String idempotencyKey,
            TransferRiskDecisionService.RiskHoldDecision riskHoldDecision
    ) {
        TransferEntity t = new TransferEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmountCents(amountCents);
        t.setCurrency(currency);
        t.setStatus(TransferStatus.PENDING_REVIEW);
        t.setRiskDecision("HOLD");
        t.setRiskScore(riskHoldDecision.score());
        t.setRiskReasons(riskHoldDecision.reason());
        t.setIdempotencyKey(idempotencyKey);
        return t;
    }

    public TransferEntity newCompletedTransfer(
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency,
            String idempotencyKey
    ) {
        TransferEntity t = new TransferEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmountCents(amountCents);
        t.setCurrency(currency);
        t.setStatus(TransferStatus.COMPLETED);
        t.setIdempotencyKey(idempotencyKey);
        return t;
    }
}