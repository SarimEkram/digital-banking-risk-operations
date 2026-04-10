package com.sarim.digitalbanking.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.sarim.digitalbanking.IntegrationTestSupport;
import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.accounts.AccountType;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.idempotency.IdempotencyKeyRepository;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import com.sarim.digitalbanking.transfers.TransferEntity;
import com.sarim.digitalbanking.transfers.TransferRepository;
import com.sarim.digitalbanking.transfers.TransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDepositIdempotencyIT extends IntegrationTestSupport {

    private static final String SYSTEM_USER_EMAIL = "system@bank.local";

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void sameAdminDepositRequestWithSameKeyReturnsStoredResponseAndDoesNotDuplicateMoneyMovement() throws Exception {
        RegisteredUser recipient = registerUser(uniqueEmail("deposit-recipient"), "Password123!");
        AdminUser admin = createAdminAndLogin();

        Long systemUserId = userRepository.findByEmailIgnoreCase(SYSTEM_USER_EMAIL)
                .orElseThrow()
                .getId();

        AccountEntity treasuryAccount = accountRepository
                .findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
                        systemUserId,
                        AccountType.CHEQUING,
                        "CAD",
                        "ACTIVE"
                )
                .orElseThrow();

        long treasuryStartingBalance = treasuryAccount.getBalanceCents();
        long recipientStartingBalance = accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents();

        String depositKey = "admin-deposit-" + UUID.randomUUID();

        Map<String, Object> depositRequest = Map.of(
                "toAccountId", recipient.accountId(),
                "amountCents", 25_000L
        );

        JsonNode firstResponse = postJson(
                "/api/admin/deposit",
                admin.bearerToken(),
                depositKey,
                depositRequest,
                status().isOk()
        );

        JsonNode secondResponse = postJson(
                "/api/admin/deposit",
                admin.bearerToken(),
                depositKey,
                depositRequest,
                status().isOk()
        );

        assertThat(secondResponse).isEqualTo(firstResponse);

        assertThat(firstResponse.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(firstResponse.get("fromEmail").asText()).isEqualTo(SYSTEM_USER_EMAIL);
        assertThat(firstResponse.get("toEmail").asText()).isEqualTo(recipient.email());
        assertThat(firstResponse.get("amountCents").asLong()).isEqualTo(25_000L);
        assertThat(firstResponse.get("currency").asText()).isEqualTo("CAD");

        TransferEntity storedTransfer = transferRepository.findByIdempotencyKey(depositKey).orElseThrow();

        assertThat(storedTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        long ledgerEntriesForThisTransfer = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getTransfer() != null)
                .filter(entry -> storedTransfer.getId().equals(entry.getTransfer().getId()))
                .count();

        assertThat(ledgerEntriesForThisTransfer).isEqualTo(2);

        long treasuryEndingBalance = accountRepository.findById(treasuryAccount.getId()).orElseThrow().getBalanceCents();
        long recipientEndingBalance = accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents();

        assertThat(treasuryEndingBalance).isEqualTo(treasuryStartingBalance - 25_000L);
        assertThat(recipientEndingBalance).isEqualTo(recipientStartingBalance + 25_000L);

        assertThat(transferRepository.findAll().stream()
                .filter(t -> depositKey.equals(t.getIdempotencyKey()))
                .count()).isEqualTo(1);

        assertThat(idempotencyKeyRepository.findByKey(depositKey)).isPresent();
    }
}