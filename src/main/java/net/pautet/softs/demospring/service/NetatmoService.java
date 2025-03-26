package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.SalesforceCredentials;
import net.pautet.softs.demospring.entity.TokenResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static net.pautet.softs.demospring.rest.ApiController.NETATMO_CALLBACK_ENDPOINT;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_SCOPE;

@Slf4j
@Service
public class NetatmoService {

    private final NetatmoConfig netatmoConfig;
    private String netatmoRefreshToken;
    private long expiresAt;
    private String accessToken;
    private AppConfig appConfig;

    private final SalesforceCredentials salesforceCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate redisTemplate; // Injected Redis client

    private RestClient createApiWebClient(String accessToken) {
        return RestClient.builder().baseUrl("https://api.netatmo.com/api")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                //  .requestInterceptor(new ApiController.RefreshTokenInterceptor(principal))
                .build();
    }

    public NetatmoService(AppConfig appConfig, NetatmoConfig netatmoConfig, SalesforceCredentials salesforceCredentials, StringRedisTemplate redisTemplate) {
        this.appConfig = appConfig;
        this.netatmoConfig = netatmoConfig;
        this.salesforceCredentials = salesforceCredentials;
        this.redisTemplate = redisTemplate;
        String loaded = "";
        // Load initial refresh token from Redis if available, otherwise use the property
        String redisValue = redisTemplate.opsForValue().get("netatmo:refresh_token");
        if (redisValue != null) {
            this.netatmoRefreshToken = redisValue;
            loaded += " RefreshToken";
        }
        redisValue = redisTemplate.opsForValue().get("netatmo:access_token");
        if (redisValue != null) {
            this.accessToken = redisValue;
            loaded += " AccessToken";
        }
        redisValue = redisTemplate.opsForValue().get("netatmo:expires_at");
        if (redisValue != null) {
            this.expiresAt = Long.parseLong(redisValue);
            loaded += " expires: " +  new Date(this.expiresAt);
        }
        if (!loaded.isEmpty()) {
            log.info("Loaded from redis:" + loaded);
        }
    }

    // Exchange authorization code for access and refresh tokens
    public TokenResponse exchangeCodeForTokens(String code) throws IOException {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", netatmoConfig.getClientId());
        formData.add("client_secret", netatmoConfig.getClientSecret());
        formData.add("code", code);
        formData.add("redirect_uri",  appConfig.getRedirectUri() + NETATMO_CALLBACK_ENDPOINT);
        formData.add("scope", NETATMO_SCOPE);
        ResponseEntity<String> response = RestClient.builder().baseUrl(NETATMO_API_URI).build().post().uri("/oauth2/token").body(formData)
                .retrieve().toEntity(String.class);
        TokenResponse tokenResponse = objectMapper.readValue(response.getBody(), TokenResponse.class);        if (tokenResponse == null) {
            throw new IOException("Failed to exchange code for tokens !");
        }

        // Save refresh token to Redis
        saveTokens(tokenResponse);

        return tokenResponse;
    }

    // Save refresh token to Redis
    public void saveTokens(TokenResponse tokenResponse) {
        redisTemplate.opsForValue().set("netatmo:refresh_token", tokenResponse.getRefreshToken());
        this.netatmoRefreshToken = tokenResponse.getRefreshToken(); // Update in-memory value
        redisTemplate.opsForValue().set("netatmo:access_token", tokenResponse.getAccessToken());
        this.accessToken = tokenResponse.getAccessToken();
        long newExpiresAt = System.currentTimeMillis() - 60000 + tokenResponse.getExpiresIn() * 1000;
        redisTemplate.opsForValue().set("netatmo:expires_at", Long.toString(expiresAt));
        this.expiresAt = newExpiresAt;
    }

    private void refreshToken() throws IOException {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", netatmoConfig.getClientId());
        formData.add("client_secret", netatmoConfig.getClientSecret());
        formData.add("refresh_token", this.netatmoRefreshToken);
        TokenResponse tokenResponse = RestClient.builder().baseUrl(NETATMO_API_URI)
                .build().post().uri("/oauth2/token").body(formData)
                .retrieve()
                .body(TokenResponse.class);
        if (tokenResponse == null) {
            throw new IOException("Unexpected null TokenResponse");
        }
        log.info("Token refreshed!");

        saveTokens(tokenResponse);
    }

