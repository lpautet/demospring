package net.pautet.softs.demospring.repository;

import net.pautet.softs.demospring.entity.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, String> {
    Mono<User> findByUsername(String username);
}