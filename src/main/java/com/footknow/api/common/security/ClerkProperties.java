package com.footknow.api.common.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Set;

/**
 * Configuration properties for Clerk security settings.
 */
@Validated
@ConfigurationProperties(prefix = "security.clerk")
public record ClerkProperties(
        @NotBlank
        String issuer,

        @NotEmpty
        Set<@NotBlank String> authorizedParties,

        String audience
) {

    @AssertTrue(message =
            "Clerk issuer must be an HTTPS URL without credentials, query, or fragment")
    public boolean isIssuerValid() {
        if (issuer == null || issuer.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(issuer);

            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String jwkSetUri() {
        String base = issuer.endsWith("/")
                ? issuer.substring(0, issuer.length() - 1)
                : issuer;

        return base + "/.well-known/jwks.json";
    }
}