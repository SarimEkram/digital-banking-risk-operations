package com.sarim.digitalbanking.transfers;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;

@Service
public class TransferVelocityRiskService {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long MAX_ALLOWED_SUCCESSFUL_TRANSFERS_IN_WINDOW = 3L;
    private static final Duration KEY_TTL = WINDOW.plusMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public TransferVelocityRiskService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public VelocitySnapshot getSnapshot(Long userId, Instant now) {
        String key = key(userId);
        long cutoffMs = now.minus(WINDOW).toEpochMilli();

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoffMs - 1);

        Long count = redisTemplate.opsForZSet().zCard(key);
        long priorSuccessfulTransfers = count == null ? 0L : count;

        boolean hold = priorSuccessfulTransfers >= MAX_ALLOWED_SUCCESSFUL_TRANSFERS_IN_WINDOW;

        return new VelocitySnapshot(
                hold,
                priorSuccessfulTransfers,
                hold ? "transfer velocity exceeded: 4th transfer within 10 minutes" : null,
                hold ? 80 : null
        );
    }

    public void recordSuccessfulTransferAfterCommit(Long userId, Long transferId, Instant now) {
        Runnable action = () -> recordSuccessfulTransfer(userId, transferId, now);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void recordSuccessfulTransfer(Long userId, Long transferId, Instant now) {
        String key = key(userId);
        long nowMs = now.toEpochMilli();
        long cutoffMs = now.minus(WINDOW).toEpochMilli();

        redisTemplate.opsForZSet().add(key, String.valueOf(transferId), nowMs);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoffMs - 1);
        redisTemplate.expire(key, KEY_TTL);
    }

    private String key(Long userId) {
        return "risk:velocity:user:" + userId;
    }

    public record VelocitySnapshot(
            boolean hold,
            long priorSuccessfulTransfers,
            String reason,
            Integer score
    ) {}
}