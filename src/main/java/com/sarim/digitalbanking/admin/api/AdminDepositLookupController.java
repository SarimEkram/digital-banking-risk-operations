package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.accounts.AccountType;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/deposit")
public class AdminDepositLookupController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public AdminDepositLookupController(
            UserRepository userRepository,
            AccountRepository accountRepository
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/lookup")
    public AdminAccountLookupResponse lookupByEmail(
            @RequestParam String email,
            HttpServletRequest request
    ) {
        requireUid(request);

        String normalized = email == null ? "" : email.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        AccountEntity account = accountRepository
                .findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
                        user.getId(),
                        AccountType.CHEQUING,
                        "CAD",
                        "ACTIVE"
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "active CAD chequing account not found"
                ));

        return new AdminAccountLookupResponse(
                user.getId(),
                user.getEmail(),
                account.getId(),
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
