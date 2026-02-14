package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.ledger.LedgerDirection;
import com.sarim.digitalbanking.ledger.LedgerEntryEntity;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import com.sarim.digitalbanking.transfers.api.CreateTransferRequest;
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;


import java.util.Comparator;
import java.util.List;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;


    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }


    @Transactional
    public TransferResponse createTransfer(Long actorUserId, String idempotencyKey, CreateTransferRequest req) {
        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must be different");
        }

        String currency = (req.currency() == null || req.currency().isBlank())
                ? "CAD"
                : req.currency().trim().toUpperCase();

        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }

        long amount = req.amountCents();

        List<Long> ids = List.of(req.fromAccountId(), req.toAccountId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<AccountEntity> locked = accountRepository.findByIdInForUpdate(ids);
        if (locked.size() != 2) {
            throw new IllegalArgumentException("Account not found");
        }

        AccountEntity from = locked.get(0).getId().equals(req.fromAccountId()) ? locked.get(0) : locked.get(1);
        AccountEntity to   = locked.get(0).getId().equals(req.toAccountId())   ? locked.get(0) : locked.get(1);

        if (!from.getUser().getId().equals(actorUserId)) {
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
            return transferRepository.findByIdempotencyKey(idempotencyKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> dup);
        }

        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(t);
        debit.setAccount(from);
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmountCents(amount);
        debit.setCurrency(currency);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(t);
        credit.setAccount(to);
        credit.setDirection(LedgerDirection.CREDIT);
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
        log.setDetails("from=" + from.getId() + ", to=" + to.getId() + ", amount_cents=" + amount + ", currency=" + currency);

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
