package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.transfers.TransferStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private static final String SYSTEM_USER_EMAIL = "system@bank.local";

    private final EntityManager entityManager;

    public AdminStatsController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping
    public AdminStatsResponse getStats(HttpServletRequest request) {
        requireUid(request);

        Object[] pendingRow = (Object[]) entityManager.createQuery(
                        "SELECT COUNT(t), COALESCE(SUM(t.amountCents), 0) " +
                                "FROM TransferEntity t WHERE t.status = :status"
                )
                .setParameter("status", TransferStatus.PENDING_REVIEW)
                .getSingleResult();
        long pendingReviewCount = ((Number) pendingRow[0]).longValue();
        long heldAmountCents = ((Number) pendingRow[1]).longValue();

        Instant startOfTodayUtc = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Object[] todayRow = (Object[]) entityManager.createQuery(
                        "SELECT COUNT(t), COALESCE(SUM(t.amountCents), 0) " +
                                "FROM TransferEntity t " +
                                "WHERE t.status = :status AND t.createdAt >= :start"
                )
                .setParameter("status", TransferStatus.COMPLETED)
                .setParameter("start", startOfTodayUtc)
                .getSingleResult();
        long transfersTodayCount = ((Number) todayRow[0]).longValue();
        long transfersTodayVolumeCents = ((Number) todayRow[1]).longValue();

        long completedCount = countByStatus(TransferStatus.COMPLETED);
        long rejectedCount = countByStatus(TransferStatus.REJECTED);
        long blockedCount = countByStatus(TransferStatus.BLOCKED);

        Query userQuery = entityManager.createQuery(
                "SELECT COUNT(u) FROM UserEntity u WHERE LOWER(u.email) <> :systemEmail"
        );
        userQuery.setParameter("systemEmail", SYSTEM_USER_EMAIL.toLowerCase());
        long activeUserCount = ((Number) userQuery.getSingleResult()).longValue();

        Query balanceQuery = entityManager.createQuery(
                "SELECT COALESCE(SUM(a.balanceCents), 0) FROM AccountEntity a " +
                        "WHERE LOWER(a.user.email) <> :systemEmail"
        );
        balanceQuery.setParameter("systemEmail", SYSTEM_USER_EMAIL.toLowerCase());
        long systemBalanceCents = ((Number) balanceQuery.getSingleResult()).longValue();

        return new AdminStatsResponse(
                pendingReviewCount,
                heldAmountCents,
                transfersTodayCount,
                transfersTodayVolumeCents,
                completedCount,
                rejectedCount,
                blockedCount,
                activeUserCount,
                systemBalanceCents
        );
    }

    private long countByStatus(TransferStatus status) {
        Query q = entityManager.createQuery(
                "SELECT COUNT(t) FROM TransferEntity t WHERE t.status = :status"
        );
        q.setParameter("status", status);
        return ((Number) q.getSingleResult()).longValue();
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}