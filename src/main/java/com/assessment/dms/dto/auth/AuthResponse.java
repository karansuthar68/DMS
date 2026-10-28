package com.assessment.dms.dto.auth;

import com.assessment.dms.entity.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String username,
        Role role) {
}
