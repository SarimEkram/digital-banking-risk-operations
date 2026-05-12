package com.sarim.digitalbanking.audit.api;

import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.transfers.TransferAdminReviewGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String SCOPE_SELF = "self";
    private static final String SCOPE_USER = "user";
    private static final String SCOPE_ADMIN = "admin";

    private final AuditLogRepository auditLogRepository;
    private final TransferAdminReviewGuard transferAdminReviewGuard;

    public AuditLogController(
            AuditLogRepository auditLogRepository,
            TransferAdminReviewGuard transferAdminReviewGuard
    ) {
        this.auditLogRepository = auditLogRepository;
        this.transferAdminReviewGuard = transferAdminReviewGuard;
    }

    @GetMapping
    public AuditLogPageResponse list(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "50") int size,
            @RequestParam(name = "scope", required = false, defaultValue = SCOPE_SELF) String scope,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "email", required = false) String email,
            HttpServletRequest request
    ) {
        Long adminUserId = requireUid(request);
        transferAdminReviewGuard.requireAdminActor(adminUserId);

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE));

        String safeAction = (action == null || action.isBlank()) ? null : action.trim();
        String safeEmail = (email == null || email.isBlank()) ? null : email.trim();
        String safeScope = (scope == null || scope.isBlank()) ? SCOPE_SELF : scope.trim().toLowerCase();

        Long actorUserId;
        String actorRole;

        switch (safeScope) {
            case SCOPE_SELF -> {
                actorUserId = adminUserId;
                actorRole = null;
            }
            case SCOPE_USER -> {
                if (safeEmail == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "email is required when scope is 'user'"
                    );
                }
                actorUserId = null;
                actorRole = "USER";
            }
            case SCOPE_ADMIN -> {
                if (safeEmail == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "email is required when scope is 'admin'"
                    );
                }
                actorUserId = null;
                actorRole = "ADMIN";
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "scope must be one of: self, user, admin"
            );
        }

        Page<AuditLogEntity> result = auditLogRepository.search(
                safeAction,
                safeEmail,
                actorRole,
                actorUserId,
                PageRequest.of(safePage, safeSize)
        );

        List<AuditLogResponse> items = result.getContent().stream()
                .map(AuditLogController::toResponse)
                .toList();

        return new AuditLogPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private static AuditLogResponse toResponse(AuditLogEntity a) {
        Long actorUserId = a.getActorUser() == null ? null : a.getActorUser().getId();
        String actorEmail = a.getActorUser() == null ? null : a.getActorUser().getEmail();
        Long affectedUserId = a.getAffectedUser() == null ? null : a.getAffectedUser().getId();
        String affectedEmail = a.getAffectedUser() == null ? null : a.getAffectedUser().getEmail();
        return new AuditLogResponse(
                a.getId(),
                actorUserId,
                actorEmail,
                affectedUserId,
                affectedEmail,
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                a.getDetails(),
                a.getCorrelationId(),
                a.getCreatedAt()
        );
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}