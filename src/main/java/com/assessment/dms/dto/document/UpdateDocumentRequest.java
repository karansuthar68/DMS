package com.assessment.dms.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateDocumentRequest(
        @NotBlank @Size(max = 150)
        String title,

        @Size(max = 1000)
        String description,

        @Size(max = 100)
        String category,

        @Size(max = 20, message = "can contain at most 20 entries")
        Map<@NotBlank @Size(max = 100) String, @NotNull @Size(max = 500) String> metadata) {
}
