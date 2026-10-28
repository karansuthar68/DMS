package com.assessment.dms.dto.document;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Fields of the multipart/form-data upload request. One or more files can be sent under the "files" key.
 * Description and category apply to every file. The title is used only for a single-file upload;
 * otherwise (or when omitted) each document is titled with its file name.
 */
public record DocumentUploadRequest(
        @NotEmpty(message = "at least one file is required")
        @Size(max = 10, message = "a maximum of 10 files can be uploaded at once")
        List<MultipartFile> files,

        @Size(max = 150)
        String title,

        @Size(max = 1000)
        String description,

        @Size(max = 100)
        String category) {
}
