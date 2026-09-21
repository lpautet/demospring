package net.pautet.softs.demospring.identity.internal.config;

import net.pautet.softs.demospring.identity.internal.persistence.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
class IdentityRepositoriesConfig {
}
