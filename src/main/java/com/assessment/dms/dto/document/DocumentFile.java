package com.assessment.dms.dto.document;

import org.springframework.core.io.Resource;

/**
 * File content plus the details the controller needs to build a download/preview response.
 */
public record DocumentFile(Resource resource, String fileName, String contentType, long size) {
}
