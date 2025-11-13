package net.pautet.softs.demospring.service;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.SlackConfig;
import net.pautet.softs.demospring.entity.LogMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class SlackLogMessageService {

    private final SlackConfig slackConfig;
    private final Slack slackClient;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    
    private String resolvedChannelId;

    public SlackLogMessageService(SlackConfig slackConfig) {
        this.slackConfig = slackConfig;
        this.slackClient = Slack.getInstance();
    }

    @PostConstruct
    public void initialize() {
        if (!isBasicConfigValid()) {
            log.info("Slack integration is not enabled or not configured");
            return;
        }

        try {
            // If channelId is provided, use it directly
            if (slackConfig.channelId() != null && !slackConfig.channelId().isBlank()) {
                this.resolvedChannelId = slackConfig.channelId();
                log.info("Using configured Slack channel ID: {}", resolvedChannelId);
            }
            // Otherwise, try to resolve from channelName
            else if (slackConfig.channelName() != null && !slackConfig.channelName().isBlank()) {
                String channelName = normalizeChannelName(slackConfig.channelName());
                this.resolvedChannelId = findChannelByName(channelName);
                
                if (this.resolvedChannelId == null && isAutoCreateEnabled()) {
                    log.info("Channel '{}' not found, creating it...", channelName);
                    this.resolvedChannelId = createChannel(channelName);
                    if (this.resolvedChannelId != null) {
                        log.info("Successfully created Slack channel '{}' with ID: {}", channelName, resolvedChannelId);
                    }
                } else if (this.resolvedChannelId != null) {
                    log.info("Found Slack channel '{}' with ID: {}", channelName, resolvedChannelId);
                }
            }

            if (this.resolvedChannelId == null) {
                log.warn("Slack integration configured but no valid channel could be resolved. Please provide slack.channel-id or slack.channel-name");
            } else {
                log.info("Slack bot integration initialized successfully. Logs will be sent to channel: {}", resolvedChannelId);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Slack integration: {}", e.getMessage(), e);
        }
    }

    private String normalizeChannelName(String name) {
        // Remove # prefix if present
        String normalized = name.startsWith("#") ? name.substring(1) : name;
        // Convert to lowercase and replace spaces with hyphens
        return normalized.toLowerCase().replaceAll("[\\s_]+", "-");
    }

    private String findChannelByName(String channelName) {
        try {
            ConversationsListResponse response = slackClient.methods(slackConfig.botToken())
                    .conversationsList(req -> req
                            .excludeArchived(true)
                            .types(List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                            .limit(1000)
                    );

            if (response.isOk()) {
                return response.getChannels().stream()
                        .filter(channel -> channel.getName().equalsIgnoreCase(channelName))
                        .findFirst()
                        .map(Conversation::getId)
                        .orElse(null);
            } else {
                log.error("Failed to list Slack channels: {}", response.getError());
                return null;
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error finding Slack channel by name '{}': {}", channelName, e.getMessage(), e);
            return null;
        }
    }

    private String createChannel(String channelName) {
        try {
            ConversationsCreateResponse response = slackClient.methods(slackConfig.botToken())
                    .conversationsCreate(req -> req
                            .name(channelName)
                            .isPrivate(false)
                    );

            if (response.isOk()) {
                return response.getChannel().getId();
            } else {
                log.error("Failed to create Slack channel '{}': {}", channelName, response.getError());
                return null;
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error creating Slack channel '{}': {}", channelName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Send a log message to Slack channel
     */
    public void sendLogMessage(LogMessage logMessage) {
        if (!isEnabled()) {
            log.debug("Slack integration is disabled, skipping message");
            return;
        }

        try {
            String emoji = getEmojiForSeverity(logMessage.getSeverity());
            String formattedTime = FORMATTER.format(logMessage.getTimestamp());
            
            String messageText = String.format("%s *[%s]* %s%n_Source: %s | Time: %s_",
                    emoji,
                    logMessage.getSeverity(),
                    logMessage.getMessage(),
                    logMessage.getSource(),
                    formattedTime
            );

            ChatPostMessageResponse response = slackClient.methods(slackConfig.botToken())
                    .chatPostMessage(req -> req
                            .channel(resolvedChannelId)
                            .text(messageText)
                            .mrkdwn(true)
                    );

            if (!response.isOk()) {
                log.error("Failed to send message to Slack: {}", response.getError());
            } else {
                log.debug("Successfully sent log message to Slack channel: {}", resolvedChannelId);
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error sending message to Slack: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a formatted block message to Slack with multiple log entries
     */
    public void sendBatchLogMessages(List<LogMessage> logMessages) {
        if (!isEnabled() || logMessages.isEmpty()) {
            return;
        }

        try {
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("*📋 Log Messages Batch*\n\n");

            for (LogMessage logMessage : logMessages) {
                String emoji = getEmojiForSeverity(logMessage.getSeverity());
                String formattedTime = FORMATTER.format(logMessage.getTimestamp());
                messageBuilder.append(String.format("%s *[%s]* %s%n_Source: %s | Time: %s_%n%n",
                        emoji,
                        logMessage.getSeverity(),
                        logMessage.getMessage(),
                        logMessage.getSource(),
                        formattedTime
                ));
            }

            List<LayoutBlock> blocks = List.of(
                    SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(messageBuilder.toString())
                                    .build())
                            .build()
            );

            ChatPostMessageResponse response = slackClient.methods(slackConfig.botToken())
                    .chatPostMessage(req -> req
                            .channel(resolvedChannelId)
                            .blocks(blocks)
                    );

            if (!response.isOk()) {
                log.error("Failed to send batch messages to Slack: {}", response.getError());
            } else {
                log.info("Successfully sent {} log messages to Slack", logMessages.size());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error sending batch messages to Slack: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a simple text message to Slack
     */
    public void sendSimpleMessage(String message) {
        if (!isEnabled()) {
            return;
        }

        try {
            ChatPostMessageResponse response = slackClient.methods(slackConfig.botToken())
                    .chatPostMessage(req -> req
                            .channel(resolvedChannelId)
                            .text(message)
                    );

            if (!response.isOk()) {
                log.error("Failed to send simple message to Slack: {}", response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error sending simple message to Slack: {}", e.getMessage(), e);
        }
    }

    private String getEmojiForSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "ERROR" -> "🔴";
            case "WARN", "WARNING" -> "⚠️";
            case "INFO" -> "ℹ️";
            case "DEBUG" -> "🐛";
            case "SUCCESS" -> "✅";
            default -> "📝";
        };
    }

    private boolean isBasicConfigValid() {
        return slackConfig.enabled() != null && slackConfig.enabled()
                && slackConfig.botToken() != null && !slackConfig.botToken().isBlank();
    }

    private boolean isAutoCreateEnabled() {
        return slackConfig.autoCreateChannel() != null && slackConfig.autoCreateChannel();
    }

    private boolean isEnabled() {
        return isBasicConfigValid() && resolvedChannelId != null && !resolvedChannelId.isBlank();
    }

    public boolean isConfigured() {
        return isEnabled();
    }
}
