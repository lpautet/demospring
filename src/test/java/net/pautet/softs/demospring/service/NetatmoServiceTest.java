package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetatmoServiceTest {

    private NetatmoConfig netatmoConfig;
    private AppConfig appConfig;
    private StringRedisTemplate redisTemplate;
    private MessageService messageService;
    private NetatmoService netatmoService;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        netatmoConfig = mock(NetatmoConfig.class);
        appConfig = mock(AppConfig.class);
        redisTemplate = mock(StringRedisTemplate.class);
        messageService = mock(MessageService.class);
        restClient = mock(RestClient.class);
        netatmoService = new NetatmoService(appConfig, netatmoConfig, redisTemplate, messageService);
    }

    @Test
    void getNetatmoMetrics_WhenSuccessful() throws Exception {
        // Arrange
        String mockResponse = "{\"body\": {\"devices\": [{\"dashboard_data\": {\"Temperature\": 20.5, \"Humidity\": 60}}]}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restClient.get().uri(anyString()).retrieve().toEntity(String.class)).thenReturn(responseEntity);

        // Act
        List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();

        // Assert
        assertNotNull(metrics);
        assertEquals(1, metrics.size());
        Map<String, Object> metric = metrics.get(0);
        assertEquals(20.5, metric.get("temperature"));
        assertEquals(60, metric.get("humidity"));
    }

    @Test
    void getNetatmoMetrics_WhenApiError() {
        // Arrange
        ResponseEntity<String> responseEntity = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restClient.get().uri(anyString()).retrieve().toEntity(String.class)).thenReturn(responseEntity);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> netatmoService.getNetatmoMetrics());
    }

    @Test
    void getNetatmoMetrics_WhenInvalidResponse() {
        // Arrange
        String invalidResponse = "Invalid JSON";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(invalidResponse, HttpStatus.OK);
        when(restClient.get().uri(anyString()).retrieve().toEntity(String.class)).thenReturn(responseEntity);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> netatmoService.getNetatmoMetrics());
    }

    @Test
    void getNetatmoMetrics_WhenEmptyResponse() throws Exception {
        // Arrange
        String emptyResponse = "{\"body\": {\"devices\": []}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(emptyResponse, HttpStatus.OK);
        when(restClient.get().uri(anyString()).retrieve().toEntity(String.class)).thenReturn(responseEntity);

        // Act
        List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();

        // Assert
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }
} 