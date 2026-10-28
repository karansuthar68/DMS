package com.assessment.dms.controller;

import com.assessment.dms.dto.document.DocumentPermissionResponse;
import com.assessment.dms.dto.document.GrantAccessRequest;
import com.assessment.dms.security.UserPrincipal;
import com.assessment.dms.service.DocumentPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
@RequiredArgsConstructor
public class DocumentPermissionController {

    private final DocumentPermissionService permissionService;

    @GetMapping
    public List<DocumentPermissionResponse> getPermissions(@PathVariable Long documentId,
                                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        return permissionService.getPermissions(documentId, currentUser);
    }

    @PostMapping("/access")
    public DocumentPermissionResponse grantAccess(@PathVariable Long documentId,
                                                  @Valid @RequestBody GrantAccessRequest request,
                                                  @AuthenticationPrincipal UserPrincipal currentUser) {
        return permissionService.grantAccess(documentId, request, currentUser);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAccess(@PathVariable Long documentId,
                             @PathVariable Long userId,
                             @AuthenticationPrincipal UserPrincipal currentUser) {
        permissionService.revokeAccess(documentId, userId, currentUser);
    }
}
