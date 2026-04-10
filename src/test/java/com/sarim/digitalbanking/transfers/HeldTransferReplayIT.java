package com.sarim.digitalbanking.transfers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarim.digitalbanking.IntegrationTestSupport;
import com.sarim.digitalbanking.idempotency.IdempotencyKeyRepository;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeldTransferReplayIT extends IntegrationTestSupport {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void sameHeldTransferRequestWithSameKeyReturnsStoredResponseAndDoesNotDoubleReserveFunds() throws Exception {
        RegisteredUser sender = registerUser(uniqueEmail("held-sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("held-recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), sender.accountId(), 700_000L);

        long payeeId = createPayee(senderBearer, recipient.email(), "held recipient");

        String holdKey = "held-transfer-" + UUID.randomUUID();

        Map<String, Object> transferRequest = Map.of(
                "fromAccountId", sender.accountId(),
                "payeeId", payeeId,
                "amountCents", 600_000L,
                "currency", "CAD"
        );

        JsonNode firstResponse = postJson(
                "/api/transfers",
                senderBearer,
                holdKey,
                transferRequest,
                status().isOk()
        );

        JsonNode secondResponse = postJson(
                "/api/transfers",
                senderBearer,
                holdKey,
                transferRequest,
                status().isOk()
        );

        assertThat(secondResponse).isEqualTo(firstResponse);

        assertThat(firstResponse.get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(firstResponse.get("riskDecision").asText()).isEqualTo("HOLD");
        assertThat(firstResponse.get("riskScore").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(firstResponse.get("riskReasons").asText()).contains("$5,000");

        TransferEntity storedTransfer = transferRepository.findByIdempotencyKey(holdKey).orElseThrow();

        assertThat(storedTransfer.getStatus()).isEqualTo(TransferStatus.PENDING_REVIEW);
        assertThat(storedTransfer.getRiskDecision()).isEqualTo("HOLD");

        long ledgerEntriesForThisTransfer = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> storedTransfer.getId().equals(entry.getTransfer().getId()))
                .count();

        assertThat(ledgerEntriesForThisTransfer).isEqualTo(1);

        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(100_000L);

        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(0L);

        assertThat(transferRepository.findAll().stream()
                .filter(t -> holdKey.equals(t.getIdempotencyKey()))
                .count()).isEqualTo(1);

        assertThat(idempotencyKeyRepository.findByKey(holdKey)).isPresent();
    }
}