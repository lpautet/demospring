package net.pautet.softs.demospring.operations.internal;

import net.pautet.softs.demospring.operations.LogMessage;
import net.pautet.softs.demospring.operations.internal.persistence.MessageRepository;
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
        LogMessage startup = new LogMessage(
                "Application started successfully.",
                "INFO",
                "server"
        );
        messageRepository.save(startup);
    }
}
