package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.entity.Message;
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

    public List<Message> getAllMessages() {
        return messageRepository.findAll();
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupMessages() {
        // Keep only the last 50 messages
        Sort sort = Sort.by(Sort.Direction.ASC, "timestamp");
        List<Message> messages = messageRepository.findAll(sort);
        if (messages.size() > 50) {
            messageRepository.deleteAll(messages.subList(0, messages.size() - 50));
        }
    }
}