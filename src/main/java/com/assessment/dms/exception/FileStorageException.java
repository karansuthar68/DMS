package com.assessment.dms.exception;

/**
 * Reading or writing a file on the storage failed (server side problem).
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
