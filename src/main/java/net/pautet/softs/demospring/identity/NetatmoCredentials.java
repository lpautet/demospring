package net.pautet.softs.demospring.identity;

public record NetatmoCredentials(
        String accessToken,
        String refreshToken,
        Long expiresAt,
        Long refreshedAt
) {}
