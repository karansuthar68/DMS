package com.assessment.dms.service;

import com.assessment.dms.dto.document.DocumentPermissionResponse;
import com.assessment.dms.dto.document.GrantAccessRequest;
import com.assessment.dms.entity.Document;
import com.assessment.dms.entity.DocumentPermission;
import com.assessment.dms.entity.User;
import com.assessment.dms.exception.BadRequestException;
import com.assessment.dms.exception.ResourceNotFoundException;
import com.assessment.dms.repository.DocumentPermissionRepository;
import com.assessment.dms.repository.UserRepository;
import com.assessment.dms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPermissionService {

    private final DocumentAccessService documentAccessService;
    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DocumentPermissionResponse> getPermissions(Long documentId, UserPrincipal currentUser) {
        documentAccessService.getDocumentForChange(documentId, currentUser);
        return permissionRepository.findByDocumentIdOrderByGrantedAtAsc(documentId).stream()
                .map(DocumentPermissionResponse::from)
                .toList();
    }

    @Transactional
    public DocumentPermissionResponse grantAccess(Long documentId, GrantAccessRequest request,
                                                  UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForChange(documentId, currentUser);
        User targetUser = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + request.username()));

        if (document.isOwnedBy(targetUser.getId())) {
            throw new BadRequestException("The document owner already has full access");
        }

        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, targetUser.getId())
                .orElseGet(() -> new DocumentPermission(document, targetUser));
        permission.setAccessLevel(request.accessLevel());

        DocumentPermission saved = permissionRepository.save(permission);
        log.info("User '{}' granted {} access on document {} to '{}'",
                currentUser.getUsername(), request.accessLevel(), documentId, targetUser.getUsername());
        return DocumentPermissionResponse.from(saved);
    }

    @Transactional
    public void revokeAccess(Long documentId, Long userId, UserPrincipal currentUser) {
        documentAccessService.getDocumentForChange(documentId, currentUser);
        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + userId + " has no access entry on document " + documentId));

        permissionRepository.delete(permission);
        log.info("User '{}' revoked access on document {} from user {}", currentUser.getUsername(), documentId, userId);
    }
}
