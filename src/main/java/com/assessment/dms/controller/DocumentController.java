package com.assessment.dms.controller;

import com.assessment.dms.dto.PagedResponse;
import com.assessment.dms.dto.document.DocumentFile;
import com.assessment.dms.dto.document.DocumentResponse;
import com.assessment.dms.dto.document.DocumentUploadRequest;
import com.assessment.dms.dto.document.UpdateDocumentRequest;
import com.assessment.dms.entity.AccessLevel;
import com.assessment.dms.security.UserPrincipal;
import com.assessment.dms.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<DocumentResponse> uploadDocuments(@Valid @ModelAttribute DocumentUploadRequest request,
                                                  @AuthenticationPrincipal UserPrincipal currentUser) {
        return documentService.uploadDocuments(request, currentUser);
    }

    @GetMapping
    public PagedResponse<DocumentResponse> searchDocuments(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return documentService.searchDocuments(keyword, pageable, currentUser);
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return documentService.getDocument(id, currentUser);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        DocumentFile file = documentService.getDocumentFile(id, AccessLevel.VIEW, currentUser);
        return fileResponse(file, ContentDisposition.inline());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        DocumentFile file = documentService.getDocumentFile(id, AccessLevel.DOWNLOAD, currentUser);
        return fileResponse(file, ContentDisposition.attachment());
    }

    @PutMapping("/{id}")
    public DocumentResponse updateDocument(@PathVariable Long id,
                                           @Valid @RequestBody UpdateDocumentRequest request,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        return documentService.updateDocument(id, request, currentUser);
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse replaceFile(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file,
                                        @AuthenticationPrincipal UserPrincipal currentUser) {
        return documentService.replaceFile(id, file, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        documentService.deleteDocument(id, currentUser);
    }

    private ResponseEntity<Resource> fileResponse(DocumentFile file, ContentDisposition.Builder disposition) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }
}
