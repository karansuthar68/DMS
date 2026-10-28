package com.assessment.dms.dto;

import java.util.List;

public record AppSettingsResponse(
        List<String> allowedFileTypes,
        String maxFileSize,
        boolean domainRestrictionEnabled,
        List<String> allowedDomains,
        long tokenExpirationMinutes) {
}
