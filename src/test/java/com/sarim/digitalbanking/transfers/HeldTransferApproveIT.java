package com.sarim.digitalbanking.transfers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarim.digitalbanking.IntegrationTestSupport;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeldTransferApproveIT extends IntegrationTestSupport {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void adminApprovingHeldTransferCreditsRecipientAndCompletesTransfer() throws Exception {
        // ----- arrange -----
        RegisteredUser sender = registerUser(uniqueEmail("approve-sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("approve-recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        // Fund the sender with $7,000 so the $6,000 transfer is affordable but trips the
        // amount-hold threshold ($5,000) and lands in PENDING_REVIEW.
        adminDeposit(admin.bearerToken(), sender.accountId(), 700_000L);

        long payeeId = createPayee(senderBearer, recipient.email(), "approve recipient");

        // ----- act 1: send the held transfer -----
        String idempotencyKey = "approve-held-" + UUID.randomUUID();
        JsonNode heldResponse = sendTransfer(senderBearer, sender.accountId(), payeeId, 600_000L, idempotencyKey);

        long transferId = heldResponse.get("id").asLong();

        // sanity: the transfer is held, not completed
        assertThat(heldResponse.get("status").asText()).isEqualTo("PENDING_REVIEW");

        // sender debited (reserve), recipient still untouched
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(100_000L);
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(0L);

        // exactly 1 ledger entry exists for this transfer (the debit reserve)
        long ledgerEntriesAfterHold = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> Long.valueOf(transferId).equals(entry.getTransfer().getId()))
                .count();
        assertThat(ledgerEntriesAfterHold).isEqualTo(1);

        // ----- act 2: admin approves -----
        // NOTE: admin.bearerToken() already includes the "Bearer " prefix (see login() in IntegrationTestSupport)
        mockMvc.perform(
                        post("/api/admin/transfers/{id}/approve", transferId)
                                .header("Authorization", admin.bearerToken())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.riskDecision").value("APPROVE"));

        // ----- assert post-approval state -----
        var transfer = transferRepository.findById(transferId).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getRiskDecision()).isEqualTo("APPROVE");

        // ledger now has both rows: original debit (from hold) + new credit (from approval)
        long ledgerEntriesAfterApprove = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> Long.valueOf(transferId).equals(entry.getTransfer().getId()))
                .count();
        assertThat(ledgerEntriesAfterApprove).isEqualTo(2);

        // sender stays at $1,000 (debit was already applied at hold time, not re-applied here)
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(100_000L);

        // recipient now has the $6,000 delivered
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(600_000L);
    }
}