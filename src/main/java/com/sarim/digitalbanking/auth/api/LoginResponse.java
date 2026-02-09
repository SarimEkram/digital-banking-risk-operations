package com.sarim.digitalbanking.auth.api;

public record LoginResponse(
        Long userId,
        String email,
        String role,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {}
