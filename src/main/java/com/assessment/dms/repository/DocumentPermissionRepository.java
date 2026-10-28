package com.assessment.dms.repository;

import com.assessment.dms.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    Optional<DocumentPermission> findByDocumentIdAndUserId(Long documentId, Long userId);

    List<DocumentPermission> findByDocumentIdOrderByGrantedAtAsc(Long documentId);
}
