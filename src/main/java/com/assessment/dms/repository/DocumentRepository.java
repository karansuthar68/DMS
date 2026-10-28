package com.assessment.dms.repository;

import com.assessment.dms.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
            SELECT d FROM Document d
            WHERE (:isAdmin = true
                   OR d.owner.id = :userId
                   OR EXISTS (SELECT p FROM DocumentPermission p
                              WHERE p.document = d AND p.user.id = :userId))
              AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(d.category, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Document> searchDocuments(@Param("userId") Long userId,
                                   @Param("isAdmin") boolean isAdmin,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    boolean existsByOwnerId(Long ownerId);
}
