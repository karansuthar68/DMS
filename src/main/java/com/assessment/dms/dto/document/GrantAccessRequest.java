package com.assessment.dms.dto.document;

import com.assessment.dms.entity.AccessLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GrantAccessRequest(
        @NotBlank String username,
        @NotNull AccessLevel accessLevel) {
}
