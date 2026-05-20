package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.audit.AuditLogEntity;
import com.sarim.digitalbanking.audit.AuditLogRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountManagementController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminAccountManagementController(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/users/{userId}/accounts")
    public UserAccountsResponse getUserAccounts(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        requireUid(request);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        List<AccountEntity> accounts = accountRepository.findByUserIdOrderByIdAsc(userId);

        List<AccountDetailsResponse> accountDetails = accounts.stream()
                .map(a -> new AccountDetailsResponse(
                        a.getId(),
                        a.getAccountType().name(),
                        a.getCurrency(),
                        a.getStatus(),
                        a.getBalanceCents()
                ))
                .collect(Collectors.toList());

        return new UserAccountsResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                accountDetails
        );
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @Transactional
    public AccountDetailsResponse freezeAccount(
            @PathVariable Long accountId,
            HttpServletRequest request
    ) {
        Long actorUid = requireUid(request);

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account must be ACTIVE to freeze");
        }

        account.setStatus("FROZEN");
        accountRepository.save(account);

        // Audit log
        UserEntity actor = userRepository.findById(actorUid).orElse(null);
        UserEntity affectedUser = account.getUser();

        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorUser(actor);
        audit.setAffectedUser(affectedUser);
        audit.setAction("ACCOUNT_FREEZE");
        audit.setEntityType("account");
        audit.setEntityId(String.valueOf(accountId));
        audit.setDetails(String.format("accountId=%d,previousStatus=ACTIVE,newStatus=FROZEN", accountId));
        auditLogRepository.save(audit);

        return new AccountDetailsResponse(
                account.getId(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getStatus(),
                account.getBalanceCents()
        );
    }

    @PostMapping("/accounts/{accountId}/unfreeze")
    @Transactional
    public AccountDetailsResponse unfreezeAccount(
            @PathVariable Long accountId,
            HttpServletRequest request
    ) {
        Long actorUid = requireUid(request);

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));

        if (!"FROZEN".equalsIgnoreCase(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account must be FROZEN to unfreeze");
        }

        account.setStatus("ACTIVE");
        accountRepository.save(account);

        // Audit log
        UserEntity actor = userRepository.findById(actorUid).orElse(null);
        UserEntity affectedUser = account.getUser();

        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorUser(actor);
        audit.setAffectedUser(affectedUser);
        audit.setAction("ACCOUNT_UNFREEZE");
        audit.setEntityType("account");
        audit.setEntityId(String.valueOf(accountId));
        audit.setDetails(String.format("accountId=%d,previousStatus=FROZEN,newStatus=ACTIVE", accountId));
        auditLogRepository.save(audit);

        return new AccountDetailsResponse(
                account.getId(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getStatus(),
                account.getBalanceCents()
        );
    }

    @PostMapping("/accounts/{accountId}/close")
    @Transactional
    public AccountDetailsResponse closeAccount(
            @PathVariable Long accountId,
            HttpServletRequest request
    ) {
        Long actorUid = requireUid(request);

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));

        String currentStatus = account.getStatus().toUpperCase();
        if (!currentStatus.equals("ACTIVE") && !currentStatus.equals("FROZEN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account must be ACTIVE or FROZEN to close");
        }

        if (account.getBalanceCents() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot close account with non-zero balance");
        }

        String previousStatus = account.getStatus();
        account.setStatus("CLOSED");
        accountRepository.save(account);

        // Audit log
        UserEntity actor = userRepository.findById(actorUid).orElse(null);
        UserEntity affectedUser = account.getUser();

        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorUser(actor);
        audit.setAffectedUser(affectedUser);
        audit.setAction("ACCOUNT_CLOSE");
        audit.setEntityType("account");
        audit.setEntityId(String.valueOf(accountId));
        audit.setDetails(String.format("accountId=%d,previousStatus=%s,newStatus=CLOSED,balanceCents=0", accountId, previousStatus));
        auditLogRepository.save(audit);

        return new AccountDetailsResponse(
                account.getId(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getStatus(),
                account.getBalanceCents()
        );
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}