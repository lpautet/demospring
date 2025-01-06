package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.AppConfig;
import net.pautet.softs.demospring.config.JWTUtil;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.*;
import net.pautet.softs.demospring.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Date;
import java.util.Map;

@RestController
@AllArgsConstructor
public class AuthController {

    static final String REDIRECT_ENDPOINT = "/atmocb";
    static final String NETATMO_SCOPE = "read_station read_thermostat";
    static final String NETATMO_API_URI = "https://api.netatmo.com";

    private NetatmoConfig netatmoConfig;
    private JWTUtil jwtUtil;
    private UserService userService;
    private AppConfig appConfig;

    private final WebClient tokenWebClient = WebClient.builder().baseUrl(NETATMO_API_URI).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE).build();

    @GetMapping("/loginAtmo")
    public Mono<ResponseEntity<String>> loginNetatmo(@RequestParam String id) {
        URI uri = UriComponentsBuilder.fromUriString(NETATMO_API_URI + "/oauth2/authorize")
                .queryParam("client_id", netatmoConfig.getClientId())
                .queryParam("redirect_uri", appConfig.getRedirectUri() + REDIRECT_ENDPOINT)
                .queryParam("scope", NETATMO_SCOPE)
                .queryParam("state", id)
                .build().toUri();
        return Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
    }

    @GetMapping(REDIRECT_ENDPOINT)
    public Mono<ResponseEntity<String>> atmocb(@RequestParam String state, @RequestParam String code) {
        return tokenWebClient.post().uri("/oauth2/token").body(
                        BodyInserters.fromFormData("grant_type", "authorization_code")
                                .with("client_id", netatmoConfig.getClientId())
                                .with("client_secret", netatmoConfig.getClientSecret())
                                .with("code", code)
                                .with("redirect_uri", appConfig.getRedirectUri() + REDIRECT_ENDPOINT)
                                .with("scope", NETATMO_SCOPE))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .flatMap(tokenResponse ->
                        userService.findByUsername(state)
                                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username in callback")))
                                .flatMap(user -> {
                                    user.setAccessToken(tokenResponse.getAccessToken());
                                    user.setRefreshToken(tokenResponse.getRefreshToken());
                                    user.setExpiresAt(System.currentTimeMillis() + tokenResponse.getExpiresIn()*1000);
                                    System.out.println(new Date(user.getExpiresAt()));
                                    return userService.save(user);
                                })
                                .then(Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                        .location(URI.create("/")).build()))
                );
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody SignupRequest signupRequest) {
        return userService.findByUsername(signupRequest.getUsername())
                .map(user ->
                        ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getUsername()))))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/signup")
    public Mono<ResponseEntity<User>> signup(@RequestBody User user) {
        user.setId(null);
        return userService.save(user).map(ResponseEntity::ok);
    }

    @GetMapping("/env")
    public Mono<ResponseEntity<Map<String, String>>> getEnv() {
        return Mono.just(ResponseEntity.ok(System.getenv()));
    }
}
