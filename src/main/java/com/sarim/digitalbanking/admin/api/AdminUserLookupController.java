package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserLookupController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public AdminUserLookupController(
            UserRepository userRepository,
            AccountRepository accountRepository
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/lookup")
    public UserAccountsResponse lookupByEmail(
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

        List<AccountEntity> accounts = accountRepository.findByUserIdOrderByIdAsc(user.getId());

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

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}