package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.accounts.AccountType;
import com.sarim.digitalbanking.admin.api.AdminHeldTransferResponse;
import com.sarim.digitalbanking.admin.api.CreateAdminDepositRequest;
import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.ledger.LedgerDirection;
import com.sarim.digitalbanking.ledger.LedgerEntryEntity;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
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
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PayeeRepository payeeRepository;
    private final EntityManager entityManager;
    private final TransferVelocityRiskService transferVelocityRiskService;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            PayeeRepository payeeRepository,
            EntityManager entityManager,
            TransferVelocityRiskService transferVelocityRiskService
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.payeeRepository = payeeRepository;
        this.entityManager = entityManager;
        this.transferVelocityRiskService = transferVelocityRiskService;
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

            return toResponse(t, actorUserId);
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
            return toResponse(t, actorUserId);
        }

        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (holdForReview) {
            applyHeldTransferReserve(t, from, amount, currency);

            AuditLogEntity log = new AuditLogEntity();
            log.setActorUser(actor);
            log.setAction("TRANSFER_HELD");
            log.setEntityType("transfer");
            log.setEntityId(String.valueOf(t.getId()));
            log.setDetails("from=" + from.getId()
                    + ", payee_id=" + payee.getId()
                    + ", to=" + to.getId()
                    + ", amount_cents=" + amount
                    + ", currency=" + currency
                    + ", reason=" + riskHoldDecision.reason()
                    + ", funds_reserved=true");

            auditLogRepository.save(log);

            transferVelocityRiskService.recordSuccessfulTransferAfterCommit(actorUserId, t.getId(), amount, riskEvaluatedAt);

            return toResponse(t, actorUserId);
        }

        applyLedgerAndBalances(t, from, to, amount, currency);

        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(actor);
        log.setAction("TRANSFER_CREATE");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(t.getId()));
        log.setDetails("from=" + from.getId()
                + ", payee_id=" + payee.getId()
                + ", to=" + to.getId()
                + ", amount_cents=" + amount
                + ", currency=" + currency);

        auditLogRepository.save(log);

        transferVelocityRiskService.recordSuccessfulTransferAfterCommit(actorUserId, t.getId(), amount, riskEvaluatedAt);

        return toResponse(t, actorUserId);
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

            return toResponse(t, systemUser.getId());
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
            return toResponse(t, systemUser.getId());
        }

        applyLedgerAndBalances(t, from, to, amount, CAD);

        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAction("ADMIN_DEPOSIT");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(t.getId()));
        log.setDetails("from_treasury=" + from.getId()
                + ", to=" + to.getId()
                + ", amount_cents=" + amount
                + ", currency=" + CAD);
        auditLogRepository.save(log);

        return toResponse(t, systemUser.getId());
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

        var items = page.stream().map(t -> toResponse(t, actorUserId)).toList();
        return new TransferPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public List<AdminHeldTransferResponse> listHeldTransfers(Long adminUserId) {
        requireAdminActor(adminUserId);

        return transferRepository.findByStatusOrderByCreatedAtAsc(TransferStatus.PENDING_REVIEW)
                .stream()
                .map(this::toAdminHeldResponse)
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

        applyHeldTransferApprovalCredit(t, to, t.getAmountCents(), t.getCurrency());

        t.setStatus(TransferStatus.COMPLETED);
        t.setRiskDecision("APPROVE");
        transferRepository.save(t);

        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAction("TRANSFER_APPROVE");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(t.getId()));
        log.setDetails("to=" + to.getId()
                + ", amount_cents=" + t.getAmountCents()
                + ", currency=" + t.getCurrency());

        auditLogRepository.save(log);

        return toAdminHeldResponse(t);
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

        applyHeldTransferRefund(t, from, t.getAmountCents(), t.getCurrency());

        String finalReason = (reason == null || reason.isBlank())
                ? "rejected during manual review"
                : reason.trim();

        t.setStatus(TransferStatus.REJECTED);
        t.setRiskDecision("BLOCK");
        t.setRiskReasons(finalReason);
        transferRepository.save(t);

        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAction("TRANSFER_REJECT");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(t.getId()));
        log.setDetails("from=" + from.getId()
                + ", amount_cents=" + t.getAmountCents()
                + ", currency=" + t.getCurrency()
                + ", reason=" + finalReason);

        auditLogRepository.save(log);

        return toAdminHeldResponse(t);
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

    private void applyHeldTransferReserve(
            TransferEntity transfer,
            AccountEntity from,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(transfer);
        debit.setAccount(from);
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmountCents(amountCents);
        debit.setCurrency(currency);

        ledgerEntryRepository.save(debit);

        from.setBalanceCents(from.getBalanceCents() - amountCents);
        accountRepository.save(from);
    }

    private void applyHeldTransferApprovalCredit(
            TransferEntity transfer,
            AccountEntity to,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(transfer);
        credit.setAccount(to);
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmountCents(amountCents);
        credit.setCurrency(currency);

        ledgerEntryRepository.save(credit);

        to.setBalanceCents(to.getBalanceCents() + amountCents);
        accountRepository.save(to);
    }

    private void applyHeldTransferRefund(
            TransferEntity transfer,
            AccountEntity from,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity refund = new LedgerEntryEntity();
        refund.setTransfer(transfer);
        refund.setAccount(from);
        refund.setDirection(LedgerDirection.CREDIT);
        refund.setAmountCents(amountCents);
        refund.setCurrency(currency);

        ledgerEntryRepository.save(refund);

        from.setBalanceCents(from.getBalanceCents() + amountCents);
        accountRepository.save(from);
    }

    private void applyLedgerAndBalances(
            TransferEntity transfer,
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(transfer);
        debit.setAccount(from);
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmountCents(amountCents);
        debit.setCurrency(currency);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(transfer);
        credit.setAccount(to);
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmountCents(amountCents);
        credit.setCurrency(currency);

        ledgerEntryRepository.saveAll(List.of(debit, credit));

        from.setBalanceCents(from.getBalanceCents() - amountCents);
        to.setBalanceCents(to.getBalanceCents() + amountCents);
        accountRepository.saveAll(List.of(from, to));
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

    private AdminHeldTransferResponse toAdminHeldResponse(TransferEntity t) {
        String fromEmail = t.getFromAccount().getUser().getEmail();
        String toEmail = t.getToAccount().getUser().getEmail();

        return new AdminHeldTransferResponse(
                t.getId(),
                t.getFromAccount().getId(),
                t.getToAccount().getId(),
                t.getAmountCents(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getRiskDecision(),
                t.getRiskScore(),
                t.getRiskReasons(),
                t.getCreatedAt(),
                fromEmail,
                toEmail
        );
    }

    private TransferResponse toResponse(TransferEntity t, Long actorUserId) {
        Long fromUid = t.getFromAccount().getUser().getId();
        Long toUid   = t.getToAccount().getUser().getId();

        String fromEmail = t.getFromAccount().getUser().getEmail();
        String toEmail   = t.getToAccount().getUser().getEmail();

        String direction = "UNKNOWN";
        String counterpartyEmail = null;

        if (actorUserId != null && actorUserId.equals(fromUid)) {
            direction = "SENT";
            counterpartyEmail = toEmail;
        } else if (actorUserId != null && actorUserId.equals(toUid)) {
            direction = "RECEIVED";
            counterpartyEmail = fromEmail;
        }

        return new TransferResponse(
                t.getId(),
                t.getFromAccount().getId(),
                t.getToAccount().getId(),
                t.getAmountCents(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getRiskDecision(),
                t.getRiskScore(),
                t.getRiskReasons(),
                t.getCreatedAt(),
                fromEmail,
                toEmail,
                direction,
                counterpartyEmail
        );
    }
}
