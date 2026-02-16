package com.sarim.digitalbanking.transfers.api;

import com.sarim.digitalbanking.idempotency.IdempotencyKeyUtil;
import com.sarim.digitalbanking.transfers.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.sarim.digitalbanking.transfers.api.TransferPageResponse;


@RestController
@RequestMapping("/api/transfers")
public class TransfersController {

    private final TransferService transferService;

    public TransfersController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public TransferResponse create(@Valid @RequestBody CreateTransferRequest body, HttpServletRequest request) {
        Long uid = requireUid(request);
        String idem = requireIdempotencyKey(request);

        return transferService.createTransfer(uid, idem, body);
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

    @GetMapping
    public TransferPageResponse list(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request
    ) {
        Long uid = requireUid(request);
        return transferService.listTransfers(uid, limit, cursor);
    }

}
