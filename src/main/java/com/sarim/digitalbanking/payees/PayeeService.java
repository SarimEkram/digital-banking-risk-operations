package com.sarim.digitalbanking.payees;

import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.payees.api.CreatePayeeRequest;
import com.sarim.digitalbanking.payees.api.PayeeResponse;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    public PayeeService(
            PayeeRepository payeeRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            EntityManager entityManager
    ) {
        this.payeeRepository = payeeRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public PayeeResponse addPayee(Long ownerUserId, CreatePayeeRequest req) {
        String email = normalizeEmail(req.email());

        UserEntity payeeUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("payee email not found"));

        if (payeeUser.getId().equals(ownerUserId)) {
            throw new IllegalArgumentException("cannot add yourself as payee");
        }

        // If it already exists and is disabled -> re-enable it.
        var existing = payeeRepository.findByOwnerUserIdAndPayeeUserId(ownerUserId, payeeUser.getId());
        if (existing.isPresent()) {
            PayeeEntity p = existing.get();
            if ("DISABLED".equalsIgnoreCase(p.getStatus())) {
                p.setStatus("ACTIVE");
                if (req.label() != null && !req.label().isBlank()) {
                    p.setLabel(req.label().trim());
                }
                PayeeEntity saved = payeeRepository.save(p);
                audit(ownerUserId, "PAYEE_ENABLE", "payee", String.valueOf(saved.getId()),
                        "payee_email=" + saved.getPayeeEmail() + ", payee_user_id=" + payeeUser.getId());
                return toResponse(saved);
            }
            // keep error style consistent (409 via ApiExceptionHandler's conflict handler)
            throw new DataIntegrityViolationException("conflict");
        }

        UserEntity owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PayeeEntity p = new PayeeEntity();
        p.setOwnerUser(owner);
        p.setPayeeUser(payeeUser);
        p.setPayeeEmail(email);
        p.setLabel(req.label() == null ? null : req.label().trim());
        p.setStatus("ACTIVE");

        PayeeEntity saved = payeeRepository.saveAndFlush(p);
        entityManager.refresh(saved);

        audit(ownerUserId, "PAYEE_ADD", "payee", String.valueOf(saved.getId()),
                "payee_email=" + saved.getPayeeEmail() + ", payee_user_id=" + payeeUser.getId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PayeeResponse> listActivePayees(Long ownerUserId) {
        return payeeRepository.findByOwnerUserIdAndStatusOrderByIdAsc(ownerUserId, "ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PayeeResponse disablePayee(Long ownerUserId, Long payeeId) {
        PayeeEntity p = payeeRepository.findByIdAndOwnerUser_Id(payeeId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("payee not found"));

        if (!"DISABLED".equalsIgnoreCase(p.getStatus())) {
            p.setStatus("DISABLED");
            payeeRepository.save(p);

            audit(ownerUserId, "PAYEE_DISABLE", "payee", String.valueOf(p.getId()),
                    "payee_email=" + p.getPayeeEmail() + ", payee_user_id=" + p.getPayeeUser().getId());
        }

        return toResponse(p);
    }

    private PayeeResponse toResponse(PayeeEntity p) {
        return new PayeeResponse(
                p.getId(),
                p.getPayeeEmail(),
                p.getLabel(),
                p.getStatus(),
                p.getCreatedAt()
        );
    }

    private void audit(Long actorUserId, String action, String entityType, String entityId, String details) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AuditLogEntity log = new AuditLogEntity();
        log.setActorUser(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);

        auditLogRepository.save(log);
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("email is required");
        String e = email.trim().toLowerCase();
        if (e.isBlank()) throw new IllegalArgumentException("email is required");
        return e;
    }
}
