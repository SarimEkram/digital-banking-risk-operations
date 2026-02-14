package com.sarim.digitalbanking.payees.api;

import com.sarim.digitalbanking.payees.PayeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/payees")
public class PayeesController {

    private final PayeeService payeeService;

    public PayeesController(PayeeService payeeService) {
        this.payeeService = payeeService;
    }

    @PostMapping
    public PayeeResponse add(@Valid @RequestBody CreatePayeeRequest body, HttpServletRequest request) {
        Long uid = requireUid(request);
        return payeeService.addPayee(uid, body);
    }

    @GetMapping
    public List<PayeeResponse> list(HttpServletRequest request) {
        Long uid = requireUid(request);
        return payeeService.listActivePayees(uid);
    }

    @PatchMapping("/{id}/disable")
    public PayeeResponse disable(@PathVariable Long id, HttpServletRequest request) {
        Long uid = requireUid(request);
        return payeeService.disablePayee(uid, id);
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}
