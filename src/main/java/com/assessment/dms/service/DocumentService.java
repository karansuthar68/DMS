package com.assessment.dms.service;

import com.assessment.dms.dto.PagedResponse;
import com.assessment.dms.dto.document.DocumentFile;
import com.assessment.dms.dto.document.DocumentResponse;
import com.assessment.dms.dto.document.DocumentUploadRequest;
import com.assessment.dms.dto.document.UpdateDocumentRequest;
import com.assessment.dms.entity.AccessLevel;
import com.assessment.dms.entity.Document;
import com.assessment.dms.entity.User;
import com.assessment.dms.exception.BadRequestException;
import com.assessment.dms.exception.ResourceNotFoundException;
import com.assessment.dms.repository.DocumentRepository;
import com.assessment.dms.repository.UserRepository;
import com.assessment.dms.security.UserPrincipal;
import com.assessment.dms.storage.FileStorageService;
import com.assessment.dms.storage.FileValidator;
import com.assessment.dms.storage.SupportedFileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int MAX_TITLE_LENGTH = 150;

    private static final Set<String> SORTABLE_FIELDS =
            new LinkedHashSet<>(List.of("title", "category", "originalFileName", "fileSize", "createdAt", "updatedAt"));

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentAccessService documentAccessService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;

    /**
     * Uploads one or more files as separate documents. All-or-nothing: every file is validated first,
     * and if anything fails while saving, the stored files are removed and the transaction rolls back.
     */
    @Transactional
    public List<DocumentResponse> uploadDocuments(DocumentUploadRequest request, UserPrincipal currentUser) {
        List<MultipartFile> files = request.files();
        List<SupportedFileType> fileTypes = new ArrayList<>();
        for (MultipartFile file : files) {
            fileTypes.add(fileValidator.validate(file));
        }

        User owner = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getId()));
        boolean useGivenTitle = files.size() == 1 && StringUtils.hasText(request.title());

        List<Document> documents = new ArrayList<>();
        List<String> storedFileNames = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                SupportedFileType fileType = fileTypes.get(i);
                String originalFileName = fileValidator.extractFileName(file);

                Document document = new Document();
                document.setTitle(useGivenTitle ? request.title().trim() : defaultTitle(originalFileName));
                document.setDescription(request.description());
                document.setCategory(request.category());
                document.setOwner(owner);

                String storedFileName = fileStorageService.store(file, fileType.getExtension());
                storedFileNames.add(storedFileName);
                applyFileDetails(document, file, originalFileName, fileType, storedFileName);
                documents.add(document);
            }
            List<Document> saved = documentRepository.saveAllAndFlush(documents);
            log.info("User '{}' uploaded {} document(s)", owner.getUsername(), saved.size());
            return saved.stream().map(DocumentResponse::from).toList();
        } catch (RuntimeException ex) {
            storedFileNames.forEach(fileStorageService::delete);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<DocumentResponse> searchDocuments(String keyword, Pageable pageable, UserPrincipal currentUser) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed sort fields: " + String.join(", ", SORTABLE_FIELDS));
            }
        }
        return PagedResponse.from(documentRepository
                .searchDocuments(currentUser.getId(), currentUser.isAdmin(), keyword.trim(), pageable)
                .map(DocumentResponse::from));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long documentId, UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForRead(documentId, currentUser, AccessLevel.VIEW);
        return DocumentResponse.from(document);
    }

  
    @Transactional(readOnly = true)
    public DocumentFile getDocumentFile(Long documentId, AccessLevel requiredLevel, UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForRead(documentId, currentUser, requiredLevel);
        Resource resource = fileStorageService.load(document.getStoredFileName());
        return new DocumentFile(resource, document.getOriginalFileName(), document.getContentType(),
                document.getFileSize());
    }

    @Transactional
    public DocumentResponse updateDocument(Long documentId, UpdateDocumentRequest request, UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForChange(documentId, currentUser);

        document.setTitle(request.title().trim());
        document.setDescription(request.description());
        document.setCategory(request.category());
        if (request.metadata() != null) {
            // Modify the managed collection instead of replacing it, so Hibernate tracks the change
            document.getMetadata().clear();
            document.getMetadata().putAll(request.metadata());
        }

        Document saved = documentRepository.saveAndFlush(document);
        log.info("User '{}' updated document {}", currentUser.getUsername(), documentId);
        return DocumentResponse.from(saved);
    }

    @Transactional
    public DocumentResponse replaceFile(Long documentId, MultipartFile file, UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForChange(documentId, currentUser);
        SupportedFileType fileType = fileValidator.validate(file);
        String originalFileName = fileValidator.extractFileName(file);

        String oldStoredFileName = document.getStoredFileName();
        String newStoredFileName = fileStorageService.store(file, fileType.getExtension());
        applyFileDetails(document, file, originalFileName, fileType, newStoredFileName);
        try {
            documentRepository.saveAndFlush(document);
        } catch (RuntimeException ex) {
            fileStorageService.delete(newStoredFileName);
            throw ex;
        }

        // Remove the previous version only after the database points to the new file
        fileStorageService.delete(oldStoredFileName);
        log.info("User '{}' replaced the file of document {}", currentUser.getUsername(), documentId);
        return DocumentResponse.from(document);
    }

    @Transactional
    public void deleteDocument(Long documentId, UserPrincipal currentUser) {
        Document document = documentAccessService.getDocumentForChange(documentId, currentUser);

        documentRepository.delete(document);
        documentRepository.flush();
        fileStorageService.delete(document.getStoredFileName());
        log.info("User '{}' deleted document {}", currentUser.getUsername(), documentId);
    }

    private void applyFileDetails(Document document, MultipartFile file, String originalFileName,
                                  SupportedFileType fileType, String storedFileName) {
        document.setOriginalFileName(originalFileName);
        document.setStoredFileName(storedFileName);
        document.setFileExtension(fileType.getExtension());
        document.setContentType(fileType.getMimeType());
        document.setFileSize(file.getSize());
    }

    private String defaultTitle(String fileName) {
        return fileName.length() > MAX_TITLE_LENGTH ? fileName.substring(0, MAX_TITLE_LENGTH) : fileName;
    }
}
