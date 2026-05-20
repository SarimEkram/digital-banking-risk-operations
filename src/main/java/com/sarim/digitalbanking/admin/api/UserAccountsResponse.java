package com.sarim.digitalbanking.admin.api;

import java.util.List;

public record UserAccountsResponse(
        Long userId,
        String email,
        String role,
        List<AccountDetailsResponse> accounts
) {}