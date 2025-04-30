package net.pautet.softs.demospring.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulingServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        schedulingService = new SchedulingService(redisTemplate);
    }

    @Test
    void shouldExecute_WhenNoPreviousExecution() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertTrue(schedulingService.shouldExecute("test:task", 1000));
    }

    @Test
    void shouldExecute_WhenIntervalHasPassed() {
        Instant pastTime = Instant.now().minus(2, ChronoUnit.HOURS);
        when(valueOperations.get(anyString())).thenReturn(pastTime.toString());
        assertTrue(schedulingService.shouldExecute("test:task", 3600000)); // 1 hour
    }

    @Test
    void shouldNotExecute_WhenIntervalHasNotPassed() {
        Instant recentTime = Instant.now().minus(30, ChronoUnit.MINUTES);
        when(valueOperations.get(anyString())).thenReturn(recentTime.toString());
        assertFalse(schedulingService.shouldExecute("test:task", 3600000)); // 1 hour
    }

    @Test
    void updateLastExecutionTime_SetsCurrentTime() {
        schedulingService.updateLastExecutionTime("test:task");
        verify(valueOperations).set(eq("test:task"), anyString());
    }

    @Test
    void getLastExecutionTime_ReturnsNullWhenNoValue() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertNull(schedulingService.getLastExecutionTime("test:task"));
    }

    @Test
    void getLastExecutionTime_ReturnsParsedInstant() {
        Instant expectedTime = Instant.now();
        when(valueOperations.get(anyString())).thenReturn(expectedTime.toString());
        assertEquals(expectedTime, schedulingService.getLastExecutionTime("test:task"));
    }

    @Test
    void taskSpecificMethods_UseCorrectKeys() {
        // Test Netatmo to DataCloud methods
        schedulingService.shouldExecuteNetatmoToDataCloud(1000);
        verify(valueOperations).get("scheduler:netatmo_to_datacloud");

        schedulingService.updateNetatmoToDataCloudExecutionTime();
        verify(valueOperations).set(eq("scheduler:netatmo_to_datacloud"), anyString());

        // Test Message Cleanup methods
        schedulingService.shouldExecuteMessageCleanup(1000);
        verify(valueOperations).get("scheduler:message_cleanup");

        schedulingService.updateMessageCleanupExecutionTime();
        verify(valueOperations).set(eq("scheduler:message_cleanup"), anyString());

        // Test Metrics Collection methods
        schedulingService.shouldExecuteMetricsCollection(1000);
        verify(valueOperations).get("scheduler:metrics_collection");

        schedulingService.updateMetricsCollectionExecutionTime();
        verify(valueOperations).set(eq("scheduler:metrics_collection"), anyString());
    }
} 