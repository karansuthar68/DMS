package com.assessment.dms.security;

import com.assessment.dms.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;


@Slf4j
public class DomainRestrictionFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final List<String> allowedDomains;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public DomainRestrictionFilter(AppProperties.DomainRestriction settings,
                                   SecurityErrorResponseWriter errorResponseWriter) {
        this.enabled = settings.enabled();
        this.allowedDomains = settings.allowedDomains().stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .toList();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String host = request.getServerName();
        if (!isApprovedDomain(host)) {
            reject(request, response, "Requests to host '" + host + "' are not allowed");
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !isApprovedDomain(extractHost(origin))) {
            reject(request, response, "Requests from origin '" + origin + "' are not allowed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    boolean isApprovedDomain(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedDomains) {
            boolean matches = allowed.startsWith("*.")
                    ? normalizedHost.endsWith(allowed.substring(1))
                    : normalizedHost.equals(allowed);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private String extractHost(String origin) {
        try {
            return URI.create(origin).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        log.warn("Domain restriction blocked {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, message);
    }
}
