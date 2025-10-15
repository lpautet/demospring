package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.entity.LogMessage;
import net.pautet.softs.demospring.repository.MessageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void info(String message) {
        messageRepository.save(new LogMessage(message,
                "INFO",
                "server"));
    }

    public void error(String message) {
        messageRepository.save(new LogMessage(message,
                "ERROR",
                "server"));
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