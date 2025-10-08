package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.repository.RedisUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
public class RedisUserService implements UserDetailsService {

    private final RedisUserRepository userRepository; // 💡 Inject the Repository

    // Constructor is now much cleaner
    public RedisUserService(RedisUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findByUsername(String username) {
        // Find by username (using the derived query method)
        return userRepository.findByUsername(username).orElse(null);
    }

    public User save(User user) {
        return userRepository.save(user); // Handles serialization, keying, and expiry
    }

    public void delete(String username) {
        // Note: CrudRepository delete methods typically work by ID,
        // which might require a different approach for username keying.
        // For simplicity with the derived method pattern:
        userRepository.findByUsername(username)
                .ifPresent(userRepository::delete);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User Not Found with username: " + username);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                "",
                Collections.emptyList()
        );
    }
} 