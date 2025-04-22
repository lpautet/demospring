package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Random;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

//    private final Random random = new Random();

    public List<Message> getAllMessages() {
        return messageRepository.findAll();
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void generateRandomMessage() {
//        String severity = random.nextDouble() < 0.2 ? "warning" : "info";
//        Message message = new Message("Server message " + System.currentTimeMillis(), severity, "server");
//        messageRepository.save(message);

        // Keep only the last 50 messages
        List<Message> messages = messageRepository.findAll();
        if (messages.size() > 50) {
            messageRepository.deleteAll(messages.subList(0, messages.size() - 50));
        }
    }
}