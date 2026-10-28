package com.assessment.dms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;


@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Storage storage, DomainRestriction domainRestriction) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Storage(String uploadDir) {
    }

    public record DomainRestriction(boolean enabled, List<String> allowedDomains) {
    }
}
