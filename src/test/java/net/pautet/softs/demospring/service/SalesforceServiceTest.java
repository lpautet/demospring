package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.config.SalesforceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesforceServiceTest {

    private RestTemplate restTemplate;
    private SalesforceConfig salesforceConfig;
    private SalesforceService salesforceService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        salesforceConfig = mock(SalesforceConfig.class);
        salesforceService = new SalesforceService(salesforceConfig);
    }

    @Test
    void pushToDataCloud_WhenSuccessful() throws IOException {
        // Arrange
        List<Map<String, Object>> metrics = createTestMetrics();
        ResponseEntity<String> responseEntity = new ResponseEntity<>("Success", HttpStatus.OK);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(),
            eq(String.class)
        )).thenReturn(responseEntity);

        // Act
        salesforceService.pushToDataCloud(metrics);

        // Assert
        verify(restTemplate).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(),
            eq(String.class)
        );
    }

    @Test
    void pushToDataCloud_WhenApiError() {
        // Arrange
        List<Map<String, Object>> metrics = createTestMetrics();
        ResponseEntity<String> responseEntity = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(),
            eq(String.class)
        )).thenReturn(responseEntity);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> salesforceService.pushToDataCloud(metrics));
    }

    @Test
    void pushToDataCloud_WhenEmptyMetrics() throws IOException {
        // Arrange
        List<Map<String, Object>> emptyMetrics = Arrays.asList();

        // Act
        salesforceService.pushToDataCloud(emptyMetrics);

        // Assert
        verify(restTemplate, never()).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(),
            eq(String.class)
        );
    }

    private List<Map<String, Object>> createTestMetrics() {
        Map<String, Object> metric = new HashMap<>();
        metric.put("temperature", 20.5);
        metric.put("humidity", 60);
        return Arrays.asList(metric);
    }
} 