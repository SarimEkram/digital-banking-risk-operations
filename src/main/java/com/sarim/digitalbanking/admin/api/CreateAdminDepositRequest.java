package com.sarim.digitalbanking.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateAdminDepositRequest(
        @NotNull Long toAccountId,
        @Min(1) long amountCents
) {}