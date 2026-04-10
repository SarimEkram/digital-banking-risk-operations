package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.IntegrationTestSupport;
import com.sarim.digitalbanking.idempotency.IdempotencyKeyRepository;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferIdempotencyConflictIT extends IntegrationTestSupport {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void sameKeyWithDifferentTransferRequestReturnsConflictAndDoesNotApplySecondTransfer() throws Exception {
        RegisteredUser sender = registerUser(uniqueEmail("conflict-sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("conflict-recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), sender.accountId(), 50_000L);

        long payeeId = createPayee(senderBearer, recipient.email(), "recipient");

        String transferKey = "transfer-conflict-" + UUID.randomUUID();

        Map<String, Object> firstRequest = Map.of(
                "fromAccountId", sender.accountId(),
                "payeeId", payeeId,
                "amountCents", 1_200L,
                "currency", "CAD"
        );

        Map<String, Object> secondRequest = Map.of(
                "fromAccountId", sender.accountId(),
                "payeeId", payeeId,
                "amountCents", 1_500L,
                "currency", "CAD"
        );

        postJson(
                "/api/transfers",
                senderBearer,
                transferKey,
                firstRequest,
                status().isOk()
        );

        postJson(
                "/api/transfers",
                senderBearer,
                transferKey,
                secondRequest,
                status().isConflict()
        );

        TransferEntity storedTransfer = transferRepository.findByIdempotencyKey(transferKey).orElseThrow();

        assertThat(storedTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(storedTransfer.getAmountCents()).isEqualTo(1_200L);

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