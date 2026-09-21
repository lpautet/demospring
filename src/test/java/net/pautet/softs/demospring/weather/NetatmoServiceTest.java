package net.pautet.softs.demospring.weather;

import net.pautet.softs.demospring.foundation.AppConfig;
import net.pautet.softs.demospring.operations.MessageService;
import net.pautet.softs.demospring.weather.NetatmoService;
import net.pautet.softs.demospring.weather.internal.config.NetatmoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NetatmoServiceTest {

    private NetatmoConfig netatmoConfig;
    private AppConfig appConfig;
    private StringRedisTemplate redisTemplate;
    private MessageService messageService;
    private NetatmoService netatmoService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        netatmoConfig = mock(NetatmoConfig.class);
        appConfig = mock(AppConfig.class);
        redisTemplate = mock(StringRedisTemplate.class);
        messageService = mock(MessageService.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("netatmo:access_token")).thenReturn("test-access-token");
        when(valueOperations.get("netatmo:expires_at")).thenReturn(Long.toString(System.currentTimeMillis() + 60_000));

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        netatmoService = new NetatmoService(appConfig, netatmoConfig, redisTemplate, messageService, restClientBuilder);
    }

    @Test
    void getNetatmoMetrics_WhenSuccessful() throws Exception {
        // Arrange
        String mockResponse = "{\"body\":{\"devices\":[{\"station_name\":\"Home\",\"_id\":\"device-1\",\"dashboard_data\":{\"time_utc\":1700000000,\"Temperature\":20.5,\"Humidity\":60}}]}}";
        mockServer.expect(requestTo("https://api.netatmo.com/api/getstationsdata"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // Act
        List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();

        // Assert
        assertNotNull(metrics);
        assertEquals(1, metrics.size());
        Map<String, Object> metric = metrics.get(0);
        assertEquals(20.5, metric.get(NetatmoService.TEMPERATURE));
        assertEquals(60, metric.get(NetatmoService.HUMIDITY));
        mockServer.verify();
    }

    @Test
    void getNetatmoMetrics_WhenApiError() {
        // Arrange
        mockServer.expect(requestTo("https://api.netatmo.com/api/getstationsdata"))
                .andRespond(withServerError());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> netatmoService.getNetatmoMetrics());
    }

    @Test
    void getNetatmoMetrics_WhenInvalidResponse() {
        // Arrange
        String invalidResponse = "Invalid JSON";
        mockServer.expect(requestTo("https://api.netatmo.com/api/getstationsdata"))
                .andRespond(withSuccess(invalidResponse, MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThrows(java.io.IOException.class, () -> netatmoService.getNetatmoMetrics());
    }

    @Test
    void getNetatmoMetrics_WhenEmptyResponse() throws Exception {
        // Arrange
        String emptyResponse = "{\"body\": {\"devices\": []}}";
        mockServer.expect(requestTo("https://api.netatmo.com/api/getstationsdata"))
                .andRespond(withSuccess(emptyResponse, MediaType.APPLICATION_JSON));

        // Act
        List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();

        // Assert
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
        mockServer.verify();
    }
}
