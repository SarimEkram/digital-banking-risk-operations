package com.sarim.digitalbanking.statements;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/statements")
public class StatementsController {

    private final MonthlyStatementService monthlyStatementService;

    public StatementsController(MonthlyStatementService monthlyStatementService) {
        this.monthlyStatementService = monthlyStatementService;
    }

    @GetMapping
    public ResponseEntity<byte[]> getMonthlyStatement(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request
    ) {
        Long userId = requireUid(request);

        byte[] pdfBytes = monthlyStatementService.generateMonthlyStatement(userId, year, month);

        String filename = String.format("statement_%d_%02d.pdf", year, month);

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