package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
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
import reactor.core.publisher.Mono;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Map;

@RestController
@AllArgsConstructor
public class AuthController {

    static final String REDIRECT_URI = "http://localhost:8080/atmocb";
    static final String NETATMO_SCOPE = "read_station";
    static final MessageFormat NETATMO_OAUTH_URL_FORMAT = new MessageFormat("https://api.netatmo.com/oauth2/authorize?" +
            "client_id={0}" +
            "&redirect_uri={1}" +
            "&scope={2}" +
            "&state={3}");

    private NetatmoConfig netatmoConfig;
    private JWTUtil jwtUtil;
    private UserService userService;

    private final WebClient tokenWebClient = WebClient.builder().baseUrl("https://api.netatmo.com").defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE).build();

    @GetMapping("/loginAtmo")
    public Mono<ResponseEntity<String>> loginNetatmo(@RequestParam String id) {
        Object[] args = {
                netatmoConfig.getClientId(),
                REDIRECT_URI, NETATMO_SCOPE, id
        };
        return Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create(NETATMO_OAUTH_URL_FORMAT.format(args))).build());
    }

    @GetMapping("/atmocb")
    public Mono<ResponseEntity<String>> atmocb(@RequestParam String state, @RequestParam String code) {
        return tokenWebClient.post().uri("/oauth2/token").body(
                        BodyInserters.fromFormData("grant_type", "authorization_code")
                                .with("client_id", netatmoConfig.getClientId())
                                .with("client_secret", netatmoConfig.getClientSecret())
                                .with("code", code)
                                .with("redirect_uri", REDIRECT_URI)
                                .with("scope", NETATMO_SCOPE))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .flatMap(tokenResponse ->
                    userService.findByUsername(state)
                            .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username in callback")))
                            .flatMap(user -> {
                                user.setAccessToken(tokenResponse.getAccessToken());
                                user.setRefreshToken(tokenResponse.getRefreshToken());
                                return userService.save(user);
                            }).then(Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                    .location(URI.create("/")).build()))
                );
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest authRequest) {
        return userService.findByUsername(authRequest.getUsername())
                .map(userDetails ->
                        ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(authRequest.getUsername())))
                ).switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/signup")
    public Mono<ResponseEntity<User>> signup(@RequestBody User user) {
        // Encrypt password before saving
        //user.setPassword(user.getPassword());
        user.setId(null);
        return userService.save(user)
                .map(savedUser -> ResponseEntity.ok(user));
    }

    @GetMapping("/protected")
    public Mono<ResponseEntity<String>> protectedEndpoint() {
        return Mono.just(ResponseEntity.ok("You have accessed a protected endpoint!"));
    }

    @GetMapping("/env")
    public Mono<ResponseEntity<Map<String, String>>> getEnv() {
        return Mono.just(ResponseEntity.ok(System.getenv()));
    }
}
