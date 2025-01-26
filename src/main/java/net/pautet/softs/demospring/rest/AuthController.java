package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.JWTUtil;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.AuthResponse;
import net.pautet.softs.demospring.entity.SignupRequest;
import net.pautet.softs.demospring.entity.TokenResponse;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Date;

@RestController
@AllArgsConstructor
@RequestMapping(AuthController.AUTH_ENDPOINTS_PREFIX)
public class AuthController {

    static final String AUTH_ENDPOINTS_PREFIX = "/api/auth";
    static final String CALLBACK_ATMO = "/callbackAtmo";
    static final String REDIRECT_ENDPOINT = AUTH_ENDPOINTS_PREFIX + CALLBACK_ATMO;
    static final String NETATMO_SCOPE = "read_station read_thermostat";
    static final String NETATMO_API_URI = "https://api.netatmo.com";

    private NetatmoConfig netatmoConfig;
    private JWTUtil jwtUtil;
    private UserRepository userRepository;
    private AppConfig appConfig;

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
    public ResponseEntity<String> atmocb(@RequestParam String state, @RequestParam String code) {
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", netatmoConfig.getClientId());
        formData.add("client_secret", netatmoConfig.getClientSecret());
        formData.add("code", code);
        formData.add("redirect_uri", appConfig.getRedirectUri() + REDIRECT_ENDPOINT);
        formData.add("scope", NETATMO_SCOPE);
        TokenResponse tokenResponse = tokenRestClient.post().uri("/oauth2/token").body(formData)
                .retrieve().body(TokenResponse.class);
        User user = userRepository.findByUsername(state);
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());
        user.setExpiresAt(System.currentTimeMillis() + tokenResponse.getExpiresIn()*1000);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create("/")).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody SignupRequest signupRequest) {
        User user = userRepository.findByUsername(signupRequest.getUsername());
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getUsername())));
    }

    @PostMapping("/signup")
    public ResponseEntity<User> signup(@RequestBody User user) {
        user.setId(null);
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }
}

