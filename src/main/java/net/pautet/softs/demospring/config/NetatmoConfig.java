package net.pautet.softs.demospring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("netatmo")
public record NetatmoConfig(
        String clientId,
        String clientSecret
) {
}
