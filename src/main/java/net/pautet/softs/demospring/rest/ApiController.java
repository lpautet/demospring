package net.pautet.softs.demospring.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.*;
import net.pautet.softs.demospring.exception.NetatmoApiException;
import net.pautet.softs.demospring.service.MessageService;
import net.pautet.softs.demospring.service.RedisUserService;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.SalesforceService;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
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
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;
import static net.pautet.softs.demospring.rest.AuthController.NETATMO_SCOPE;

@Slf4j
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {

    public static final String NETATMO_CALLBACK_ENDPOINT = "/api" + "/netatmo/callback";

    private NetatmoConfig netatmoConfig;
    private RedisUserService redisUserService;
    private NetatmoService netatmoService;
    private SalesforceService salesforceService;
    private AppConfig appConfig;
    private MessageService messageService;

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
            // handle 403 FORBIDDEN (Access token expired if error.code == 3)
            if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                String responseBody = new String(response.getBody().readAllBytes());

                // Try to parse the error response
                ObjectMapper mapper = new ObjectMapper();
                NetatmoErrorResponse error;
                try {
                    error = mapper.readValue(responseBody, NetatmoErrorResponse.class);
                } catch (Exception e) {
                    throw new IOException("Error parsing Netatmo FORBIDDEN error response: " + responseBody);
                }
                if (error.getError().getCode() == 3 || error.getError().getCode() == 26 ) {  // Access token expired
                    String newToken = refreshToken(user);
                    if (newToken == null) {
                        throw new IOException("Failed to refresh token - received null token");
                    }
                    request.getHeaders().replace("Authorization", Collections.singletonList("Bearer " + newToken));
                    return execution.execute(request, body);
                } else {
                    throw new NetatmoApiException(error, HttpStatus.FORBIDDEN);
                }
            }
            return response;
        }

        private String refreshToken(User user) throws IOException {
            try {
                MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
                formData.add("grant_type", "refresh_token");
                formData.add("client_id", netatmoConfig.getClientId());
                formData.add("client_secret", netatmoConfig.getClientSecret());
                formData.add("refresh_token", user.getRefreshToken());
                TokenResponse tokenResponse = RestClient.builder().baseUrl(NETATMO_API_URI)
                        .build().post().uri("/oauth2/token").body(formData)
                        .retrieve()
                        .body(TokenResponse.class);
                log.info("Netatmo Token refreshed!");
                if (tokenResponse == null) {
                    throw new IOException("Unexpected null tokenResponse!");
                }
                user.setAccessToken(tokenResponse.getAccessToken());
                user.setRefreshToken(tokenResponse.getRefreshToken());
                redisUserService.save(user);
                return tokenResponse.getAccessToken();
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
        System.out.println("Calling for homesdata: " + principal.getName());
        return createApiWebClient(principal).get().uri("/homesdata")
                .retrieve().body(String.class);
    }

    @Cacheable(value = "homestatus", key = "#principal.name + ':' + #homeId", unless = "#result == null")
    @GetMapping("/homestatus")
    public String getHomeStatus(Principal principal, @RequestParam("home_id") String homeId) {
        System.out.println("Calling for homesdata: " + principal.getName() + ":" + homeId);
        return createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/homestatus")
                        .queryParam("home_id", homeId)
                        .build())
                .retrieve().body(String.class);
    }

    @Cacheable(value = "getmeasure", key = "#principal.name + ':' + #deviceId + ':' + #moduleId + ':' + #scale + ':' + {#types}", unless = "#result == null")
    @GetMapping("/getmeasure")
    public String getMeasure(Principal principal,
                             @RequestParam("device_id") String deviceId,
                             @RequestParam("module_id") String moduleId,
                             @RequestParam("scale") String scale,
                             @RequestParam("type") String[] types) {
        System.out.println("Calling for getmeasure: " + principal.getName() + ":" + deviceId + ":" + moduleId + ":" + scale + ":" + Arrays.toString(types) );
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

    @GetMapping("/env")
    public ResponseEntity<Map<String, String>> getEnv() {
        return ResponseEntity.ok(System.getenv());
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
                "?client_id=" + netatmoConfig.getClientId() +
                "&redirect_uri=" + URLEncoder.encode(appConfig.getRedirectUri() + NETATMO_CALLBACK_ENDPOINT, StandardCharsets.UTF_8) +
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

        TokenResponse tokenResponse = netatmoService.exchangeCodeForTokens(code);
        netatmoService.getNetatmoMetrics();
        return "Netatmo tokens retrieved successfully: " + tokenResponse.toString() + "<br>Refresh token saved";
    }
}
