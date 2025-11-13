package net.pautet.softs.demospring.config;

import net.pautet.softs.demospring.repository.MessageRepository;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import net.pautet.softs.demospring.repository.RedisUserRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(
    basePackages = "net.pautet.softs.demospring.repository",
    includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        MessageRepository.class,
        RecommendationHistoryRepository.class
    }),
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisUserRepository.class
    })
)
@EnableRedisRepositories(
    basePackages = "net.pautet.softs.demospring.repository",
    includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisUserRepository.class
    }),
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        MessageRepository.class,
        RecommendationHistoryRepository.class
    })
)
public class DataRepositoriesConfig {
}
