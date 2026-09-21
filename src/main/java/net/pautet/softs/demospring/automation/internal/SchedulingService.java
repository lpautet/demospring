package net.pautet.softs.demospring.automation.internal;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;

@Service
public class SchedulingService {
    private final StringRedisTemplate redisTemplate;

    // Task-specific keys
    private static final String NETATMO_TO_DATACLOUD_KEY = "scheduler:netatmo_to_datacloud";
    private static final String MESSAGE_CLEANUP_KEY = "scheduler:message_cleanup";
    private static final String METRICS_COLLECTION_KEY = "scheduler:metrics_collection";

    public SchedulingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String taskKey, long intervalMillis) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                taskKey, Instant.now().toString(), Duration.ofMillis(intervalMillis));
        return Boolean.TRUE.equals(acquired);
    }

    // Task-specific convenience methods
    public boolean tryAcquireNetatmoToDataCloud(long intervalMillis) {
        return tryAcquire(NETATMO_TO_DATACLOUD_KEY, intervalMillis);
    }

    public boolean tryAcquireMessageCleanup(long intervalMillis) {
        return tryAcquire(MESSAGE_CLEANUP_KEY, intervalMillis);
    }

    public boolean tryAcquireMetricsCollection(long intervalMillis) {
        return tryAcquire(METRICS_COLLECTION_KEY, intervalMillis);
    }
}
