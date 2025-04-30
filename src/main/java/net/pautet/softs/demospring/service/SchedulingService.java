package net.pautet.softs.demospring.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

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

    public Instant getLastExecutionTime(String taskKey) {
        String lastExecution = redisTemplate.opsForValue().get(taskKey);
        return lastExecution != null ? Instant.parse(lastExecution) : null;
    }

    public void updateLastExecutionTime(String taskKey) {
        redisTemplate.opsForValue().set(taskKey, Instant.now().toString());
    }

    public boolean shouldExecute(String taskKey, long intervalMillis) {
        Instant lastExecution = getLastExecutionTime(taskKey);
        if (lastExecution == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - lastExecution.toEpochMilli() >= intervalMillis;
    }

    // Task-specific convenience methods
    public boolean shouldExecuteNetatmoToDataCloud(long intervalMillis) {
        return shouldExecute(NETATMO_TO_DATACLOUD_KEY, intervalMillis);
    }

    public void updateNetatmoToDataCloudExecutionTime() {
        updateLastExecutionTime(NETATMO_TO_DATACLOUD_KEY);
    }

    public boolean shouldExecuteMessageCleanup(long intervalMillis) {
        return shouldExecute(MESSAGE_CLEANUP_KEY, intervalMillis);
    }

    public void updateMessageCleanupExecutionTime() {
        updateLastExecutionTime(MESSAGE_CLEANUP_KEY);
    }

    public boolean shouldExecuteMetricsCollection(long intervalMillis) {
        return shouldExecute(METRICS_COLLECTION_KEY, intervalMillis);
    }

    public void updateMetricsCollectionExecutionTime() {
        updateLastExecutionTime(METRICS_COLLECTION_KEY);
    }
} 