package com.sarim.digitalbanking.idempotency;

import com.sarim.digitalbanking.admin.api.CreateAdminDepositRequest;
import com.sarim.digitalbanking.transfers.api.CreateTransferRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class IdempotencyRequestHasher {

    private static final String CAD = "CAD";

    public String hashUserTransfer(Long actorUserId, CreateTransferRequest req) {
        String currency = normalizeCurrency(req.currency());

        String raw = String.join("|",
                "user-transfer-v1",
                String.valueOf(actorUserId),
                String.valueOf(req.fromAccountId()),
                String.valueOf(req.payeeId()),
                String.valueOf(req.amountCents()),
                currency
        );

        return sha256Hex(raw);
    }

    public String hashAdminDeposit(CreateAdminDepositRequest req) {
        String raw = String.join("|",
                "admin-deposit-v1",
                String.valueOf(req.toAccountId()),
                String.valueOf(req.amountCents()),
                CAD
        );

        return sha256Hex(raw);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return CAD;
        }
        return currency.trim().toUpperCase();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}