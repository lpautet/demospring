package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.LogMessage;
import net.pautet.softs.demospring.repository.MessageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Slf4j
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final SlackService slackService;

    public MessageService(MessageRepository messageRepository, SlackService slackService) {
        this.messageRepository = messageRepository;
        this.slackService = slackService;
    }

    public void info(String message) {
        LogMessage logMessage = new LogMessage(message, "INFO", "server");
        messageRepository.save(logMessage);
        publishToSlack(logMessage);
    }

    public void error(String message) {
        LogMessage logMessage = new LogMessage(message, "ERROR", "server");
        messageRepository.save(logMessage);
        publishToSlack(logMessage);
    }

    private void publishToSlack(LogMessage logMessage) {
        try {
            if (slackService.isConfigured()) {
                slackService.sendLogMessage(logMessage);
            }
        } catch (Exception e) {
            log.error("Failed to publish message to Slack: {}", e.getMessage(), e);
        }
    }

    public List<LogMessage> getAllMessages() {
        return messageRepository.findAll();
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupMessages() {
        // Keep only the last 50 messages
        Sort sort = Sort.by(Sort.Direction.ASC, "timestamp");
        List<LogMessage> logMessages = messageRepository.findAll(sort);
        if (logMessages.size() > 50) {
            messageRepository.deleteAll(logMessages.subList(0, logMessages.size() - 50));
        }
    }
}