    // Retrieve metrics from all Netatmo Weather Station modules
    public List<Map<String, Object>> getNetatmoMetrics() throws Exception {
        if (this.accessToken == null || this.expiresAt <= System.currentTimeMillis()) {
            log.info("Need a new Access Token");
            refreshToken();
        }
        String responseBody = createApiWebClient(this.accessToken).get().uri("/getstationsdata")
                .retrieve().body(String.class);

        JsonNode json = objectMapper.readTree(responseBody);

        if (!json.has("body") || !json.get("body").has("devices")) {
            throw new IOException("Invalid Netatmo response: " + responseBody);
        }

        List<Map<String, Object>> metrics = new ArrayList<>();
        JsonNode devices = json.get("body").get("devices");

        for (JsonNode device : devices) {
            Map<String, Object> deviceData = new HashMap<>();
            deviceData.put("station_name", device.get("station_name").asText());
            deviceData.put("module_name", device.get("station_name").asText());
            deviceData.put("module_id", device.get("_id").asText());
            JsonNode dashboardData = device.get("dashboard_data");
            deviceData.put("timestamp", Instant.ofEpochMilli(dashboardData.get("time_utc").asLong() * 1000).toString());
            addMetrics(deviceData, dashboardData);
            metrics.add(deviceData);

            if (device.has("modules")) {
                for (JsonNode module : device.get("modules")) {
                    Map<String, Object> moduleData = new HashMap<>();
                    moduleData.put("station_name", device.get("station_name").asText());
                    moduleData.put("module_name", module.get("module_name").asText());
                    moduleData.put("module_id", module.get("_id").asText());
                    JsonNode moduleDashboard = module.get("dashboard_data");
                    if (moduleDashboard == null) {
                        log.info("No dashboard data for " + moduleData.get("module_name"));
                        continue;
                    }
                    moduleData.put("timestamp", Instant.ofEpochMilli(moduleDashboard.get("time_utc").asLong() * 1000).toString());
                    addMetrics(moduleData, moduleDashboard);
                    metrics.add(moduleData);
                }
            }
        }
        return metrics;
    }

    private void addMetrics(Map<String, Object> data, JsonNode dashboardData) {
        if (dashboardData.has("Temperature")) data.put("Temperature", dashboardData.get("Temperature").asDouble());
        if (dashboardData.has("Humidity")) data.put("Humidity", dashboardData.get("Humidity").asInt());
        if (dashboardData.has("CO2")) data.put("CO2", dashboardData.get("CO2").asInt());
        if (dashboardData.has("Pressure")) data.put("Pressure", dashboardData.get("Pressure").asDouble());
        if (dashboardData.has("Noise")) data.put("Noise", dashboardData.get("Noise").asInt());
        if (dashboardData.has("Rain")) data.put("Rain", dashboardData.get("Rain").asDouble());
    }

    // Push Netatmo metrics to Data Cloud (unchanged, included for completeness)
    public String pushToDataCloud(List<Map<String, Object>> metrics) throws IOException {
        HttpClient httpClient = HttpClients.createDefault();
        String apiUrl = salesforceCredentials.dataCloudInstanceUrl() + "/api/v1/ingest/sources/Netatmo_Weather_Connector/WeatherStationReading";
        HttpPost post = new HttpPost(apiUrl);
        post.setHeader("Authorization", "Bearer " + salesforceCredentials.dataCloudAccessToken());
        post.setHeader("Content-Type", "application/json");

        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> metric : metrics) {
            Map<String, Object> record = new HashMap<>();
            record.put("ModuleName", safeString(metric.get("module_name")));
            record.put("DeviceId", safeString(metric.get("module_id")));
            record.put("Timestamp", metric.get("timestamp"));
            if (metric.containsKey("Temperature")) record.put("Temperature", safeNumber(metric.get("Temperature")));
            if (metric.containsKey("Humidity")) record.put("Humidity", safeNumber(metric.get("Humidity")));
            if (metric.containsKey("CO2")) record.put("CO2", safeNumber(metric.get("CO2")));
            if (metric.containsKey("Pressure")) record.put("Pressure", safeNumber(metric.get("Pressure")));
            if (metric.containsKey("Noise")) record.put("Noise", safeNumber(metric.get("Noise")));
            if (metric.containsKey("Rain")) record.put("Rain", safeNumber(metric.get("Rain")));
            data.add(record);
        }
        payload.put("data", data);

        String jsonPayload = objectMapper.writeValueAsString(payload);
        System.out.println(jsonPayload);
        post.setEntity(new StringEntity(jsonPayload));

        HttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        System.out.println("Data Cloud ingest response: " + responseBody);
        return responseBody;
    }

    private String safeString(Object value) {
        return value != null ? value.toString() : "";
    }

    private String safeNumber(Object value) {
        return value != null ? value.toString() : null;
    }

    @Scheduled(fixedRate = 600000)
    public void scheduleNetatmoToDataCloud() {
        try {
            log.info("Starting scheduled Netatmo data fetch and push at {}", new java.util.Date());
            List<Map<String, Object>> metrics = getNetatmoMetrics();
            pushToDataCloud(metrics);
            log.info("Scheduled task completed successfully");
        } catch (Exception e) {
            log.error("Error in scheduled task: ", e);
        }
    }
}