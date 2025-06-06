package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.security.JWTUtil;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.AuthResponse;
import net.pautet.softs.demospring.entity.SignupRequest;
import net.pautet.softs.demospring.entity.TokenResponse;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.service.RedisUserService;
import net.pautet.softs.demospring.service.NetatmoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Date;

@RestController
@AllArgsConstructor
@RequestMapping(AuthController.AUTH_ENDPOINTS_PREFIX)
public class AuthController {

    static final String AUTH_ENDPOINTS_PREFIX = "/api/auth";
    static final String CALLBACK_ATMO = "/callbackAtmo";
    static final String REDIRECT_ENDPOINT = AUTH_ENDPOINTS_PREFIX + CALLBACK_ATMO;
    public static final String NETATMO_SCOPE = "read_station read_thermostat";
    public static final String NETATMO_API_URI = "https://api.netatmo.com";

    private NetatmoConfig netatmoConfig;
    private JWTUtil jwtUtil;
    private RedisUserService redisUserService;
    private AppConfig appConfig;
    private NetatmoService netatmoService;

    private final RestClient tokenRestClient = RestClient.builder().baseUrl(NETATMO_API_URI).build();

    @GetMapping("/hello")
    public String getHello() {
        return "Hello, the time at the server is now " + new Date() + "\n";
    }

    @GetMapping("/authorizeAtmo")
    public ResponseEntity<String> loginNetatmo(@RequestParam String id) {
        URI uri = UriComponentsBuilder.fromUriString(NETATMO_API_URI + "/oauth2/authorize")
                .queryParam("client_id", netatmoConfig.getClientId())
                .queryParam("redirect_uri", appConfig.getRedirectUri() + REDIRECT_ENDPOINT)
                .queryParam("scope", NETATMO_SCOPE)
                .queryParam("state", id)
                .build().toUri();
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build();
    }

    @GetMapping(CALLBACK_ATMO)
    public ResponseEntity<String> atmocb(@RequestParam String state, @RequestParam String code) throws IOException {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", netatmoConfig.getClientId());
        formData.add("client_secret", netatmoConfig.getClientSecret());
        formData.add("code", code);
        formData.add("redirect_uri", appConfig.getRedirectUri() + REDIRECT_ENDPOINT);
        formData.add("scope", NETATMO_SCOPE);
        TokenResponse tokenResponse = tokenRestClient.post().uri("/oauth2/token").body(formData)
                .retrieve().body(TokenResponse.class);
        User user = redisUserService.findByUsername(state);
        if (tokenResponse == null) {
            throw new IOException("Unexpected null tokenResponse!");
        }
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());
        user.setExpiresAt(System.currentTimeMillis() + tokenResponse.getExpiresIn()*1000);
        redisUserService.save(user);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create("/")).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody SignupRequest signupRequest) {
        User user = redisUserService.findByUsername(signupRequest.getUsername());
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getUsername())));
    }

    @PostMapping("/signup")
    public ResponseEntity<User> signup(@RequestBody User user) {
        user.setUsername(user.getUsername());
        redisUserService.save(user);
        return ResponseEntity.ok(user);
    }
}

