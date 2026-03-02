package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.idempotency.IdempotencyKeyUtil;
import com.sarim.digitalbanking.transfers.TransferService;
import com.sarim.digitalbanking.transfers.api.TransferResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminDepositController {

    private final TransferService transferService;

    public AdminDepositController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/deposit")
    public TransferResponse deposit(
            @Valid @RequestBody CreateAdminDepositRequest body,
            HttpServletRequest request
    ) {
        Long adminUserId = requireUid(request);
        String idem = requireIdempotencyKey(request);

        return transferService.createAdminDeposit(adminUserId, idem, body);
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }

    private String requireIdempotencyKey(HttpServletRequest request) {
        Object key = request.getAttribute(IdempotencyKeyUtil.ATTR);
        if (key instanceof String s && !s.isBlank()) return s;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing Idempotency-Key");
    }
}