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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sarim.digitalbanking.idempotency.IdempotencyReplayService;
import com.sarim.digitalbanking.idempotency.IdempotencyRequestHasher;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class TransferService {

    private static final String SYSTEM_USER_EMAIL = "system@bank.local";
    private static final String CAD = "CAD";


    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final PayeeRepository payeeRepository;
    private final TransferVelocityRiskService transferVelocityRiskService;
    private final TransferResponseMapper transferResponseMapper;
    private final TransferSettlementService transferSettlementService;
    private final TransferAuditService transferAuditService;
    private final TransferRiskDecisionService transferRiskDecisionService;
    private final TransferPersistenceService transferPersistenceService;
    private final TransferFactory transferFactory;
    private final TransferCursorCodec transferCursorCodec;
    private final TransferAdminReviewGuard transferAdminReviewGuard;
    private final IdempotencyReplayService idempotencyReplayService;
    private final IdempotencyRequestHasher idempotencyRequestHasher;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            UserRepository userRepository,
            PayeeRepository payeeRepository,
            TransferVelocityRiskService transferVelocityRiskService,
            TransferResponseMapper transferResponseMapper,
            TransferSettlementService transferSettlementService,
            TransferAuditService transferAuditService,
            TransferRiskDecisionService transferRiskDecisionService,
            TransferPersistenceService transferPersistenceService,
            TransferFactory transferFactory,
            TransferCursorCodec transferCursorCodec,
            TransferAdminReviewGuard transferAdminReviewGuard,
            IdempotencyReplayService idempotencyReplayService,
            IdempotencyRequestHasher idempotencyRequestHasher
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.payeeRepository = payeeRepository;
        this.transferVelocityRiskService = transferVelocityRiskService;
        this.transferResponseMapper = transferResponseMapper;
        this.transferSettlementService = transferSettlementService;
        this.transferAuditService = transferAuditService;
        this.transferRiskDecisionService = transferRiskDecisionService;
        this.transferPersistenceService = transferPersistenceService;
        this.transferFactory = transferFactory;
        this.transferCursorCodec = transferCursorCodec;
        this.transferAdminReviewGuard = transferAdminReviewGuard;
        this.idempotencyReplayService = idempotencyReplayService;
        this.idempotencyRequestHasher = idempotencyRequestHasher;
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

        String requestHash = idempotencyRequestHasher.hashUserTransfer(actorUserId, req);

        var storedReplay = idempotencyReplayService.findStoredTransferReplay(idempotencyKey, requestHash);
        if (storedReplay.isPresent()) {
            return storedReplay.get();
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
        TransferRiskDecisionService.RiskHoldDecision riskHoldDecision =
                transferRiskDecisionService.evaluateRiskHoldDecision(actorUserId, amount, riskEvaluatedAt);

        boolean holdForReview = riskHoldDecision.hold();

        TransferEntity t = holdForReview
                ? transferFactory.newHeldTransfer(from, to, amount, currency, idempotencyKey, riskHoldDecision)
                : transferFactory.newCompletedTransfer(from, to, amount, currency, idempotencyKey);

        TransferPersistenceService.SaveTransferOutcome saveOutcome =
                transferPersistenceService.saveTransferWithIdempotentReplay(
                        t,
                        idempotencyKey,
                        () -> transferRepository.findByIdempotencyKeyAndFromAccount_User_Id(idempotencyKey, actorUserId),
                        winner -> sameTransferRequest(winner, req.fromAccountId(), toAccountId, amount, currency),
                        true
                );

        t = saveOutcome.transfer();
        if (saveOutcome.replayed()) {
            TransferResponse response = transferResponseMapper.toUserResponse(t, actorUserId);
            idempotencyReplayService.storeTransferResponse(idempotencyKey, requestHash, 200, response);
            return response;
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

            TransferResponse response = transferResponseMapper.toUserResponse(t, actorUserId);
            idempotencyReplayService.storeTransferResponse(idempotencyKey, requestHash, 200, response);
            return response;
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

        TransferResponse response = transferResponseMapper.toUserResponse(t, actorUserId);
        idempotencyReplayService.storeTransferResponse(idempotencyKey, requestHash, 200, response);
        return response;
    }

    @Transactional
    public TransferResponse createAdminDeposit(Long adminUserId, String idempotencyKey, CreateAdminDepositRequest req) {
        long amount = req.amountCents();
        if (amount <= 0) {
            throw new IllegalArgumentException("amountCents must be > 0");
        }

        String requestHash = idempotencyRequestHasher.hashAdminDeposit(req);

        var storedReplay = idempotencyReplayService.findStoredTransferReplay(idempotencyKey, requestHash);
        if (storedReplay.isPresent()) {
            return storedReplay.get();
        }

        UserEntity adminActor = transferAdminReviewGuard.requireAdminActor(adminUserId);

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

        TransferEntity t = transferFactory.newCompletedTransfer(from, to, amount, CAD, idempotencyKey);

        TransferPersistenceService.SaveTransferOutcome saveOutcome =
                transferPersistenceService.saveTransferWithIdempotentReplay(
                        t,
                        idempotencyKey,
                        () -> transferRepository.findByIdempotencyKey(idempotencyKey),
                        winner -> sameTransferRequest(winner, treasuryAccountId, req.toAccountId(), amount, CAD),
                        false
                );

        t = saveOutcome.transfer();
        if (saveOutcome.replayed()) {
            TransferResponse response = transferResponseMapper.toUserResponse(t, systemUser.getId());
            idempotencyReplayService.storeTransferResponse(idempotencyKey, requestHash, 200, response);
            return response;
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

        TransferResponse response = transferResponseMapper.toUserResponse(t, systemUser.getId());
        idempotencyReplayService.storeTransferResponse(idempotencyKey, requestHash, 200, response);
        return response;
    }

    @Transactional(readOnly = true)
    public TransferPageResponse listTransfers(Long actorUserId, int limit, String cursor) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var pageable = PageRequest.of(0, safeLimit + 1);

        List<TransferEntity> page;
        if (cursor == null || cursor.isBlank()) {
            page = transferRepository.findFirstPageForUser(actorUserId, pageable);
        } else {
            long[] decoded = transferCursorCodec.decode(cursor);
            Instant beforeCreatedAt = Instant.ofEpochMilli(decoded[0]);
            long beforeId = decoded[1];

            page = transferRepository.findPageForUserBefore(actorUserId, beforeCreatedAt, beforeId, pageable);
        }

        String nextCursor = null;
        if (page.size() > safeLimit) {
            var last = page.get(safeLimit - 1);
            nextCursor = transferCursorCodec.encode(last.getCreatedAt(), last.getId());
            page = page.subList(0, safeLimit);
        }

        var items = page.stream().map(t -> transferResponseMapper.toUserResponse(t, actorUserId)).toList();
        return new TransferPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public List<AdminHeldTransferResponse> listHeldTransfers(Long adminUserId) {
        transferAdminReviewGuard.requireAdminActor(adminUserId);

        return transferRepository.findByStatusOrderByCreatedAtAsc(TransferStatus.PENDING_REVIEW)
                .stream()
                .map(transferResponseMapper::toAdminHeldResponse)
                .toList();
    }

    @Transactional
    public AdminHeldTransferResponse approveHeldTransfer(Long adminUserId, Long transferId) {
        UserEntity adminActor = transferAdminReviewGuard.requireAdminActor(adminUserId);
        TransferEntity t = transferAdminReviewGuard.requirePendingTransferForUpdate(transferId);

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
        UserEntity adminActor = transferAdminReviewGuard.requireAdminActor(adminUserId);
        TransferEntity t = transferAdminReviewGuard.requirePendingTransferForUpdate(transferId);

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

}
