package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;

/**
 * Service for making direct Netatmo API calls.
 * Used by AI functions to retrieve weather data.
 */
@Slf4j
@Service
public class NetatmoApiService {

    private static final Duration CONNECT_TIMEOUT_DURATION = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT_DURATION = Duration.ofSeconds(23);
    private static final String NETATMO_API_URI = "https://api.netatmo.com";

    private final NetatmoConfig netatmoConfig;

    public NetatmoApiService(NetatmoConfig netatmoConfig) {
        this.netatmoConfig = netatmoConfig;
    }

    /**
     * Get homes data for a user (cached - same key as REST endpoint)
     */
    @Cacheable(value = "homesdata", unless = "#result == null")
    public String getHomesData(User user) {
        log.debug("Fetching homesdata for user: {} (cache miss)", user.getUsername());
        RestClient client = createNetatmoApiClient(user);
        return client.get()
                .uri("/api/homesdata")
                .retrieve()
                .body(String.class);
    }

    /**
     * Get home status for a specific home (cached - same key as REST endpoint)
     */
    @Cacheable(value = "homestatus", 
               key = "'homeid:' + #homeId", 
               unless = "#result == null")
    public String getHomeStatus(User user, String homeId) {
        log.debug("Fetching homestatus for user: {} homeId: {} (cache miss)", user.getUsername(), homeId);
        RestClient client = createNetatmoApiClient(user);
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/homestatus")
                        .queryParam("home_id", homeId)
                        .build())
                .retrieve()
                .body(String.class);
    }

    /**
     * Get measurements for a specific module (cached - same key as REST endpoint)
     */
    @Cacheable(value = "measure",
               key = "'d:' + #deviceId + ',m:' + #moduleId + ',:s30min,t:' + T(java.util.Arrays).toString(#types)",
               unless = "#result == null")
    public String getMeasure(User user, String deviceId, String moduleId, String[] types) {
        log.debug("Fetching measures for user: {} device: {} module: {} types: {} (cache miss)", 
                user.getUsername(), deviceId, moduleId, String.join(",", types));
        RestClient client = createNetatmoApiClient(user);
        
        long dateBegin = (System.currentTimeMillis() / 1000) - 24 * 3600; // Last 24 hours
        
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/getmeasure")
                        .queryParam("device_id", deviceId)
                        .queryParam("module_id", moduleId)
                        .queryParam("scale", "30min")
                        .queryParam("type", String.join(",", types))
                        .queryParam("date_begin", dateBegin)
                        .build())
                .retrieve()
                .body(String.class);
    }

    private RestClient createNetatmoApiClient(User user) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT_DURATION.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT_DURATION.toMillis());
        
        return RestClient.builder()
                .baseUrl(NETATMO_API_URI)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(user.getAccessToken()))
                .build();
    }
}
