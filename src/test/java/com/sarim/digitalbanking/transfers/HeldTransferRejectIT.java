package com.sarim.digitalbanking.transfers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarim.digitalbanking.IntegrationTestSupport;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeldTransferRejectIT extends IntegrationTestSupport {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void adminRejectingHeldTransferRefundsSenderAndStoresRejectionReason() throws Exception {
        // ----- arrange -----
        RegisteredUser sender = registerUser(uniqueEmail("reject-sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("reject-recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        // Fund sender with $7,000; $6,000 transfer trips the $5,000 amount-hold threshold.
        adminDeposit(admin.bearerToken(), sender.accountId(), 700_000L);

        long payeeId = createPayee(senderBearer, recipient.email(), "reject recipient");

        // ----- act 1: send the held transfer -----
        String idempotencyKey = "reject-held-" + UUID.randomUUID();
        JsonNode heldResponse = sendTransfer(senderBearer, sender.accountId(), payeeId, 600_000L, idempotencyKey);

        long transferId = heldResponse.get("id").asLong();
        assertThat(heldResponse.get("status").asText()).isEqualTo("PENDING_REVIEW");

        // sender debited (reserve), recipient untouched, 1 ledger entry
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(100_000L);
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(0L);

        long ledgerEntriesAfterHold = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> Long.valueOf(transferId).equals(entry.getTransfer().getId()))
                .count();
        assertThat(ledgerEntriesAfterHold).isEqualTo(1);

        // ----- act 2: admin rejects with a reason -----
        // NOTE: admin.bearerToken() already includes "Bearer " (see login() in IntegrationTestSupport)
        String rejectionReason = "flagged as suspicious by ops review";
        String rejectBody = objectMapper.writeValueAsString(Map.of("reason", rejectionReason));

        mockMvc.perform(
                        post("/api/admin/transfers/{id}/reject", transferId)
                                .header("Authorization", admin.bearerToken())
                                .contentType(APPLICATION_JSON)
                                .content(rejectBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.riskDecision").value("BLOCK"));

        // ----- assert post-rejection state -----
        var transfer = transferRepository.findById(transferId).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(transfer.getRiskDecision()).isEqualTo("BLOCK");

        // riskReasons is REPLACED entirely with the (trimmed) supplied reason — not appended.
        assertThat(transfer.getRiskReasons()).isEqualTo(rejectionReason);

        // ledger now has both rows: original debit (from hold) + new credit (refund)
        long ledgerEntriesAfterReject = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> Long.valueOf(transferId).equals(entry.getTransfer().getId()))
                .count();
        assertThat(ledgerEntriesAfterReject).isEqualTo(2);

        // sender refunded back to the original $7,000
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(700_000L);

        // recipient never received anything — still at $0
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(0L);
    }

    @Test
    void adminRejectingHeldTransferWithoutReasonStoresFallbackReason() throws Exception {
        // ----- arrange (same setup as the test above) -----
        RegisteredUser sender = registerUser(uniqueEmail("reject-noreason-sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("reject-noreason-recipient"), "Password123!");

        String senderBearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), sender.accountId(), 700_000L);
        long payeeId = createPayee(senderBearer, recipient.email(), "reject recipient");

        String idempotencyKey = "reject-held-noreason-" + UUID.randomUUID();
        JsonNode heldResponse = sendTransfer(senderBearer, sender.accountId(), payeeId, 600_000L, idempotencyKey);
        long transferId = heldResponse.get("id").asLong();
        assertThat(heldResponse.get("status").asText()).isEqualTo("PENDING_REVIEW");

        // ----- act: admin rejects with NO body (controller's @RequestBody is required = false) -----
        mockMvc.perform(
                        post("/api/admin/transfers/{id}/reject", transferId)
                                .header("Authorization", admin.bearerToken())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.riskDecision").value("BLOCK"));

        // ----- assert: riskReasons is the fallback string -----
        var transfer = transferRepository.findById(transferId).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(transfer.getRiskDecision()).isEqualTo("BLOCK");
        assertThat(transfer.getRiskReasons()).isEqualTo("rejected during manual review");

        // refund still happened: sender back to $7,000, recipient at $0
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(700_000L);
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(0L);
    }
}