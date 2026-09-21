package net.pautet.softs.demospring.automation.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulingServiceTest {
    private ValueOperations<String, String> valueOperations;
    private SchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        schedulingService = new SchedulingService(redisTemplate);
    }

    @Test
    void acquiresAnExpiringAtomicLock() {
        when(valueOperations.setIfAbsent(eq("test:task"), anyString(), eq(Duration.ofSeconds(1))))
                .thenReturn(true);
        assertTrue(schedulingService.tryAcquire("test:task", 1000));
    }

    @Test
    void doesNotAcquireAnExistingLock() {
        when(valueOperations.setIfAbsent(eq("test:task"), anyString(), eq(Duration.ofSeconds(1))))
                .thenReturn(false);
        assertFalse(schedulingService.tryAcquire("test:task", 1000));
    }

    @Test
    void taskSpecificMethodsUseDistinctKeys() {
        schedulingService.tryAcquireNetatmoToDataCloud(1000);
        schedulingService.tryAcquireMessageCleanup(1000);
        schedulingService.tryAcquireMetricsCollection(1000);

        verify(valueOperations).setIfAbsent(eq("scheduler:netatmo_to_datacloud"), anyString(), eq(Duration.ofSeconds(1)));
        verify(valueOperations).setIfAbsent(eq("scheduler:message_cleanup"), anyString(), eq(Duration.ofSeconds(1)));
        verify(valueOperations).setIfAbsent(eq("scheduler:metrics_collection"), anyString(), eq(Duration.ofSeconds(1)));
    }
}
