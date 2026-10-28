package com.assessment.dms.dto.user;

import com.assessment.dms.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 100)
        String email,

        @Size(max = 100)
        String fullName,

        @NotNull
        Role role,

        @NotNull
        Boolean enabled) {
}
