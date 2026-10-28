package com.assessment.dms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "must be 3-50 characters and contain only letters, digits, '.', '_' or '-'")
        String username,

        @NotBlank @Email @Size(max = 100)
        String email,

        @NotBlank @Size(min = 8, max = 64)
        String password,

        @Size(max = 100)
        String fullName) {
}
