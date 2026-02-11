package com.sarim.digitalbanking.accounts.api;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final AccountRepository accountRepository;

    public AccountsController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<AccountResponse> listMyAccounts(HttpServletRequest request) {
        Long uid = requireUid(request);

        return accountRepository.findByUserIdOrderByIdAsc(uid).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse getMyAccount(@PathVariable Long id, HttpServletRequest request) {
        Long uid = requireUid(request);

        AccountEntity acc = accountRepository.findByIdAndUser_Id(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        return toResponse(acc);
    }

    private AccountResponse toResponse(AccountEntity a) {
        return new AccountResponse(
                a.getId(),
                a.getCurrency(),
                a.getBalanceCents(),
                a.getStatus()
        );
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}
