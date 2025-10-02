package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.config.ConnectorSchemaProvider;
import net.pautet.softs.demospring.config.SalesforceConfig;
import net.pautet.softs.demospring.entity.DataCloudIngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesforceServiceTest {

    private SalesforceConfig salesforceConfig;
    private SalesforceAuth salesforceAuth;
    private ConnectorSchemaProvider connectorSchemaProvider;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;
    private SalesforceService salesforceService;

    @BeforeEach
    void setUp() throws IOException {
        salesforceAuth = mock(SalesforceAuth.class);
        salesforceConfig = mock(SalesforceConfig.class);
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        connectorSchemaProvider = mock(ConnectorSchemaProvider.class);
        when(connectorSchemaProvider.schemaName()).thenReturn("WeatherStationData");
        when(salesforceConfig.connectorName()).thenReturn("TestConnector");

        salesforceService = new SalesforceService(salesforceConfig, salesforceAuth, connectorSchemaProvider);

        // Default chain wiring
        when(salesforceAuth.createDataCloudApiClient()).thenReturn(restClient);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.contentType(any(MediaType.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Test
    void pushToDataCloud_WhenSuccessful() throws IOException {
        // Arrange
        List<Map<String, Object>> metrics = createTestMetrics();
        ResponseEntity<DataCloudIngestResponse> responseEntity = new ResponseEntity<>(new DataCloudIngestResponse(true), HttpStatus.ACCEPTED);
        when(responseSpec.toEntity(eq(DataCloudIngestResponse.class))).thenReturn(responseEntity);

        // Act
        salesforceService.pushToDataCloud(metrics);

        // Assert
        verify(restClient).post();
        verify(requestBodyUriSpec).uri(anyString());
        verify(requestBodySpec).contentType(eq(MediaType.APPLICATION_JSON));
        verify(requestBodySpec).body(any());
        verify(requestBodySpec).retrieve();
        verify(responseSpec).onStatus(any(), any());
        verify(responseSpec).toEntity(eq(DataCloudIngestResponse.class));
    }

    @Test
    void pushToDataCloud_WhenApiError() {
        // Arrange
        List<Map<String, Object>> metrics = createTestMetrics();
        ResponseEntity<DataCloudIngestResponse> responseEntity = new ResponseEntity<>(new DataCloudIngestResponse(false), HttpStatus.ACCEPTED);
        when(responseSpec.toEntity(eq(DataCloudIngestResponse.class))).thenReturn(responseEntity);

        // Act & Assert
        assertThrows(IOException.class, () -> salesforceService.pushToDataCloud(metrics));
    }

    @Test
    void pushToDataCloud_WhenEmptyMetrics() throws IOException {
        // Arrange
        List<Map<String, Object>> emptyMetrics = Arrays.asList();
        ResponseEntity<DataCloudIngestResponse> responseEntity = new ResponseEntity<>(new DataCloudIngestResponse(true), HttpStatus.ACCEPTED);
        when(responseSpec.toEntity(eq(DataCloudIngestResponse.class))).thenReturn(responseEntity);

        // Act
        salesforceService.pushToDataCloud(emptyMetrics);

        // Assert
        verify(restClient).post();
    }

    private List<Map<String, Object>> createTestMetrics() {
        Map<String, Object> metric = new HashMap<>();
        metric.put("ModuleName", "Indoor");
        metric.put("DeviceId", "abc");
        metric.put("Timestamp", "2024-10-01T00:00:00Z");
        metric.put("Temperature", 20.5);
        metric.put("Humidity", 60);
        return Arrays.asList(metric);
    }
}
 