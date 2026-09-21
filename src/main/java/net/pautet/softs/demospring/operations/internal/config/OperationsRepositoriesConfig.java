package net.pautet.softs.demospring.operations.internal.config;

import net.pautet.softs.demospring.operations.internal.persistence.MessageRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = MessageRepository.class)
class OperationsRepositoriesConfig {
}
