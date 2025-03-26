package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.TokenResponse;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.repository.UserRepository;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.SalesforceService;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
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
    private UserRepository userService;
    private NetatmoService netatmoService;
    private SalesforceService salesforceService;
    private AppConfig appConfig;

    private class RefreshTokenInterceptor implements ClientHttpRequestInterceptor {

        private final User user;

        RefreshTokenInterceptor(Principal principal) {
            this.user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        }

        @Override
        public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = execution.execute(request, body);
            // handle 403 FORBIDDEN (Access token expired if error.code == 3)
            if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.info("Got FORBIDDEN status, refreshing token...");
                request.getHeaders().replace("Authorization", Collections.singletonList("Bearer " + refreshToken(user)));
                return execution.execute(request, body);
            }
            return response;
        }
    }

    private RestClient createApiWebClient(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return RestClient.builder().baseUrl("https://api.netatmo.com/api")
                .defaultHeader("Authorization", "Bearer " + user.getAccessToken())
                .requestInterceptor(new RefreshTokenInterceptor(principal))
                .build();
    }

    @GetMapping("/whoami")
    public ResponseEntity<User> getWhoAmI(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return ResponseEntity.ok(user);
    }

    @GetMapping("/homesdata")
    public String getHomesData(Principal principal) {
        return createApiWebClient(principal).get().uri("/homesdata")
                .retrieve().body(String.class);
    }

    @GetMapping("/homestatus")
    public ResponseEntity<String> getHomeStatus(Principal principal, @RequestParam("home_id") String homeId) {
        final ResponseEntity[] re = {null};
        String ret = createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/homestatus")
                        .queryParam("home_id", homeId).build())
                .retrieve()
                .onStatus(HttpStatus.FORBIDDEN::equals, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    log.error("/homestatus: FORBIDDEN: {}", body);
                    re[0] = ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("getHomeStatus: FORBIDDEN " + body);
                })
                .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    log.error("/homestatus: SERVICE_UNAVAILABLE: {}", body);
                    re[0] = ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("getHomeStatus: SERVICE_UNAVAILABLE " + body);
                })
                .body(String.class);
        return ResponseEntity.ok(ret);
    }

    @GetMapping("/getmeasure")
    public ResponseEntity<String> getMeasure(Principal principal,
                                             @RequestParam("device_id") String deviceId,
                                             @RequestParam("module_id") String moduleId,
                                             @RequestParam("scale") String scale,
                                             @RequestParam("type") String[] types) {
        String ret = createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/getmeasure")
                        .queryParam("device_id", deviceId)
                        .queryParam("module_id", moduleId)
                        .queryParam("scale", scale)
                        .queryParam("type", Arrays.asList(types))
                        .queryParam("date_begin", (System.currentTimeMillis() / 1000) - 24 * 3600)
                        .build())
                .retrieve().body(String.class);
        return ResponseEntity.ok(ret);
    }

    @GetMapping("/env")
    public ResponseEntity<Map<String, String>> getEnv() {
        return ResponseEntity.ok(System.getenv());
    }

    private User refreshToken(User user) {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", netatmoConfig.getClientId());
        formData.add("client_secret", netatmoConfig.getClientSecret());
        formData.add("refresh_token", user.getRefreshToken());
        TokenResponse tokenResponse = RestClient.builder().baseUrl(NETATMO_API_URI)
                .build().post().uri("/oauth2/token").body(formData)
                .retrieve()
                .body(TokenResponse.class);
        log.info("Token refreshed!");
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());
        return userService.save(user);
    }

    @GetMapping("/salesforce/accounts")
    public String getAccounts() throws IOException {
        return salesforceService.fetchData();
    }

    @GetMapping("/datacloud/data")
    public String getDataCloudData() throws IOException {
        return salesforceService.fetchDataCloudData();
    }

    // New endpoint to start Netatmo authorization
    @GetMapping("/netatmo/authorize")
    public RedirectView authorizeNetatmo() {
        System.out.println("Authorize Scope:" + NETATMO_SCOPE);
        System.out.println("Authorize redirect_uri:" + appConfig.getRedirectUri() + NETATMO_CALLBACK_ENDPOINT);
        System.out.println("Authorize client_id:" + netatmoConfig.getClientId());
        String authUrl = "https://api.netatmo.com/oauth2/authorize" +
                "?client_id=" + netatmoConfig.getClientId() +
                "&redirect_uri=" + URLEncoder.encode(appConfig.getRedirectUri() + NETATMO_CALLBACK_ENDPOINT) +
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
