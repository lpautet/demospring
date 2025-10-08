package net.pautet.softs.demospring.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.*;
import net.pautet.softs.demospring.exception.NetatmoApiException;
import net.pautet.softs.demospring.service.MessageService;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.RedisUserService;
import net.pautet.softs.demospring.service.SalesforceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_SCOPE;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    public static final String NETATMO_CALLBACK_ENDPOINT = "/api" + "/netatmo/callback";

    private final NetatmoConfig netatmoConfig;
    private final RedisUserService redisUserService;
    private final NetatmoService netatmoService;
    private final SalesforceService salesforceService;
    private final AppConfig appConfig;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public ApiController(NetatmoConfig netatmoConfig,
                         RedisUserService redisUserService,
                         NetatmoService netatmoService,
                         SalesforceService salesforceService,
                         AppConfig appConfig,
                         MessageService messageService,
                         ObjectMapper objectMapper) {
        this.netatmoConfig = netatmoConfig;
        this.redisUserService = redisUserService;
        this.netatmoService = netatmoService;
        this.salesforceService = salesforceService;
        this.appConfig = appConfig;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getAllMessages() {
        return ResponseEntity.ok(messageService.getAllMessages());
    }

    private class RefreshTokenInterceptor implements ClientHttpRequestInterceptor {

        private final User user;

        RefreshTokenInterceptor(Principal principal) {
            this.user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        }

        @Override
        public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = execution.execute(request, body);
            HttpStatusCode status = response.getStatusCode();
            log.warn("Intercepted HTTP client error with status code " + status);

            // Handle 401/403 only; avoid reading body for other statuses
            if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
                String responseBody = new String(response.getBody().readAllBytes());

                // Try to parse the error response using injected ObjectMapper
                ObjectMapper mapper = objectMapper;
                NetatmoErrorResponse error;
                try {
                    error = mapper.readValue(responseBody, NetatmoErrorResponse.class);
                } catch (Exception e) {
                    // If parsing fails, propagate as IOException with original body (already consumed)
                    throw new IOException("Error parsing Netatmo error response (" + status + "): " + responseBody);
                }

                // Netatmo access token expired codes: 3 or 26
                if (error.error().code() == 3 || error.error().code() == 26) {
                    String newToken = refreshToken(user);
                    if (newToken == null) {
                        throw new IOException("Failed to refresh token - received null token");
                    }
                    request.getHeaders().set("Authorization", "Bearer " + newToken);
                    return execution.execute(request, body);
                } else {
                    HttpStatus httpStatus = HttpStatus.resolve(status.value());
                    if (httpStatus == null) {
                        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                    }
                    throw new NetatmoApiException(error, httpStatus);
                }
            }
            return response;
        }

        private String refreshToken(User user) throws IOException {
            try {
                MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
                formData.add("grant_type", "refresh_token");
                formData.add("client_id", netatmoConfig.clientId());
                formData.add("client_secret", netatmoConfig.clientSecret());
                formData.add("refresh_token", user.getRefreshToken());
                NetatmoTokenResponse tokenResponse = RestClient.builder().baseUrl(NETATMO_API_URI)
                        .build().post().uri("/oauth2/token").body(formData)
                        .retrieve()
                        .body(NetatmoTokenResponse.class);
                log.warn("Netatmo Token Response: " + tokenResponse);
                messageService.info("Netatmo Token refreshed!");
                if (tokenResponse == null) {
                    throw new IOException("Unexpected null tokenResponse!");
                }
                user.setAccessToken(tokenResponse.accessToken());
                user.setRefreshToken(tokenResponse.refreshToken());
                redisUserService.save(user);
                return tokenResponse.accessToken();
            } catch (Exception e) {
                log.error("Error refreshing token: {}", e.getMessage(), e);
                throw new IOException("Failed to refresh token", e);
            }
        }
    }

    private RestClient createApiWebClient(Principal principal) {
        try {
            User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (user == null || user.getAccessToken() == null) {
                log.error("User or access token is null. User: {}, AccessToken: {}", user, user != null ? user.getAccessToken() : null);
                throw new IllegalStateException("User or access token is null");
            }
            log.debug("Creating API web client with access token: {}", user.getAccessToken());
            return RestClient.builder().baseUrl(NETATMO_API_URI + "/api")
                    .defaultHeader("Authorization", "Bearer " + user.getAccessToken())
                    .requestInterceptor(new RefreshTokenInterceptor(principal))
                    .build();
        } catch (Exception e) {
            log.error("Error creating API web client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create API web client: " + e.getMessage(), e);
        }
    }

    @GetMapping("/whoami")
    public ResponseEntity<User> getWhoAmI(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return ResponseEntity.ok(user);
    }

    @Cacheable(value = "homesdata", key = "#principal.name", unless = "#result == null")
    @GetMapping("/homesdata")
    public String getHomesData(Principal principal) {
       log.debug("Calling for homesdata: " + principal.getName());
        return createApiWebClient(principal).get().uri("/homesdata")
                .retrieve().body(String.class);
    }

    @Cacheable(value = "homestatus", key = "#principal.name + ':' + #homeId", unless = "#result == null")
    @GetMapping("/homestatus")
    public String getHomeStatus(Principal principal, @RequestParam("home_id") String homeId) {
        log.debug("Calling for homesdata: " + principal.getName() + ":" + homeId);
        return createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/homestatus")
                        .queryParam("home_id", homeId)
                        .build())
                .retrieve().body(String.class);
    }

    @Cacheable(value = "getmeasure", key = "#principal.name + ':' + #deviceId + ':' + #moduleId + ':' + #scale + ':' + T(java.util.Arrays).toString(#types)", unless = "#result == null")
    @GetMapping("/getmeasure")
    public String getMeasure(Principal principal,
                             @RequestParam("device_id") String deviceId,
                             @RequestParam("module_id") String moduleId,
                             @RequestParam("scale") String scale,
                             @RequestParam("type") String[] types) {
       log.debug("Calling for getmeasure: " + principal.getName() + ":" + deviceId + ":" + moduleId + ":" + scale + ":" + Arrays.toString(types) );
        return createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/getmeasure")
                        .queryParam("device_id", deviceId)
                        .queryParam("module_id", moduleId)
                        .queryParam("scale", scale)
                        .queryParam("type", String.join(",", types))
                        .queryParam("date_begin", (System.currentTimeMillis() / 1000) - 24 * 3600)
                        .build())
                .retrieve()
                .body(String.class);
    }

    @GetMapping("/salesforce/accounts")
    public String getAccounts() throws IOException {
        return salesforceService.fetchData();
    }

    @GetMapping("/datacloud/data")
    public String getDataCloudData() throws IOException {
        return salesforceService.fetchDataCloudData();
    }

    @GetMapping("/salesforce/whoami")
    public SalesforceUserInfo getSalesforceUser() throws IOException {
        return salesforceService.getSalesforceUser();
    }

    // New endpoint to start Netatmo authorization
    @GetMapping("/netatmo/authorize")
    public RedirectView authorizeNetatmo() {
        String authUrl = "https://api.netatmo.com/oauth2/authorize" +
                "?client_id=" + netatmoConfig.clientId() +
                "&redirect_uri=" + URLEncoder.encode(appConfig.redirectUri() + NETATMO_CALLBACK_ENDPOINT, StandardCharsets.UTF_8) +
                "&scope=" + NETATMO_SCOPE +
                "&state=netatmo_auth_state"; // Simple state for security
        return new RedirectView(authUrl);
    }

    // New endpoint to handle Netatmo callback
    @GetMapping("/netatmo/callback")
    public String handleNetatmoCallback(@RequestParam("code") String code, @RequestParam("state") String state) throws Exception {
        if (!"netatmo_auth_state".equals(state)) {
            return "Error: Invalid state parameter";
        }

        NetatmoTokenResponse tokenResponse = netatmoService.exchangeCodeForTokens(code);
        // Save refresh token to Redis
        netatmoService.saveTokens(tokenResponse);
        netatmoService.getNetatmoMetrics();
        return "Netatmo tokens retrieved successfully: " + tokenResponse.toString() + "<br>Refresh token saved";
    }
}
