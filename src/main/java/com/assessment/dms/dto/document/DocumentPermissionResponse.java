package com.assessment.dms.dto.document;

import com.assessment.dms.entity.AccessLevel;
import com.assessment.dms.entity.DocumentPermission;

import java.time.LocalDateTime;

public record DocumentPermissionResponse(
        Long userId,
        String username,
        AccessLevel accessLevel,
        LocalDateTime grantedAt) {

    public static DocumentPermissionResponse from(DocumentPermission permission) {
        return new DocumentPermissionResponse(
                permission.getUser().getId(),
                permission.getUser().getUsername(),
                permission.getAccessLevel(),
                permission.getGrantedAt());
    }
}
