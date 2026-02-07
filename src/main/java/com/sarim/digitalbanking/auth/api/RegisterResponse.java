package com.sarim.digitalbanking.auth.api;

public record RegisterResponse(
        Long userId,
        String email,
        Long accountId
) {}
