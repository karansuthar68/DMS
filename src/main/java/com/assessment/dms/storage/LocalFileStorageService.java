package com.assessment.dms.storage;

import com.assessment.dms.config.AppProperties;
import com.assessment.dms.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootLocation;

    public LocalFileStorageService(AppProperties appProperties) {
        this.rootLocation = Paths.get(appProperties.storage().uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create upload directory: " + rootLocation, ex);
        }
        log.info("Documents are stored in {}", rootLocation);
    }

    @Override
    public String store(MultipartFile file, String extension) {
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path target = resolve(storedFileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException("Could not store the file. Please try again", ex);
        }
        return storedFileName;
    }

    @Override
    public Resource load(String storedFileName) {
        Path file = resolve(storedFileName);
        if (!Files.isReadable(file)) {
            log.error("Stored file is missing on disk: {}", file);
            throw new FileStorageException("The document file could not be read from storage");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(resolve(storedFileName));
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", storedFileName, ex);
        }
    }

    private Path resolve(String storedFileName) {
        Path file = rootLocation.resolve(storedFileName).normalize();
        if (!file.startsWith(rootLocation)) {
            throw new FileStorageException("Invalid file location");
        }
        return file;
    }
}
