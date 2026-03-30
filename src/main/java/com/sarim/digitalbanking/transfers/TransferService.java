package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.accounts.AccountType;
import com.sarim.digitalbanking.admin.api.AdminHeldTransferResponse;
import com.sarim.digitalbanking.admin.api.CreateAdminDepositRequest;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.payees.PayeeEntity;
import com.sarim.digitalbanking.payees.PayeeRepository;
import com.sarim.digitalbanking.transfers.api.CreateTransferRequest;
import com.sarim.digitalbanking.transfers.api.TransferPageResponse;
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class TransferService {

    private static final String SYSTEM_USER_EMAIL = "system@bank.local";
    private static final String CAD = "CAD";
    private static final long RISK_HOLD_THRESHOLD_CENTS = 500_000; // $5000.00

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final PayeeRepository payeeRepository;
    private final EntityManager entityManager;
    private final TransferVelocityRiskService transferVelocityRiskService;
    private final TransferResponseMapper transferResponseMapper;
    private final TransferSettlementService transferSettlementService;
    private final TransferAuditService transferAuditService;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            UserRepository userRepository,
            PayeeRepository payeeRepository,
            EntityManager entityManager,
            TransferVelocityRiskService transferVelocityRiskService,
            TransferResponseMapper transferResponseMapper,
            TransferSettlementService transferSettlementService,
            TransferAuditService transferAuditService
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.payeeRepository = payeeRepository;
        this.entityManager = entityManager;
        this.transferVelocityRiskService = transferVelocityRiskService;
        this.transferResponseMapper = transferResponseMapper;
        this.transferSettlementService = transferSettlementService;
        this.transferAuditService = transferAuditService;
    }

    @Transactional
    public TransferResponse createTransfer(Long actorUserId, String idempotencyKey, CreateTransferRequest req) {
        String currency = (req.currency() == null || req.currency().isBlank())
                ? CAD
                : req.currency().trim().toUpperCase();

        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }

        long amount = req.amountCents();

        PayeeEntity payee = payeeRepository.findByIdAndOwnerUser_Id(req.payeeId(), actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("payee not found"));

        if (!"ACTIVE".equalsIgnoreCase(payee.getStatus())) {
            throw new IllegalArgumentException("payee is disabled");
        }

        Long payeeUserId = payee.getPayeeUser().getId();

        Long toAccountId = accountRepository
                .findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
                        payeeUserId,
                        AccountType.CHEQUING,
                        currency,
                        "ACTIVE"
                )
                .map(AccountEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("payee account not found"));

        if (req.fromAccountId().equals(toAccountId)) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must be different");
        }

        var existing = transferRepository.findByIdempotencyKeyAndFromAccount_User_Id(idempotencyKey, actorUserId);
        if (existing.isPresent()) {
            TransferEntity t = existing.get();

            boolean same = sameTransferRequest(t, req.fromAccountId(), toAccountId, amount, currency);

            if (!same) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            return transferResponseMapper.toUserResponse(t, actorUserId);
        }

        if (transferRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used"
            );
        }

        List<Long> ids = List.of(req.fromAccountId(), toAccountId).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(req.fromAccountId()) ? locked.get(0) : locked.get(1);
        AccountEntity to   = locked.get(0).getId().equals(toAccountId)          ? locked.get(0) : locked.get(1);

        if (!from.getUser().getId().equals(actorUserId)) {
            throw new IllegalArgumentException("Account not found");
        }
        if (!to.getUser().getId().equals(payeeUserId)) {
            throw new IllegalArgumentException("Account not found");
        }

        if (!"ACTIVE".equalsIgnoreCase(from.getStatus())) {
            throw new IllegalArgumentException("From account is not active");
        }
        if (!"ACTIVE".equalsIgnoreCase(to.getStatus())) {
            throw new IllegalArgumentException("To account is not active");
        }

        if (!currency.equalsIgnoreCase(from.getCurrency()) || !currency.equalsIgnoreCase(to.getCurrency())) {
            throw new IllegalArgumentException("currency must match both accounts");
        }

        if (from.getBalanceCents() < amount) {
            throw new IllegalArgumentException("insufficient funds");
        }

        Instant riskEvaluatedAt = Instant.now();
        RiskHoldDecision riskHoldDecision = evaluateRiskHoldDecision(actorUserId, amount, riskEvaluatedAt);

        boolean holdForReview = riskHoldDecision.hold();

        TransferEntity t = holdForReview
                ? newHeldTransfer(from, to, amount, currency, idempotencyKey, riskHoldDecision)
                : newCompletedTransfer(from, to, amount, currency, idempotencyKey);

        SaveTransferOutcome saveOutcome = saveTransferWithIdempotentReplay(
                t,
                idempotencyKey,
                () -> transferRepository.findByIdempotencyKeyAndFromAccount_User_Id(idempotencyKey, actorUserId),
                winner -> sameTransferRequest(winner, req.fromAccountId(), toAccountId, amount, currency),
                true
        );

        t = saveOutcome.transfer();
        if (saveOutcome.replayed()) {
            return transferResponseMapper.toUserResponse(t, actorUserId);
        }

        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (holdForReview) {
            transferSettlementService.applyHeldTransferReserve(t, from, amount, currency);

            transferAuditService.logTransferHeld(
                    actor,
                    t.getId(),
                    from.getId(),
                    payee.getId(),
                    to.getId(),
                    amount,
                    currency,
                    riskHoldDecision.reason()
            );

            transferVelocityRiskService.recordSuccessfulTransferAfterCommit(actorUserId, t.getId(), amount, riskEvaluatedAt);

            return transferResponseMapper.toUserResponse(t, actorUserId);
        }

        transferSettlementService.applyLedgerAndBalances(t, from, to, amount, currency);

        transferAuditService.logTransferCreate(
                actor,
                t.getId(),
                from.getId(),
                payee.getId(),
                to.getId(),
                amount,
                currency
        );

        transferVelocityRiskService.recordSuccessfulTransferAfterCommit(actorUserId, t.getId(), amount, riskEvaluatedAt);

        return transferResponseMapper.toUserResponse(t, actorUserId);
    }

    @Transactional
    public TransferResponse createAdminDeposit(Long adminUserId, String idempotencyKey, CreateAdminDepositRequest req) {
        long amount = req.amountCents();
        if (amount <= 0) {
            throw new IllegalArgumentException("amountCents must be > 0");
        }

        UserEntity adminActor = requireAdminActor(adminUserId);

        UserEntity systemUser = userRepository.findByEmailIgnoreCase(SYSTEM_USER_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("system user not found"));

        Long treasuryAccountId = accountRepository
                .findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
                        systemUser.getId(),
                        AccountType.CHEQUING,
                        CAD,
                        "ACTIVE"
                )
                .map(AccountEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("system treasury account not found"));

        if (req.toAccountId().equals(treasuryAccountId)) {
            throw new IllegalArgumentException("toAccountId must be different from treasury account");
        }

        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            TransferEntity t = existing.get();

            boolean same = sameTransferRequest(t, treasuryAccountId, req.toAccountId(), amount, CAD);

            if (!same) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            return transferResponseMapper.toUserResponse(t, systemUser.getId());
        }

        List<Long> ids = List.of(treasuryAccountId, req.toAccountId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(treasuryAccountId) ? locked.get(0) : locked.get(1);
        AccountEntity to   = locked.get(0).getId().equals(req.toAccountId())  ? locked.get(0) : locked.get(1);

        if (!from.getUser().getId().equals(systemUser.getId())) {
            throw new IllegalArgumentException("system treasury account not found");
        }

        if (!"ACTIVE".equalsIgnoreCase(from.getStatus())) {
            throw new IllegalArgumentException("Treasury account is not active");
        }
        if (!"ACTIVE".equalsIgnoreCase(to.getStatus())) {
            throw new IllegalArgumentException("To account is not active");
        }

        if (from.getAccountType() != AccountType.CHEQUING) {
            throw new IllegalArgumentException("Treasury account must be CHEQUING");
        }
        if (to.getAccountType() != AccountType.CHEQUING) {
            throw new IllegalArgumentException("Destination account must be CHEQUING");
        }

        if (!CAD.equalsIgnoreCase(from.getCurrency()) || !CAD.equalsIgnoreCase(to.getCurrency())) {
            throw new IllegalArgumentException("Both accounts must be CAD");
        }

        if (from.getBalanceCents() < amount) {
            throw new IllegalArgumentException("insufficient treasury funds");
        }

        TransferEntity t = newCompletedTransfer(from, to, amount, CAD, idempotencyKey);

        SaveTransferOutcome saveOutcome = saveTransferWithIdempotentReplay(
                t,
                idempotencyKey,
                () -> transferRepository.findByIdempotencyKey(idempotencyKey),
                winner -> sameTransferRequest(winner, treasuryAccountId, req.toAccountId(), amount, CAD),
                false
        );

        t = saveOutcome.transfer();
        if (saveOutcome.replayed()) {
            return transferResponseMapper.toUserResponse(t, systemUser.getId());
        }

        transferSettlementService.applyLedgerAndBalances(t, from, to, amount, CAD);

        transferAuditService.logAdminDeposit(
                adminActor,
                t.getId(),
                from.getId(),
                to.getId(),
                amount,
                CAD
        );

        return transferResponseMapper.toUserResponse(t, systemUser.getId());
    }

    @Transactional(readOnly = true)
    public TransferPageResponse listTransfers(Long actorUserId, int limit, String cursor) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, safeLimit + 1);

        List<TransferEntity> page;
        if (cursor == null || cursor.isBlank()) {
            page = transferRepository.findFirstPageForUser(actorUserId, pageable);
        } else {
            long[] decoded = decodeCursor(cursor);
            Instant beforeCreatedAt = Instant.ofEpochMilli(decoded[0]);
            long beforeId = decoded[1];

            page = transferRepository.findPageForUserBefore(actorUserId, beforeCreatedAt, beforeId, pageable);
        }

        String nextCursor = null;
        if (page.size() > safeLimit) {
            var last = page.get(safeLimit - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getId());
            page = page.subList(0, safeLimit);
        }

        var items = page.stream().map(t -> transferResponseMapper.toUserResponse(t, actorUserId)).toList();
        return new TransferPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public List<AdminHeldTransferResponse> listHeldTransfers(Long adminUserId) {
        requireAdminActor(adminUserId);

        return transferRepository.findByStatusOrderByCreatedAtAsc(TransferStatus.PENDING_REVIEW)
                .stream()
                .map(transferResponseMapper::toAdminHeldResponse)
                .toList();
    }

    @Transactional
    public AdminHeldTransferResponse approveHeldTransfer(Long adminUserId, Long transferId) {
        UserEntity adminActor = requireAdminActor(adminUserId);
        TransferEntity t = requirePendingTransferForUpdate(transferId);

        List<Long> ids = List.of(t.getFromAccount().getId(), t.getToAccount().getId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(t.getFromAccount().getId()) ? locked.get(0) : locked.get(1);
        AccountEntity to   = locked.get(0).getId().equals(t.getToAccount().getId())   ? locked.get(0) : locked.get(1);

        if (!t.getCurrency().equalsIgnoreCase(from.getCurrency()) || !t.getCurrency().equalsIgnoreCase(to.getCurrency())) {
            throw new IllegalArgumentException("currency must match both accounts");
        }

        if (!"ACTIVE".equalsIgnoreCase(to.getStatus())) {
            throw new IllegalArgumentException("To account is not active");
        }

        transferSettlementService.applyHeldTransferApprovalCredit(t, to, t.getAmountCents(), t.getCurrency());

        t.setStatus(TransferStatus.COMPLETED);
        t.setRiskDecision("APPROVE");
        transferRepository.save(t);

        transferAuditService.logTransferApprove(
                adminActor,
                t.getId(),
                to.getId(),
                t.getAmountCents(),
                t.getCurrency()
        );

        return transferResponseMapper.toAdminHeldResponse(t);
    }

    @Transactional
    public AdminHeldTransferResponse rejectHeldTransfer(Long adminUserId, Long transferId, String reason) {
        UserEntity adminActor = requireAdminActor(adminUserId);
        TransferEntity t = requirePendingTransferForUpdate(transferId);

        List<Long> ids = List.of(t.getFromAccount().getId(), t.getToAccount().getId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(t.getFromAccount().getId()) ? locked.get(0) : locked.get(1);

        transferSettlementService.applyHeldTransferRefund(t, from, t.getAmountCents(), t.getCurrency());

        String finalReason = (reason == null || reason.isBlank())
                ? "rejected during manual review"
                : reason.trim();

        t.setStatus(TransferStatus.REJECTED);
        t.setRiskDecision("BLOCK");
        t.setRiskReasons(finalReason);
        transferRepository.save(t);

        transferAuditService.logTransferReject(
                adminActor,
                t.getId(),
                from.getId(),
                t.getAmountCents(),
                t.getCurrency(),
                finalReason
        );

        return transferResponseMapper.toAdminHeldResponse(t);
    }

    private String encodeCursor(Instant createdAt, Long id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private long[] decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("bad cursor");
            long createdAtMillis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            return new long[]{createdAtMillis, id};
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
        }
    }

    private UserEntity requireAdminActor(Long adminUserId) {
        UserEntity adminActor = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (adminActor.getRole() == null || !"ADMIN".equalsIgnoreCase(adminActor.getRole().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }

        return adminActor;
    }

    private TransferEntity requirePendingTransferForUpdate(Long transferId) {
        TransferEntity t = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));

        if (t.getStatus() != TransferStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "transfer is not pending review"
            );
        }

        return t;
    }

    private RiskHoldDecision evaluateRiskHoldDecision(Long actorUserId, long amountCents, Instant now) {
        boolean amountHold = amountCents >= RISK_HOLD_THRESHOLD_CENTS;

        TransferVelocityRiskService.VelocitySnapshot velocitySnapshot =
                transferVelocityRiskService.getSnapshot(actorUserId, amountCents, now);

        boolean cumulativeHold = velocitySnapshot.hold();

        boolean existingPendingHold =
                transferRepository.existsByFromAccount_User_IdAndStatus(actorUserId, TransferStatus.PENDING_REVIEW);

        if (!amountHold && !cumulativeHold && !existingPendingHold) {
            return new RiskHoldDecision(false, null, null);
        }

        StringBuilder reason = new StringBuilder();

        if (amountHold) {
            reason.append("single transfer amount meets or exceeds $5,000");
        }

        if (cumulativeHold) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append(velocitySnapshot.reason());
        }

        if (existingPendingHold) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append("existing outgoing transfer is still pending review");
        }

        int score = 0;
        int triggerCount = 0;

        if (amountHold) {
            score = Math.max(score, 90);
            triggerCount++;
        }

        if (cumulativeHold) {
            score = Math.max(score, velocitySnapshot.score() == null ? 85 : velocitySnapshot.score());
            triggerCount++;
        }

        if (existingPendingHold) {
            score = Math.max(score, 95);
            triggerCount++;
        }

        if (triggerCount >= 2) {
            score = Math.min(99, score + 3);
        }

        // TODO: Future implementation:
        // add automated review / AI-assisted risk scoring so low-risk holds can be auto-cleared
        // within a short review window instead of always requiring manual admin action.

        return new RiskHoldDecision(true, reason.toString(), score);
    }

    private TransferEntity newHeldTransfer(
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency,
            String idempotencyKey,
            RiskHoldDecision riskHoldDecision
    ) {
        TransferEntity t = new TransferEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmountCents(amountCents);
        t.setCurrency(currency);
        t.setStatus(TransferStatus.PENDING_REVIEW);
        t.setRiskDecision("HOLD");
        t.setRiskScore(riskHoldDecision.score());
        t.setRiskReasons(riskHoldDecision.reason());
        t.setIdempotencyKey(idempotencyKey);
        return t;
    }

    private TransferEntity newCompletedTransfer(
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency,
            String idempotencyKey
    ) {
        TransferEntity t = new TransferEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmountCents(amountCents);
        t.setCurrency(currency);
        t.setStatus(TransferStatus.COMPLETED);
        t.setIdempotencyKey(idempotencyKey);
        return t;
    }

    private boolean sameTransferRequest(
            TransferEntity t,
            Long expectedFromAccountId,
            Long expectedToAccountId,
            long expectedAmountCents,
            String expectedCurrency
    ) {
        return t.getFromAccount().getId().equals(expectedFromAccountId)
                && t.getToAccount().getId().equals(expectedToAccountId)
                && t.getAmountCents() == expectedAmountCents
                && t.getCurrency().equalsIgnoreCase(expectedCurrency);
    }

    private SaveTransferOutcome saveTransferWithIdempotentReplay(
            TransferEntity candidate,
            String idempotencyKey,
            Supplier<Optional<TransferEntity>> replayLookup,
            Predicate<TransferEntity> sameRequest,
            boolean rethrowUnknownDuplicate
    ) {
        try {
            TransferEntity saved = transferRepository.saveAndFlush(candidate);
            entityManager.refresh(saved);
            return new SaveTransferOutcome(saved, false);
        } catch (DataIntegrityViolationException dup) {
            Optional<TransferEntity> winnerOpt = replayLookup.get();

            if (winnerOpt.isPresent()) {
                TransferEntity winner = winnerOpt.get();

                if (sameRequest.test(winner)) {
                    return new SaveTransferOutcome(winner, true);
                }

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            if (!rethrowUnknownDuplicate || transferRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used");
            }

            throw dup;
        }
    }

    private record RiskHoldDecision(boolean hold, String reason, Integer score) {}

    private record SaveTransferOutcome(TransferEntity transfer, boolean replayed) {}

}
