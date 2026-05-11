package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class TransferAuditService {

    private final AuditLogRepository auditLogRepository;

    public TransferAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logTransferHeld(
            UserEntity actor,
            UserEntity affectedUser,
            Long transferId,
            Long fromAccountId,
            Long payeeId,
            Long toAccountId,
            long amountCents,
            String currency,
            String reason
    ) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(actor);
        log.setAffectedUser(affectedUser);
        log.setAction("TRANSFER_HELD");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(transferId));
        log.setDetails("from=" + fromAccountId
                + ", payee_id=" + payeeId
                + ", to=" + toAccountId
                + ", amount_cents=" + amountCents
                + ", currency=" + currency
                + ", reason=" + reason
                + ", funds_reserved=true");
        auditLogRepository.save(log);
    }

    public void logTransferHeld(
            UserEntity actor,
            Long transferId,
            Long fromAccountId,
            Long payeeId,
            Long toAccountId,
            long amountCents,
            String currency,
            String reason
    ) {
        logTransferHeld(actor, null, transferId, fromAccountId, payeeId, toAccountId, amountCents, currency, reason);
    }

    public void logTransferCreate(
            UserEntity actor,
            UserEntity affectedUser,
            Long transferId,
            Long fromAccountId,
            Long payeeId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(actor);
        log.setAffectedUser(affectedUser);
        log.setAction("TRANSFER_CREATE");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(transferId));
        log.setDetails("from=" + fromAccountId
                + ", payee_id=" + payeeId
                + ", to=" + toAccountId
                + ", amount_cents=" + amountCents
                + ", currency=" + currency);
        auditLogRepository.save(log);
    }

    public void logTransferCreate(
            UserEntity actor,
            Long transferId,
            Long fromAccountId,
            Long payeeId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        logTransferCreate(actor, null, transferId, fromAccountId, payeeId, toAccountId, amountCents, currency);
    }

    public void logAdminDeposit(
            UserEntity adminActor,
            UserEntity affectedUser,
            Long transferId,
            Long fromTreasuryAccountId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAffectedUser(affectedUser);
        log.setAction("ADMIN_DEPOSIT");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(transferId));
        log.setDetails("from_treasury=" + fromTreasuryAccountId
                + ", to=" + toAccountId
                + ", amount_cents=" + amountCents
                + ", currency=" + currency);
        auditLogRepository.save(log);
    }

    public void logAdminDeposit(
            UserEntity adminActor,
            Long transferId,
            Long fromTreasuryAccountId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        logAdminDeposit(adminActor, null, transferId, fromTreasuryAccountId, toAccountId, amountCents, currency);
    }

    public void logTransferApprove(
            UserEntity adminActor,
            UserEntity affectedUser,
            Long transferId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAffectedUser(affectedUser);
        log.setAction("TRANSFER_APPROVE");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(transferId));
        log.setDetails("to=" + toAccountId
                + ", amount_cents=" + amountCents
                + ", currency=" + currency);
        auditLogRepository.save(log);
    }

    public void logTransferApprove(
            UserEntity adminActor,
            Long transferId,
            Long toAccountId,
            long amountCents,
            String currency
    ) {
        logTransferApprove(adminActor, null, transferId, toAccountId, amountCents, currency);
    }

    public void logTransferReject(
            UserEntity adminActor,
            UserEntity affectedUser,
            Long transferId,
            Long fromAccountId,
            long amountCents,
            String currency,
            String reason
    ) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(adminActor);
        log.setAffectedUser(affectedUser);
        log.setAction("TRANSFER_REJECT");
        log.setEntityType("transfer");
        log.setEntityId(String.valueOf(transferId));
        log.setDetails("from=" + fromAccountId
                + ", amount_cents=" + amountCents
                + ", currency=" + currency
                + ", reason=" + reason);
        auditLogRepository.save(log);
    }

    public void logTransferReject(
            UserEntity adminActor,
            Long transferId,
            Long fromAccountId,
            long amountCents,
            String currency,
            String reason
    ) {
        logTransferReject(adminActor, null, transferId, fromAccountId, amountCents, currency, reason);
    }
}