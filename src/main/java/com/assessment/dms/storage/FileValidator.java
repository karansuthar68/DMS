package com.assessment.dms.storage;

import com.assessment.dms.exception.InvalidFileException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


@Component
public class FileValidator {

    public SupportedFileType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("The uploaded file is empty. Please select a valid file");
        }

        String extension = StringUtils.getFilenameExtension(extractFileName(file));
        return SupportedFileType.fromExtension(extension)
                .orElseThrow(() -> new InvalidFileException(
                        "File type '%s' is not supported. Allowed types: %s".formatted(
                                extension == null ? "(none)" : "." + extension,
                                String.join(", ", SupportedFileType.allowedExtensions()))));
    }

    public String extractFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new InvalidFileException("The uploaded file has no name");
        }
        return StringUtils.getFilename(StringUtils.cleanPath(originalFileName));
    }
}
