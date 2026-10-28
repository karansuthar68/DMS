package com.assessment.dms.exception;

/**
 * The uploaded file was rejected (empty, missing name or unsupported type).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
