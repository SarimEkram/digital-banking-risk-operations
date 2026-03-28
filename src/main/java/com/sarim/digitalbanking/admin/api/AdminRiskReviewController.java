package com.sarim.digitalbanking.admin.api;

import com.sarim.digitalbanking.transfers.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/transfers")
public class AdminRiskReviewController {

    private final TransferService transferService;

    public AdminRiskReviewController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/held")
    public List<AdminHeldTransferResponse> listHeld(HttpServletRequest request) {
        Long adminUserId = requireUid(request);
        return transferService.listHeldTransfers(adminUserId);
    }

    @PostMapping("/{transferId}/approve")
    public AdminHeldTransferResponse approve(
            @PathVariable Long transferId,
            HttpServletRequest request
    ) {
        Long adminUserId = requireUid(request);
        return transferService.approveHeldTransfer(adminUserId, transferId);
    }

    @PostMapping("/{transferId}/reject")
    public AdminHeldTransferResponse reject(
            @PathVariable Long transferId,
            @RequestBody(required = false) RejectHeldTransferRequest body,
            HttpServletRequest request
    ) {
        Long adminUserId = requireUid(request);
        String reason = body == null ? null : body.reason();
        return transferService.rejectHeldTransfer(adminUserId, transferId, reason);
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}