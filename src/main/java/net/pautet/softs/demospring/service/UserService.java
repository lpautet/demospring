package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Mono<User> save(User user) {
        //user.setPassword(user.getPassword()); // Encrypt password before saving
        return userRepository.save(user);
    }
}
