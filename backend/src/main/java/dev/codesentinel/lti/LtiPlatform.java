package dev.codesentinel.lti;

import java.time.Instant;

public record LtiPlatform(
        Long id,
        String name,
        String issuer,
        String clientId,
        String deploymentId,
        String authUrl,
        String tokenUrl,
        String jwksUrl,
        Instant createdAt
) {}
