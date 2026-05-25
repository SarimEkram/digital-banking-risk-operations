package com.sarim.digitalbanking.statements;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import com.sarim.digitalbanking.transfers.TransferEntity;
import com.sarim.digitalbanking.transfers.TransferStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MonthlyStatementService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    public MonthlyStatementService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            EntityManager entityManager
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public byte[] generateMonthlyStatement(Long userId, int year, int month) {
        // Validate month and year
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12");
        }
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year must be between 2000 and 2100");
        }

        // Load user
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Load all accounts for this user
        List<AccountEntity> accounts = accountRepository.findByUserIdOrderByIdAsc(userId);

        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no accounts");
        }

        // Calculate month boundaries (UTC)
        YearMonth requestedMonth = YearMonth.of(year, month);
        Instant monthStart = requestedMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthEnd = requestedMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Build statement data for each account
        List<AccountStatementData> accountStatements = new ArrayList<>();
        long totalOpeningCents = 0;
        long totalClosingCents = 0;

        for (AccountEntity account : accounts) {
            AccountStatementData accountData = buildAccountStatement(
                    account,
                    monthStart,
                    monthEnd
            );
            accountStatements.add(accountData);
            totalOpeningCents += accountData.openingBalanceCents;
            totalClosingCents += accountData.closingBalanceCents;
        }

        // Generate HTML
        String html = buildStatementHtml(
                user,
                requestedMonth,
                accountStatements,
                totalOpeningCents,
                totalClosingCents
        );

        // Render to PDF
        return renderPdf(html);
    }

    private AccountStatementData buildAccountStatement(
            AccountEntity account,
            Instant monthStart,
            Instant monthEnd
    ) {
        Long accountId = account.getId();

        // Fetch all COMPLETED transfers for this account during the month
        // Sent transfers (fromAccount = this account)
        @SuppressWarnings("unchecked")
        List<TransferEntity> sentTransfers = entityManager.createQuery(
                        "SELECT t FROM TransferEntity t " +
                                "WHERE t.fromAccount.id = :accountId " +
                                "AND t.status = :status " +
                                "AND t.createdAt >= :start " +
                                "AND t.createdAt < :end " +
                                "ORDER BY t.createdAt ASC"
                )
                .setParameter("accountId", accountId)
                .setParameter("status", TransferStatus.COMPLETED)
                .setParameter("start", monthStart)
                .setParameter("end", monthEnd)
                .getResultList();

        // Received transfers (toAccount = this account)
        @SuppressWarnings("unchecked")
        List<TransferEntity> receivedTransfers = entityManager.createQuery(
                        "SELECT t FROM TransferEntity t " +
                                "WHERE t.toAccount.id = :accountId " +
                                "AND t.status = :status " +
                                "AND t.createdAt >= :start " +
                                "AND t.createdAt < :end " +
                                "ORDER BY t.createdAt ASC"
                )
                .setParameter("accountId", accountId)
                .setParameter("status", TransferStatus.COMPLETED)
                .setParameter("start", monthStart)
                .setParameter("end", monthEnd)
                .getResultList();

        // Calculate opening balance
        // Opening = current balance - (received after month start) + (sent after month start)
        long currentBalance = account.getBalanceCents();
        long openingBalance = calculateOpeningBalance(accountId, currentBalance, monthStart);

        // Calculate closing balance
        long closingBalance = openingBalance;
        for (TransferEntity t : sentTransfers) {
            closingBalance -= t.getAmountCents();
        }
        for (TransferEntity t : receivedTransfers) {
            closingBalance += t.getAmountCents();
        }

        // Build transfer items
        List<TransferItem> transferItems = new ArrayList<>();
        for (TransferEntity t : sentTransfers) {
            transferItems.add(new TransferItem(
                    t.getCreatedAt(),
                    "Sent",
                    t.getToAccount().getUser().getEmail(),
                    -t.getAmountCents(), // negative for sent
                    t.getCurrency()
            ));
        }
        for (TransferEntity t : receivedTransfers) {
            transferItems.add(new TransferItem(
                    t.getCreatedAt(),
                    "Received",
                    t.getFromAccount().getUser().getEmail(),
                    t.getAmountCents(), // positive for received
                    t.getCurrency()
            ));
        }

        // Sort by createdAt
        transferItems.sort((a, b) -> a.createdAt.compareTo(b.createdAt));

        return new AccountStatementData(
                account.getId(),
                account.getAccountType().name(),
                account.getCurrency(),
                openingBalance,
                closingBalance,
                transferItems
        );
    }

    private long calculateOpeningBalance(Long accountId, long currentBalance, Instant monthStart) {
        // Opening balance = current - all received after monthStart + all sent after monthStart
        // (we reverse the effect of everything that happened after the month started)

        // Sum of received after monthStart
        Long receivedAfter = (Long) entityManager.createQuery(
                        "SELECT COALESCE(SUM(t.amountCents), 0) " +
                                "FROM TransferEntity t " +
                                "WHERE t.toAccount.id = :accountId " +
                                "AND t.status = :status " +
                                "AND t.createdAt >= :start"
                )
                .setParameter("accountId", accountId)
                .setParameter("status", TransferStatus.COMPLETED)
                .setParameter("start", monthStart)
                .getSingleResult();

        // Sum of sent after monthStart
        Long sentAfter = (Long) entityManager.createQuery(
                        "SELECT COALESCE(SUM(t.amountCents), 0) " +
                                "FROM TransferEntity t " +
                                "WHERE t.fromAccount.id = :accountId " +
                                "AND t.status = :status " +
                                "AND t.createdAt >= :start"
                )
                .setParameter("accountId", accountId)
                .setParameter("status", TransferStatus.COMPLETED)
                .setParameter("start", monthStart)
                .getSingleResult();

        return currentBalance - receivedAfter + sentAfter;
    }

    private String buildStatementHtml(
            UserEntity user,
            YearMonth statementMonth,
            List<AccountStatementData> accounts,
            long totalOpeningCents,
            long totalClosingCents
    ) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset=\"UTF-8\"/>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; color: #333; }");
        html.append("h1 { color: #2563eb; margin-bottom: 8px; }");
        html.append("h2 { color: #1e40af; margin-top: 24px; margin-bottom: 12px; font-size: 18px; }");
        html.append(".header { margin-bottom: 32px; }");
        html.append(".user-info { margin-bottom: 16px; font-size: 14px; }");
        html.append(".account-section { margin-bottom: 32px; padding: 16px; border: 1px solid #e2e8f0; border-radius: 8px; }");
        html.append(".balance-row { display: flex; justify-content: space-between; margin: 8px 0; font-weight: bold; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 16px 0; }");
        html.append("th { background-color: #f1f5f9; padding: 8px; text-align: left; font-size: 12px; border-bottom: 2px solid #cbd5e1; }");
        html.append("td { padding: 8px; font-size: 12px; border-bottom: 1px solid #e2e8f0; }");
        html.append(".amount-sent { color: #dc2626; }");
        html.append(".amount-received { color: #16a34a; }");
        html.append(".summary { margin-top: 32px; padding: 16px; background-color: #f8fafc; border-radius: 8px; }");
        html.append(".no-transfers { color: #64748b; font-style: italic; margin: 16px 0; }");
        html.append("</style>");
        html.append("</head><body>");

        // Header
        html.append("<div class=\"header\">");
        html.append("<h1>Monthly Statement</h1>");
        html.append("<div class=\"user-info\">");
        html.append("<div><strong>Account Holder:</strong> ").append(escapeHtml(user.getEmail())).append("</div>");
        html.append("<div><strong>User ID:</strong> ").append(user.getId()).append("</div>");
        html.append("<div><strong>Statement Period:</strong> ").append(formatMonth(statementMonth)).append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Per-account sections
        for (AccountStatementData account : accounts) {
            html.append("<div class=\"account-section\">");
            html.append("<h2>Account #").append(account.accountId)
                    .append(" - ").append(account.accountType)
                    .append(" (").append(account.currency).append(")</h2>");

            html.append("<div class=\"balance-row\">");
            html.append("<span>Opening Balance:</span>");
            html.append("<span>").append(formatMoney(account.openingBalanceCents, account.currency)).append("</span>");
            html.append("</div>");

            if (account.transfers.isEmpty()) {
                html.append("<div class=\"no-transfers\">No transfers during this period</div>");
            } else {
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Date</th>");
                html.append("<th>Type</th>");
                html.append("<th>Counterparty</th>");
                html.append("<th style=\"text-align: right;\">Amount</th>");
                html.append("</tr></thead>");
                html.append("<tbody>");

                for (TransferItem item : account.transfers) {
                    html.append("<tr>");
                    html.append("<td>").append(formatDateTime(item.createdAt)).append("</td>");
                    html.append("<td>").append(item.type).append("</td>");
                    html.append("<td>").append(escapeHtml(item.counterparty)).append("</td>");

                    String amountClass = item.amountCents < 0 ? "amount-sent" : "amount-received";
                    html.append("<td style=\"text-align: right;\" class=\"").append(amountClass).append("\">");
                    html.append(formatMoney(item.amountCents, item.currency));
                    html.append("</td>");

                    html.append("</tr>");
                }

                html.append("</tbody></table>");
            }

            html.append("<div class=\"balance-row\">");
            html.append("<span>Closing Balance:</span>");
            html.append("<span>").append(formatMoney(account.closingBalanceCents, account.currency)).append("</span>");
            html.append("</div>");

            html.append("</div>");
        }

        // Summary
        html.append("<div class=\"summary\">");
        html.append("<h2>Summary</h2>");
        html.append("<div class=\"balance-row\">");
        html.append("<span>Total Opening Balance:</span>");
        html.append("<span>").append(formatMoney(totalOpeningCents, "CAD")).append("</span>");
        html.append("</div>");
        html.append("<div class=\"balance-row\">");
        html.append("<span>Total Closing Balance:</span>");
        html.append("<span>").append(formatMoney(totalClosingCents, "CAD")).append("</span>");
        html.append("</div>");
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    private byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate PDF: " + e.getMessage()
            );
        }
    }

    private String formatMoney(long amountCents, String currency) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100));
        return String.format("%s %.2f", currency, amount);
    }

    private String formatMonth(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
    }

    private String formatDateTime(Instant instant) {
        return DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Inner classes for data structure
    private static class AccountStatementData {
        final Long accountId;
        final String accountType;
        final String currency;
        final long openingBalanceCents;
        final long closingBalanceCents;
        final List<TransferItem> transfers;

        AccountStatementData(
                Long accountId,
                String accountType,
                String currency,
                long openingBalanceCents,
                long closingBalanceCents,
                List<TransferItem> transfers
        ) {
            this.accountId = accountId;
            this.accountType = accountType;
            this.currency = currency;
            this.openingBalanceCents = openingBalanceCents;
            this.closingBalanceCents = closingBalanceCents;
            this.transfers = transfers;
        }
    }

    private static class TransferItem {
        final Instant createdAt;
        final String type; // "Sent" or "Received"
        final String counterparty;
        final long amountCents; // negative for sent, positive for received
        final String currency;

        TransferItem(Instant createdAt, String type, String counterparty, long amountCents, String currency) {
            this.createdAt = createdAt;
            this.type = type;
            this.counterparty = counterparty;
            this.amountCents = amountCents;
            this.currency = currency;
        }
    }
}