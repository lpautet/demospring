package net.pautet.softs.demospring.weather;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.identity.NetatmoCredentialService;
import net.pautet.softs.demospring.identity.NetatmoCredentials;
import net.pautet.softs.demospring.weather.internal.config.NetatmoConfig;
import net.pautet.softs.demospring.weather.internal.model.NetatmoBadRequestResponse;
import net.pautet.softs.demospring.weather.NetatmoTokenResponse;
import net.pautet.softs.demospring.weather.internal.model.TokenSet;
import net.pautet.softs.demospring.weather.internal.model.NetatmoErrorResponse;
import net.pautet.softs.demospring.weather.NetatmoApiException;
import net.pautet.softs.demospring.weather.internal.NetatmoBadRequestException;
import net.pautet.softs.demospring.operations.MessageService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class NetatmoService {

    public static final String NETATMO_API_URI = "https://api.netatmo.com";
    public static final String NETATMO_SCOPE = "read_station read_thermostat";
    public static final String NETATMO_CALLBACK_ENDPOINT = "/api/netatmo/callback";

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
    private final ObjectMapper objectMapper = new ObjectMapper(
            new JsonFactoryBuilder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build());
    private final StringRedisTemplate redisTemplate; // Injected Redis client
    private static final String NETATMO_REQUESTS_KEY_PREFIX = "netatmo:requests:";
    private static final Duration REQUEST_COUNTER_TTL = Duration.ofDays(3);
    private final MessageService messageService;
    private final RestClient.Builder restClientBuilder;
    private final NetatmoCredentialService credentialService;

    private RestClient createApiWebClient() throws IOException {
        if (this.tokenSet.getRefreshToken() == null) {
            loadStoredCredentials();
        }
        if (this.tokenSet.getAccessToken() == null || this.tokenSet.getExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new NetAtmo Access Token");
            refreshToken();
        }
        return restClientBuilder.clone().baseUrl(NETATMO_API_URI + "/api")
                .defaultHeader("Authorization", "Bearer " + this.tokenSet.getAccessToken())
                .build();
    }

    public NetatmoService(NetatmoConfig netatmoConfig, StringRedisTemplate redisTemplate,
                          MessageService messageService, RestClient.Builder restClientBuilder,
                          NetatmoCredentialService credentialService) {
        this.netatmoConfig = netatmoConfig;
        this.redisTemplate = redisTemplate;
        this.restClientBuilder = restClientBuilder;
        this.credentialService = credentialService;
        this.tokenSet = new TokenSet();
        loadStoredCredentials();
        this.messageService = messageService;
    }

    private void loadStoredCredentials() {
        NetatmoCredentials stored = credentialService.findSystemCredentials();
        if (stored != null) {
            this.tokenSet.setRefreshToken(stored.refreshToken());
            this.tokenSet.setAccessToken(stored.accessToken());
            this.tokenSet.setExpiresAt(stored.expiresAt() == null ? 0 : stored.expiresAt());
            log.info("Loaded system Netatmo credentials from PostgreSQL; access token expires at {}",
                    new Date(this.tokenSet.getExpiresAt()));
        }
    }

    private void incrementRequestCount() {
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH"));
        String key = NETATMO_REQUESTS_KEY_PREFIX + hourKey;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, REQUEST_COUNTER_TTL);
        }
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
                } catch (JacksonException e) {
                    log.error("Cannot get token from access code status=BAD REQUEST, with unexpected body: {}", response.getBody());
                    throw new IOException("Cannot get token from access code status=BAD REQUEST, with unexpected body: %s".formatted(response.getBody()));
                }
            }
            throw new IOException("Cannot get token from access code status=%s: %s".formatted(response.getStatusCode(), response.getBody()));
        }
        try {
            NetatmoTokenResponse tokenResponse = objectMapper.readValue(response.getBody(), NetatmoTokenResponse.class);
            if (tokenResponse == null) {
                throw new IOException("Cannot get token from access code status=200, null jSON response!");
            }
            messageService.info("Netatmo Token Refreshed");
            messageService.info("Netatmo Token Refreshed");
            return tokenResponse;
        } catch (Exception e) {
            log.error("Cannot get token from access code status=200, with unexpected body: {}", response.getBody());
            throw new IOException("Cannot get token from access code status=200, with unexpected body: %s".formatted(response.getBody()));
        }
    }

    // Persist the dedicated system integration credentials for scheduled Data Cloud forwarding.
    public void saveTokens(NetatmoTokenResponse tokenResponse) {
        this.tokenSet.update(tokenResponse);
        credentialService.saveSystemCredentials(new NetatmoCredentials(
                tokenSet.getAccessToken(), tokenSet.getRefreshToken(), tokenSet.getExpiresAt(), System.currentTimeMillis()));
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
                        } catch (JacksonException e) {
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
                deviceData.put(STATION_NAME, device.get(STATION_NAME).asString());
                deviceData.put(MODULE_NAME, device.get(STATION_NAME).asString());
                deviceData.put(MODULE_ID, device.get("_id").asString());
                JsonNode dashboardData = device.get("dashboard_data");
                deviceData.put(TIMESTAMP, Instant.ofEpochMilli(dashboardData.get("time_utc").asLong() * 1000).toString());
                addMetrics(deviceData, dashboardData);
                metrics.add(deviceData);

                if (device.has("modules")) {
                    for (JsonNode module : device.get("modules")) {
                        Map<String, Object> moduleData = new HashMap<>();
                        moduleData.put(STATION_NAME, device.get(STATION_NAME).asString());
                        moduleData.put(MODULE_NAME, module.get(MODULE_NAME).asString());
                        moduleData.put(MODULE_ID, module.get("_id").asString());
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
        } catch (JacksonException je) {
            log.error("Error parsing Netatmo response: {}", je.getMessage());
            throw new IOException("Error parsing Netatmo response", je);
        } catch (RestClientException | IOException rce) {
            if (rce.getCause() instanceof NetatmoApiException) {
                throw (NetatmoApiException) rce.getCause();
            }
            log.error("Error in getNetatmoMetrics: {}", rce.getMessage());
            throw rce;
        }
    }
}
