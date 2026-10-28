package com.assessment.dms.controller;

import com.assessment.dms.config.AppProperties;
import com.assessment.dms.dto.AppSettingsResponse;
import com.assessment.dms.storage.SupportedFileType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AppProperties appProperties;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;

    @GetMapping
    public AppSettingsResponse getSettings() {
        return new AppSettingsResponse(
                SupportedFileType.allowedExtensions(),
                maxFileSize.toMegabytes() + "MB",
                appProperties.domainRestriction().enabled(),
                appProperties.domainRestriction().allowedDomains(),
                appProperties.jwt().expirationMinutes());
    }
}
