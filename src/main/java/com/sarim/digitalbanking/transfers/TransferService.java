package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.accounts.AccountType;
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
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PayeeRepository payeeRepository;
    private final EntityManager entityManager;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            PayeeRepository payeeRepository,
            EntityManager entityManager
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.payeeRepository = payeeRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public TransferResponse createTransfer(Long actorUserId, String idempotencyKey, CreateTransferRequest req) {
        String currency = (req.currency() == null || req.currency().isBlank())
                ? "CAD"
                : req.currency().trim().toUpperCase();

        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }

        long amount = req.amountCents();

        // 1) resolve payeeId -> payee row (must belong to the current user)
        PayeeEntity payee = payeeRepository.findByIdAndOwnerUser_Id(req.payeeId(), actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("payee not found"));

        if (!"ACTIVE".equalsIgnoreCase(payee.getStatus())) {
            throw new IllegalArgumentException("payee is disabled");
        }

        Long payeeUserId = payee.getPayeeUser().getId();

        // 2) find payee’s ACTIVE CHEQUING in the requested currency
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

        // idempotency check (SCOPED to actor)
        var existing = transferRepository.findByIdempotencyKeyAndFromAccount_User_Id(idempotencyKey, actorUserId);
        if (existing.isPresent()) {
            TransferEntity t = existing.get();

            boolean same =
                    t.getFromAccount().getId().equals(req.fromAccountId()) &&
                            t.getToAccount().getId().equals(toAccountId) &&
                            t.getAmountCents() == amount &&
                            t.getCurrency().equalsIgnoreCase(currency);

            if (!same) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request"
                );
            }

            return toResponse(t);
        }

        // If the key exists but doesn't belong to this actor, return a clean 409 (avoid leaking data)
        if (transferRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used"
            );
        }

        // 3) lock both accounts
        List<Long> ids = List.of(req.fromAccountId(), toAccountId).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(req.fromAccountId()) ? locked.get(0) : locked.get(1);
        AccountEntity to   = locked.get(0).getId().equals(toAccountId)          ? locked.get(0) : locked.get(1);

        // 4) ownership + safety checks
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

        // 5) create transfer + ledger entries + balances
        TransferEntity t = new TransferEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmountCents(amount);
        t.setCurrency(currency);
        t.setStatus(TransferStatus.COMPLETED);
        t.setIdempotencyKey(idempotencyKey);

        try {
            t = transferRepository.saveAndFlush(t);
            entityManager.refresh(t);
        } catch (DataIntegrityViolationException dup) {
            // race: if another request with same idempotency key won, return it (ONLY if it belongs to actor)
            var winner = transferRepository.findByIdempotencyKeyAndFromAccount_User_Id(idempotencyKey, actorUserId);
            if (winner.isPresent()) {
                return toResponse(winner.get());
            }

            // key exists but not ours -> clean conflict (avoid 500 + avoid leaking details)
            if (transferRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used"
                );
            }

            // otherwise, rethrow
            throw dup;
        }

        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(t);
        debit.setAccount(from);
        debit.setDirection(com.sarim.digitalbanking.ledger.LedgerDirection.DEBIT);
        debit.setAmountCents(amount);
        debit.setCurrency(currency);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(t);
        credit.setAccount(to);
        credit.setDirection(com.sarim.digitalbanking.ledger.LedgerDirection.CREDIT);
        credit.setAmountCents(amount);
        credit.setCurrency(currency);

        ledgerEntryRepository.saveAll(List.of(debit, credit));

        from.setBalanceCents(from.getBalanceCents() - amount);
        to.setBalanceCents(to.getBalanceCents() + amount);
        accountRepository.saveAll(List.of(from, to));

        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

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

        return toResponse(t);
    }

    private TransferResponse toResponse(TransferEntity t) {
        return new TransferResponse(
                t.getId(),
                t.getFromAccount().getId(),
                t.getToAccount().getId(),
                t.getAmountCents(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getCreatedAt()
        );
    }
}
