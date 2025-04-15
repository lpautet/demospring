package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisUserService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String USER_KEY_PREFIX = "user:";
    private static final long USER_EXPIRY_DAYS = 30;

    public RedisUserService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public User findByUsername(String username) {
        try {
            String userJson = redisTemplate.opsForValue().get(USER_KEY_PREFIX + username);
            if (userJson == null) {
                return null;
            }
            return objectMapper.readValue(userJson, User.class);
        } catch (Exception e) {
            log.error("Error finding user by username: {}", username, e);
            return null;
        }
    }

    public User save(User user) {
        try {
            String userJson = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(USER_KEY_PREFIX + user.getUsername(), userJson, USER_EXPIRY_DAYS, TimeUnit.DAYS);
            return user;
        } catch (Exception e) {
            log.error("Error saving user: {}", user.getUsername(), e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    public void delete(String username) {
        redisTemplate.delete(USER_KEY_PREFIX + username);
    }
} 