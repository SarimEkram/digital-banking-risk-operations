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

class TransferIdempotencyIT extends IntegrationTestSupport {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void sameTransferRequestWithSameKeyReturnsStoredResponseAndDoesNotDuplicateMoneyMovement() throws Exception {
        RegisteredUser sender = registerUser(uniqueEmail("sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), sender.accountId(), 50_000L);

        long payeeId = createPayee(senderBearer, recipient.email(), "recipient");

        String transferKey = "transfer-" + UUID.randomUUID();

        Map<String, Object> transferRequest = Map.of(
                "fromAccountId", sender.accountId(),
                "payeeId", payeeId,
                "amountCents", 1_200L,
                "currency", "CAD"
        );

        JsonNode firstResponse = postJson(
                "/api/transfers",
                senderBearer,
                transferKey,
                transferRequest,
                status().isOk()
        );

        JsonNode secondResponse = postJson(
                "/api/transfers",
                senderBearer,
                transferKey,
                transferRequest,
                status().isOk()
        );

        assertThat(secondResponse).isEqualTo(firstResponse);

        TransferEntity storedTransfer = transferRepository.findByIdempotencyKey(transferKey).orElseThrow();

        assertThat(storedTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        long ledgerEntriesForThisTransfer = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> storedTransfer.getId().equals(entry.getTransfer().getId()))
                .count();

        assertThat(ledgerEntriesForThisTransfer).isEqualTo(2);

        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(48_800L);

        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(1_200L);

        assertThat(transferRepository.findAll().stream()
                .filter(t -> transferKey.equals(t.getIdempotencyKey()))
                .count()).isEqualTo(1);

        assertThat(idempotencyKeyRepository.findByKey(transferKey)).isPresent();
    }
}