package net.pautet.softs.demospring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack")
public record SlackConfig(
    String botToken,
    String channelId,
    String channelName,
    Boolean enabled,
    Boolean autoCreateChannel
) {}
