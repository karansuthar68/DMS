package com.assessment.dms.dto.document;

import com.assessment.dms.entity.Document;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

public record DocumentResponse(
        Long id,
        String title,
        String description,
        String category,
        String fileName,
        String fileExtension,
        String contentType,
        long fileSize,
        Long ownerId,
        String ownerUsername,
        Map<String, String> metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.getCategory(),
                document.getOriginalFileName(),
                document.getFileExtension(),
                document.getContentType(),
                document.getFileSize(),
                document.getOwner().getId(),
                document.getOwner().getUsername(),
                new TreeMap<>(document.getMetadata()),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
