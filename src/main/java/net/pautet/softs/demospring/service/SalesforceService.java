package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.DataCloudIngestResponse;
import net.pautet.softs.demospring.entity.SalesforceUserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.pautet.softs.demospring.service.NetatmoService.*;

@Slf4j
@Service
@AllArgsConstructor
public class SalesforceService {

    private final SalesforceAuth salesforceAuth;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchData() throws IOException {
        Map<String, String> queryParams = Map.of(
                "q", "SELECT Id,Name FROM Account LIMIT 10"
        );
        ResponseEntity<String> responseEntity = salesforceAuth.createSalesforceApiClient().get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/services/data/v59.0/query"); // Set the base URL or path
                    queryParams.forEach(uriBuilder::queryParam); // Add each key-value pair as a query param
                    return uriBuilder.build();
                })
                .retrieve().toEntity(String.class);

        log.debug("Salesforce fetch data response: {}", responseEntity.getBody());
        return responseEntity.getBody();
    }

    public String fetchDataCloudData() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sql", "SELECT * FROM Netatmo_Weather_Connector_Weath_D902105F__dll");
        ResponseEntity<String> responseEntity = salesforceAuth.createDataCloudApiClient().post()
                .uri("/api/v2/query").contentType(MediaType.APPLICATION_JSON).body(payload)
                .retrieve().onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Salesforce query failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                }).toEntity(String.class);

        log.debug("Data Cloud fetch data response status: {}", responseEntity.getStatusCode());
        log.debug("Data Cloud fetch data response: {}", responseEntity);
        return responseEntity.getBody();
    }

    // Push Netatmo metrics to Data Cloud (unchanged, included for completeness)
    public void pushToDataCloud(List<Map<String, Object>> metrics) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> metric : metrics) {
            Map<String, Object> readings = new HashMap<>();
            readings.put("ModuleName", safeString(metric.get(MODULE_NAME)));
            readings.put("DeviceId", safeString(metric.get(MODULE_ID)));
            readings.put("Timestamp", metric.get(TIMESTAMP));
            if (metric.containsKey(TEMPERATURE)) readings.put(TEMPERATURE, safeNumber(metric.get(TEMPERATURE)));
            if (metric.containsKey(HUMIDITY)) readings.put(HUMIDITY, safeNumber(metric.get(HUMIDITY)));
            if (metric.containsKey(CO_2)) readings.put(CO_2, safeNumber(metric.get(CO_2)));
            if (metric.containsKey(PRESSURE)) readings.put(PRESSURE, safeNumber(metric.get(PRESSURE)));
            if (metric.containsKey(NOISE)) readings.put(NOISE, safeNumber(metric.get(NOISE)));
            if (metric.containsKey(RAIN)) readings.put(RAIN, safeNumber(metric.get(RAIN)));
            data.add(readings);
        }
        payload.put("data", data);

        ResponseEntity<DataCloudIngestResponse> responseEntity = salesforceAuth.createDataCloudApiClient().post().uri("/api/v1/ingest/sources/Netatmo_Weather_Connector/WeatherStationReading").contentType(MediaType.APPLICATION_JSON).body(payload)
                .retrieve().onStatus(status -> status != HttpStatus.ACCEPTED, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Data Cloud query failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                }).toEntity(DataCloudIngestResponse.class);

        if (!responseEntity.getBody().accepted()) {
            throw new IOException("Data Cloud did not accept ingested data !: " + responseEntity.getBody());
        }

        log.info("Data Cloud accepted ingest data");
    }

    private String safeString(Object value) {
        return value != null ? value.toString() : "";
    }

    private String safeNumber(Object value) {
        return value != null ? value.toString() : null;
    }

    public SalesforceUserInfo getSalesforceUser() throws IOException {
        return salesforceAuth.getSalesforceUser();
    }
}
