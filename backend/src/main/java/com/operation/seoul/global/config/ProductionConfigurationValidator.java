package com.operation.seoul.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("prod")
public class ProductionConfigurationValidator {
    private final String jwtSecret;
    private final String allowedOrigins;
    private final boolean arrivalDevModeEnabled;

    public ProductionConfigurationValidator(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.dev-mode.arrival-enabled:false}") boolean arrivalDevModeEnabled
    ) {
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.arrivalDevModeEnabled = arrivalDevModeEnabled;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32 || jwtSecret.contains("local-development")) {
            throw new IllegalStateException("Production JWT_SECRET must be a random value of at least 32 characters.");
        }
        String normalizedOrigins = allowedOrigins == null ? "" : allowedOrigins.toLowerCase(Locale.ROOT);
        if (normalizedOrigins.isBlank()
                || normalizedOrigins.contains("*")
                || normalizedOrigins.contains("localhost")
                || normalizedOrigins.contains("127.0.0.1")) {
            throw new IllegalStateException("Production CORS_ALLOWED_ORIGINS must contain only explicit deployed origins.");
        }
        if (arrivalDevModeEnabled) {
            throw new IllegalStateException("DEV_ARRIVAL_ENABLED must be false in production.");
        }
    }
}
