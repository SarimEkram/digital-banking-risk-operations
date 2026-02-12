package com.sarim.digitalbanking.accounts.api;

import com.sarim.digitalbanking.accounts.AccountType;

public record AccountResponse(
        Long id,
        AccountType accountType,
        String currency,
        long balanceCents,
        String status
) {}
