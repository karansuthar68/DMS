package com.assessment.dms.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String store(MultipartFile file, String extension);

    Resource load(String storedFileName);

    void delete(String storedFileName);
}
