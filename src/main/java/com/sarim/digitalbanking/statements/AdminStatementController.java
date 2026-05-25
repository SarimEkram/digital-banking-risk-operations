package com.sarim.digitalbanking.statements;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users/{userId}/statement")
public class AdminStatementController {

    private final MonthlyStatementService monthlyStatementService;

    public AdminStatementController(MonthlyStatementService monthlyStatementService) {
        this.monthlyStatementService = monthlyStatementService;
    }

    @GetMapping
    public ResponseEntity<byte[]> getUserMonthlyStatement(
            @PathVariable Long userId,
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request
    ) {
        requireUid(request); // Ensure admin is authenticated

        byte[] pdfBytes = monthlyStatementService.generateMonthlyStatement(userId, year, month);

        String filename = String.format("statement_user%d_%d_%02d.pdf", userId, year, month);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private Long requireUid(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        if (uid instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
    }
}