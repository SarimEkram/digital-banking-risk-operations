package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrencyIT extends IntegrationTestSupport {

    private static final int THREADS = 100;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void oppositeDirectionTransfersFromManyThreadsDoNotDeadlockAndConserveMoney() throws Exception {
        RegisteredUser a = registerUser(uniqueEmail("a"), "Password123!");
        RegisteredUser b = registerUser(uniqueEmail("b"), "Password123!");
        String aBearer = login(a.email(), a.password());
        String bBearer = login(b.email(), b.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), a.accountId(), 100_000L);
        adminDeposit(admin.bearerToken(), b.accountId(), 100_000L);

        long payeeB = createPayee(aBearer, b.email(), "b");
        long payeeA = createPayee(bBearer, a.email(), "a");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            boolean aToB = i % 2 == 0;
            String bearer = aToB ? aBearer : bBearer;
            long from = aToB ? a.accountId() : b.accountId();
            long payee = aToB ? payeeB : payeeA;
            tasks.add(() -> {
                sendTransfer(bearer, from, payee, 100L, "concurrent-" + UUID.randomUUID());
                return null;
            });
        }

        runConcurrently(tasks);

        // 50 transfers each way of equal amount: net zero, total conserved.
        long balanceA = accountRepository.findById(a.accountId()).orElseThrow().getBalanceCents();
        long balanceB = accountRepository.findById(b.accountId()).orElseThrow().getBalanceCents();
        assertThat(balanceA + balanceB).isEqualTo(200_000L);
        assertThat(balanceA).isEqualTo(100_000L);
        assertThat(balanceB).isEqualTo(100_000L);
    }

    @Test
    void sameIdempotencyKeyFromManyThreadsMovesMoneyExactlyOnce() throws Exception {
        RegisteredUser sender = registerUser(uniqueEmail("sender"), "Password123!");
        RegisteredUser recipient = registerUser(uniqueEmail("recipient"), "Password123!");
        String bearer = login(sender.email(), sender.password());
        AdminUser admin = createAdminAndLogin();

        adminDeposit(admin.bearerToken(), sender.accountId(), 100_000L);
        long payeeId = createPayee(bearer, recipient.email(), "recipient");

        String key = "same-key-" + UUID.randomUUID();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                // Losers may legitimately see 409 while the winner is in flight; only 5xx/exceptions fail.
                var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/transfers")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fromAccountId", sender.accountId(),
                                "payeeId", payeeId,
                                "amountCents", 1_000L,
                                "currency", "CAD")));
                int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
                assertThat(status).isLessThan(500);
                return null;
            });
        }

        runConcurrently(tasks);

        assertThat(transferRepository.findAll().stream()
                .filter(t -> key.equals(t.getIdempotencyKey())).count()).isEqualTo(1);
        assertThat(accountRepository.findById(sender.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(99_000L);
        assertThat(accountRepository.findById(recipient.accountId()).orElseThrow().getBalanceCents())
                .isEqualTo(1_000L);
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Void> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
