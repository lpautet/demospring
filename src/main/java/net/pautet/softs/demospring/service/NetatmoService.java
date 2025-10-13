package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.NetatmoBadRequestResponse;
import net.pautet.softs.demospring.entity.NetatmoTokenResponse;
import net.pautet.softs.demospring.entity.TokenSet;
import net.pautet.softs.demospring.entity.NetatmoErrorResponse;
import net.pautet.softs.demospring.exception.NetatmoApiException;
import net.pautet.softs.demospring.exception.NetatmoBadRequestException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static net.pautet.softs.demospring.rest.ApiController.NETATMO_CALLBACK_ENDPOINT;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_SCOPE;

@Slf4j
@Service
public class NetatmoService {

    public static final String STATION_NAME = "station_name";
    public static final String MODULE_NAME = "module_name";
    public static final String MODULE_ID = "module_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String TEMPERATURE = "Temperature";
    public static final String HUMIDITY = "Humidity";
    public static final String CO_2 = "CO2";
    public static final String PRESSURE = "Pressure";
    public static final String NOISE = "Noise";
    public static final String RAIN = "Rain";
    private final NetatmoConfig netatmoConfig;
    private final TokenSet tokenSet;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature());
    private final StringRedisTemplate redisTemplate; // Injected Redis client
    private static final String NETATMO_REQUESTS_KEY_PREFIX = "netatmo:requests:";

    private RestClient createApiWebClient() throws IOException {
        if (this.tokenSet.getAccessToken() == null || this.tokenSet.getExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new NetAtmo Access Token");
            refreshToken();
        }
        return RestClient.builder().baseUrl(NETATMO_API_URI + "/api")
                .defaultHeader("Authorization", "Bearer " + this.tokenSet.getAccessToken())
                .build();
    }

    public NetatmoService(AppConfig appConfig, NetatmoConfig netatmoConfig, StringRedisTemplate redisTemplate) {
        this.appConfig = appConfig;
        this.netatmoConfig = netatmoConfig;
        this.redisTemplate = redisTemplate;
        this.tokenSet = new TokenSet();
        String loaded = "";
        // Load initial refresh token from Redis if available, otherwise use the property
        String redisValue = redisTemplate.opsForValue().get("netatmo:refresh_token");
        if (redisValue != null) {
            this.tokenSet.setRefreshToken(redisValue);
            loaded += " RefreshToken";
        }
        redisValue = redisTemplate.opsForValue().get("netatmo:access_token");
        if (redisValue != null) {
            this.tokenSet.setAccessToken(redisValue);
            loaded += " AccessToken";
        }
        redisValue = redisTemplate.opsForValue().get("netatmo:expires_at");
        if (redisValue != null) {
            this.tokenSet.setExpiresAt(Long.parseLong(redisValue));
            loaded += " expires: " + new Date(this.tokenSet.getExpiresAt());
        }
        if (!loaded.isEmpty()) {
            log.info("Loaded from redis: {}", loaded);
        }
    }

    private void incrementRequestCount() {
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH"));
        redisTemplate.opsForValue().increment(NETATMO_REQUESTS_KEY_PREFIX + hourKey);
    }

    public String getCurrentHourRequestCount() {
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH"));
        String count = redisTemplate.opsForValue().get(NETATMO_REQUESTS_KEY_PREFIX + hourKey);
        return count != null ? count : "0";
    }

    // Exchange authorization code for access and refresh tokens
    public NetatmoTokenResponse exchangeCodeForTokens(String code, String redirectUri) throws IOException {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", netatmoConfig.clientId());
        formData.add("client_secret", netatmoConfig.clientSecret());
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        formData.add("scope", NETATMO_SCOPE);
        ResponseEntity<String> response = RestClient.builder().baseUrl(NETATMO_API_URI).build().post().uri("/oauth2/token").body(formData)
                .retrieve().toEntity(String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                try {
                    NetatmoBadRequestResponse netatmoBadRequestResponse = objectMapper.readValue(response.getBody(), NetatmoBadRequestResponse.class);
                    if (netatmoBadRequestResponse.error().equals("invalid grant")) {
                        log.error("URI Mismatch in exchanging Code for Tokens redirect_uri={}", redirectUri);
                        throw new NetatmoBadRequestException("URI Mismatch in exchanging Code for Tokens");
                    }
                } catch (IOException e) {
                    log.error("Cannot get token from access code status=BAD REQUEST, with unexpected body: %s".formatted( response.getBody()));
                    throw new IOException("Cannot get token from access code status=BAD REQUEST, with unexpected body: %s".formatted( response.getBody()));
                }
            }
            throw new IOException("Cannot get token from access code status=%s: %s".formatted(response.getStatusCode(), response.getBody()));
        }
        try {
            NetatmoTokenResponse tokenResponse = objectMapper.readValue(response.getBody(), NetatmoTokenResponse.class);
            if (tokenResponse == null) {
                throw new IOException("Failed to exchange code for tokens !");
            }
            return tokenResponse;
        } catch (Exception e) {
            log.error("Cannot get token from access code status=200, with unexpected body: %s".formatted( response.getBody()));
            throw new IOException("Cannot get token from access code status=200, with unexpected body: %s".formatted( response.getBody()));
        }
    }

    // Save refresh token to Redis
    public void saveTokens(NetatmoTokenResponse tokenResponse) {
        this.tokenSet.update(tokenResponse);
        redisTemplate.opsForValue().set("netatmo:refresh_token", tokenSet.getRefreshToken());
        redisTemplate.opsForValue().set("netatmo:access_token", tokenSet.getAccessToken());
        redisTemplate.opsForValue().set("netatmo:expires_at", Long.toString(tokenSet.getExpiresAt()));
    }

    private void refreshToken() throws IOException {
        if (this.tokenSet.getRefreshToken() == null) {
            throw new IllegalStateException("No Netatmo Refresh Token !");
        }
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", netatmoConfig.clientId());
        formData.add("client_secret", netatmoConfig.clientSecret());
        formData.add("refresh_token", tokenSet.getRefreshToken());
        NetatmoTokenResponse tokenResponse = RestClient.builder().baseUrl(NETATMO_API_URI)
                .build().post().uri("/oauth2/token").body(formData)
                .retrieve()
                .body(NetatmoTokenResponse.class);
        if (tokenResponse == null) {
            throw new IOException("Unexpected null TokenResponse");
        }
        log.info("Token refreshed!");

        saveTokens(tokenResponse);
    }

    private void addMetrics(Map<String, Object> data, JsonNode dashboardData) {
        if (dashboardData.has(TEMPERATURE)) data.put(TEMPERATURE, dashboardData.get(TEMPERATURE).asDouble());
        if (dashboardData.has(HUMIDITY)) data.put(HUMIDITY, dashboardData.get(HUMIDITY).asInt());
        if (dashboardData.has(CO_2)) data.put(CO_2, dashboardData.get(CO_2).asInt());
        if (dashboardData.has(PRESSURE)) data.put(PRESSURE, dashboardData.get(PRESSURE).asDouble());
        if (dashboardData.has(NOISE)) data.put(NOISE, dashboardData.get(NOISE).asInt());
        if (dashboardData.has(RAIN)) data.put(RAIN, dashboardData.get(RAIN).asDouble());
    }

    // Retrieve metrics from all Netatmo Weather Station modules
    @Cacheable(value = "netatmoMetrics", unless = "#result == null || #result.isEmpty()")
    public List<Map<String, Object>> getNetatmoMetrics() throws IOException {
        try {
            incrementRequestCount();
            String responseBody = createApiWebClient().get().uri("/getstationsdata")
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.FORBIDDEN, (request, response) -> {
                        log.warn("getstationdata failed with FORBIDDEN");
                        String errorBody = new String(response.getBody().readAllBytes());
                        NetatmoErrorResponse error;
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            error = mapper.readValue(errorBody, NetatmoErrorResponse.class);
                        } catch (IOException e) {
                            throw new IOException("Error parsing Netatmo error response", e);
                        }
                        throw new NetatmoApiException(error, HttpStatus.FORBIDDEN);
                    })
                    .body(String.class);

            //System.out.println("getstationdata:" + responseBody);

            JsonNode json = objectMapper.readTree(responseBody);

            if (!json.has("body") || !json.get("body").has("devices")) {
                throw new IOException("Invalid Netatmo response: " + responseBody);
            }

            List<Map<String, Object>> metrics = new ArrayList<>();
            JsonNode devices = json.get("body").get("devices");

            for (JsonNode device : devices) {
                Map<String, Object> deviceData = new HashMap<>();
                deviceData.put(STATION_NAME, device.get(STATION_NAME).asText());
                deviceData.put(MODULE_NAME, device.get(STATION_NAME).asText());
                deviceData.put(MODULE_ID, device.get("_id").asText());
                JsonNode dashboardData = device.get("dashboard_data");
                deviceData.put(TIMESTAMP, Instant.ofEpochMilli(dashboardData.get("time_utc").asLong() * 1000).toString());
                addMetrics(deviceData, dashboardData);
                metrics.add(deviceData);

                if (device.has("modules")) {
                    for (JsonNode module : device.get("modules")) {
                        Map<String, Object> moduleData = new HashMap<>();
                        moduleData.put(STATION_NAME, device.get(STATION_NAME).asText());
                        moduleData.put(MODULE_NAME, module.get(MODULE_NAME).asText());
                        moduleData.put(MODULE_ID, module.get("_id").asText());
                        JsonNode moduleDashboard = module.get("dashboard_data");
                        if (moduleDashboard == null) {
                            log.info("No dashboard data for " + moduleData.get(MODULE_NAME));
                            continue;
                        }
                        moduleData.put(TIMESTAMP, Instant.ofEpochMilli(moduleDashboard.get("time_utc").asLong() * 1000).toString());
                        addMetrics(moduleData, moduleDashboard);
                        metrics.add(moduleData);
                    }
                }
            }
            return metrics;
        } catch (RestClientException | IOException rce) {
            if (rce.getCause() instanceof NetatmoApiException) {
                throw (NetatmoApiException) rce.getCause();
            }
            log.error("Error in getNetatmoMetrics: {}",rce.getMessage());
            throw rce;
        }
    }
}