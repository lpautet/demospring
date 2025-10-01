package net.pautet.softs.demospring.bootstrap;

import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.repository.MessageRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupMessages implements ApplicationRunner {

    private final MessageRepository messageRepository;

    public StartupMessages(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Add a startup message once the application context is ready
        Message startup = new Message(
                "Application started successfully.",
                "INFO",
                "server"
        );
        messageRepository.save(startup);
    }
}
