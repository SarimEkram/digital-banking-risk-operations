package com.sarim.digitalbanking.payees.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePayeeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 100) String label
) {}
