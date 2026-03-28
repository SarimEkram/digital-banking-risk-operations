package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.admin.api.AdminHeldTransferResponse;
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import org.springframework.stereotype.Component;

@Component
public class TransferResponseMapper {

    public TransferResponse toUserResponse(TransferEntity t, Long actorUserId) {
        Long fromUid = t.getFromAccount().getUser().getId();
        Long toUid = t.getToAccount().getUser().getId();

        String fromEmail = t.getFromAccount().getUser().getEmail();
        String toEmail = t.getToAccount().getUser().getEmail();

        String direction = "UNKNOWN";
        String counterpartyEmail = null;

        if (actorUserId != null && actorUserId.equals(fromUid)) {
            direction = "SENT";
            counterpartyEmail = toEmail;
        } else if (actorUserId != null && actorUserId.equals(toUid)) {
            direction = "RECEIVED";
            counterpartyEmail = fromEmail;
        }

        return new TransferResponse(
                t.getId(),
                t.getFromAccount().getId(),
                t.getToAccount().getId(),
                t.getAmountCents(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getRiskDecision(),
                t.getRiskScore(),
                t.getRiskReasons(),
                t.getCreatedAt(),
                fromEmail,
                toEmail,
                direction,
                counterpartyEmail
        );
    }

    public AdminHeldTransferResponse toAdminHeldResponse(TransferEntity t) {
        String fromEmail = t.getFromAccount().getUser().getEmail();
        String toEmail = t.getToAccount().getUser().getEmail();

        return new AdminHeldTransferResponse(
                t.getId(),
                t.getFromAccount().getId(),
                t.getToAccount().getId(),
                t.getAmountCents(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getRiskDecision(),
                t.getRiskScore(),
                t.getRiskReasons(),
                t.getCreatedAt(),
                fromEmail,
                toEmail
        );
    }
}