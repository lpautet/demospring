package net.pautet.softs.demospring.identity.internal.config;

import net.pautet.softs.demospring.identity.internal.persistence.RedisUserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableRedisRepositories(basePackageClasses = RedisUserRepository.class)
class IdentityRepositoriesConfig {
}
