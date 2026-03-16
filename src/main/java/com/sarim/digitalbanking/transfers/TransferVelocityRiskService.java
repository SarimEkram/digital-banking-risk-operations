package com.sarim.digitalbanking.transfers;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class TransferVelocityRiskService {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long CUMULATIVE_HOLD_THRESHOLD_CENTS = 500_000L; // $5000.00
    private static final Duration KEY_TTL = WINDOW.plusMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public TransferVelocityRiskService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public VelocitySnapshot getSnapshot(Long userId, long currentAmountCents, Instant now) {
        String key = key(userId);
        long cutoffMs = now.minus(WINDOW).toEpochMilli();

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoffMs - 1);

        Set<String> members = redisTemplate.opsForZSet().rangeByScore(key, cutoffMs, now.toEpochMilli());

        long priorWindowAmountCents = 0L;
        if (members != null) {
            for (String member : members) {
                priorWindowAmountCents += parseAmountCents(member);
            }
        }

        long projectedWindowAmountCents = priorWindowAmountCents + currentAmountCents;
        boolean hold = projectedWindowAmountCents > CUMULATIVE_HOLD_THRESHOLD_CENTS;

        return new VelocitySnapshot(
                hold,
                priorWindowAmountCents,
                projectedWindowAmountCents,
                hold ? "rolling 10-minute outgoing total exceeds $5,000" : null,
                hold ? 85 : null
        );
    }

    public void recordSuccessfulTransferAfterCommit(Long userId, Long transferId, long amountCents, Instant now) {
        Runnable action = () -> recordSuccessfulTransfer(userId, transferId, amountCents, now);

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

    private void recordSuccessfulTransfer(Long userId, Long transferId, long amountCents, Instant now) {
        String key = key(userId);
        long nowMs = now.toEpochMilli();
        long cutoffMs = now.minus(WINDOW).toEpochMilli();

        redisTemplate.opsForZSet().add(key, member(transferId, amountCents), nowMs);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoffMs - 1);
        redisTemplate.expire(key, KEY_TTL);
    }

    private String key(Long userId) {
        return "risk:velocity:user:" + userId;
    }

    private String member(Long transferId, long amountCents) {
        return transferId + ":" + amountCents;
    }

    private long parseAmountCents(String member) {
        int sep = member.indexOf(':');
        if (sep < 0 || sep == member.length() - 1) {
            return 0L;
        }

        try {
            return Long.parseLong(member.substring(sep + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public record VelocitySnapshot(
            boolean hold,
            long priorWindowAmountCents,
            long projectedWindowAmountCents,
            String reason,
            Integer score
    ) {}
}