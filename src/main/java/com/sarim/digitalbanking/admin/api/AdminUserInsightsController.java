package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.payees.PayeeEntity;
import com.sarim.digitalbanking.transfers.TransferEntity;
import com.sarim.digitalbanking.transfers.TransferStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users/{userId}")
public class AdminUserInsightsController {

    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    public AdminUserInsightsController(
            AccountRepository accountRepository,
            EntityManager entityManager
    ) {
        this.accountRepository = accountRepository;
        this.entityManager = entityManager;
    }

    @GetMapping("/activity-summary")
    public List<ActivitySummaryResponse> getActivitySummary(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        requireUid(request);

        List<AccountEntity> accounts = accountRepository.findByUserIdOrderByIdAsc(userId);

        List<ActivitySummaryResponse> summaries = new ArrayList<>();

        for (AccountEntity account : accounts) {
            // Count and sum transfers sent
            Query sentQuery = entityManager.createQuery(
                    "SELECT COUNT(t), COALESCE(SUM(t.amountCents), 0) " +
                            "FROM TransferEntity t " +
                            "WHERE t.fromAccount.id = :accountId AND t.status = :status"
            );
            sentQuery.setParameter("accountId", account.getId());
            sentQuery.setParameter("status", TransferStatus.COMPLETED);
            Object[] sentResult = (Object[]) sentQuery.getSingleResult();
            Long sentCount = (Long) sentResult[0];
            Long sentTotal = ((Number) sentResult[1]).longValue();

            // Count and sum transfers received
            Query receivedQuery = entityManager.createQuery(
                    "SELECT COUNT(t), COALESCE(SUM(t.amountCents), 0) " +
                            "FROM TransferEntity t " +
                            "WHERE t.toAccount.id = :accountId AND t.status = :status"
            );
            receivedQuery.setParameter("accountId", account.getId());
            receivedQuery.setParameter("status", TransferStatus.COMPLETED);
            Object[] receivedResult = (Object[]) receivedQuery.getSingleResult();
            Long receivedCount = (Long) receivedResult[0];
            Long receivedTotal = ((Number) receivedResult[1]).longValue();

            // Get last activity timestamp
            Query lastActivityQuery = entityManager.createQuery(
                    "SELECT MAX(t.createdAt) FROM TransferEntity t " +
                            "WHERE t.fromAccount.id = :accountId OR t.toAccount.id = :accountId"
            );
            lastActivityQuery.setParameter("accountId", account.getId());
            Instant lastActivity = (Instant) lastActivityQuery.getSingleResult();

            summaries.add(new ActivitySummaryResponse(
                    account.getId(),
                    account.getAccountType().name(),
                    account.getCurrency(),
                    account.getBalanceCents(),
                    account.getCreatedAt(),
                    sentCount,
                    sentTotal,
                    receivedCount,
                    receivedTotal,
                    lastActivity
            ));
        }

        return summaries;
    }

    @GetMapping("/risk-profile")
    public RiskProfileResponse getRiskProfile(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "5") int limit,
            HttpServletRequest request
    ) {
        requireUid(request);

        // Validate limit
        if (limit < 1 || limit > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 20");
        }

        List<AccountEntity> accounts = accountRepository.findByUserIdOrderByIdAsc(userId);
        List<Long> accountIds = accounts.stream().map(AccountEntity::getId).collect(Collectors.toList());

        if (accountIds.isEmpty()) {
            return new RiskProfileResponse(0L, 0L, 0L, null, List.of());
        }

        // Count held transfers
        Query heldQuery = entityManager.createQuery(
                "SELECT COUNT(t) FROM TransferEntity t " +
                        "WHERE t.fromAccount.id IN :accountIds AND t.status = :status"
        );
        heldQuery.setParameter("accountIds", accountIds);
        heldQuery.setParameter("status", TransferStatus.PENDING_REVIEW);
        Long heldCount = (Long) heldQuery.getSingleResult();

        // Count blocked transfers
        Query blockedQuery = entityManager.createQuery(
                "SELECT COUNT(t) FROM TransferEntity t " +
                        "WHERE t.fromAccount.id IN :accountIds AND t.status = :status"
        );
        blockedQuery.setParameter("accountIds", accountIds);
        blockedQuery.setParameter("status", TransferStatus.BLOCKED);
        Long blockedCount = (Long) blockedQuery.getSingleResult();

        // Count rejected transfers
        Query rejectedQuery = entityManager.createQuery(
                "SELECT COUNT(t) FROM TransferEntity t " +
                        "WHERE t.fromAccount.id IN :accountIds AND t.status = :status"
        );
        rejectedQuery.setParameter("accountIds", accountIds);
        rejectedQuery.setParameter("status", TransferStatus.REJECTED);
        Long rejectedCount = (Long) rejectedQuery.getSingleResult();

        // Get recent transfers with risk flags (for both display AND average calculation)
        Query recentRiskQuery = entityManager.createQuery(
                "SELECT t FROM TransferEntity t " +
                        "WHERE t.fromAccount.id IN :accountIds " +
                        "AND (t.riskReasons IS NOT NULL OR t.status IN (:pendingReview, :blocked, :rejected)) " +
                        "ORDER BY t.createdAt DESC",
                TransferEntity.class
        );
        recentRiskQuery.setParameter("accountIds", accountIds);
        recentRiskQuery.setParameter("pendingReview", TransferStatus.PENDING_REVIEW);
        recentRiskQuery.setParameter("blocked", TransferStatus.BLOCKED);
        recentRiskQuery.setParameter("rejected", TransferStatus.REJECTED);
        recentRiskQuery.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<TransferEntity> recentRiskTransfers = recentRiskQuery.getResultList();

        // Calculate average risk score ONLY from the last N transfers (matching limit)
        Double avgRiskScore = null;
        if (!recentRiskTransfers.isEmpty()) {
            List<Integer> riskScores = recentRiskTransfers.stream()
                    .map(TransferEntity::getRiskScore)
                    .filter(score -> score != null)
                    .collect(Collectors.toList());

            if (!riskScores.isEmpty()) {
                avgRiskScore = riskScores.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0);
            }
        }

        List<RecentRiskItem> recentRiskItems = recentRiskTransfers.stream()
                .map(t -> new RecentRiskItem(
                        t.getId(),
                        t.getStatus().name(),
                        t.getRiskDecision(),
                        t.getRiskScore(),
                        t.getRiskReasons(),
                        t.getAmountCents(),
                        t.getCurrency(),
                        t.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new RiskProfileResponse(
                heldCount,
                blockedCount,
                rejectedCount,
                avgRiskScore,
                recentRiskItems
        );
    }

    @GetMapping("/payees")
    public List<PayeeItemResponse> getPayees(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        requireUid(request);

        // Get ALL payees (ACTIVE and DISABLED)
        Query payeesQuery = entityManager.createQuery(
                "SELECT p FROM PayeeEntity p " +
                        "WHERE p.ownerUser.id = :userId " +
                        "ORDER BY p.createdAt DESC",
                PayeeEntity.class
        );
        payeesQuery.setParameter("userId", userId);

        @SuppressWarnings("unchecked")
        List<PayeeEntity> payees = payeesQuery.getResultList();

        return payees.stream()
                .map(p -> new PayeeItemResponse(
                        p.getId(),
                        p.getPayeeUser().getId(),
                        p.getPayeeEmail(),
                        p.getLabel(),
                        p.getStatus(),
                        p.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}