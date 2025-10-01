package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.repository.RedisUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisUserService implements UserDetailsService {

    private final RedisUserRepository userRepository; // 💡 Inject the Repository

    private static final String USER_KEY_PREFIX = "user:";
    private static final long USER_EXPIRY_DAYS = 30;

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