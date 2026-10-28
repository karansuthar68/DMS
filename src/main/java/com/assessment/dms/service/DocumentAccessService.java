package com.assessment.dms.service;

import com.assessment.dms.entity.AccessLevel;
import com.assessment.dms.entity.Document;
import com.assessment.dms.exception.ResourceNotFoundException;
import com.assessment.dms.repository.DocumentPermissionRepository;
import com.assessment.dms.repository.DocumentRepository;
import com.assessment.dms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;


    public Document getDocumentForRead(Long documentId, UserPrincipal user, AccessLevel requiredLevel) {
        Document document = findDocument(documentId);
        if (user.isAdmin() || document.isOwnedBy(user.getId())) {
            return document;
        }

        boolean permitted = permissionRepository.findByDocumentIdAndUserId(documentId, user.getId())
                .map(permission -> permission.getAccessLevel().allows(requiredLevel))
                .orElse(false);
        if (!permitted) {
            throw new AccessDeniedException(
                    "You do not have " + requiredLevel + " permission for document " + documentId);
        }
        return document;
    }

    public Document getDocumentForChange(Long documentId, UserPrincipal user) {
        Document document = findDocument(documentId);
        if (!user.isAdmin() && !document.isOwnedBy(user.getId())) {
            throw new AccessDeniedException("Only the document owner or an administrator can modify this document");
        }
        return document;
    }

    private Document findDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }
}
