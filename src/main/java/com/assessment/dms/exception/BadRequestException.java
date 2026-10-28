package com.assessment.dms.exception;

/**
 * The request is well-formed but breaks a business rule (e.g. an admin deleting their own account).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
