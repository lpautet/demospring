package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.TokenResponse;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Date;

import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {

    private NetatmoConfig netatmoConfig;
    private UserService userService;

    private final WebClient apiWebClient = WebClient.builder().baseUrl("https://api.netatmo.com/api").build();

    private WebClient createApiWebClient(Principal principal) {
        return WebClient.builder().baseUrl("https://api.netatmo.com/api").filter(tokenHandlingFilter(principal)).build();
    }

    private ExchangeFilterFunction tokenHandlingFilter(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();

        return (request, next) -> {
            ClientRequest newRequest = ClientRequest.from(request)
                    .headers(httpHeaders -> httpHeaders.setBearerAuth(user.getAccessToken()))
                    .build();
            return next.exchange(newRequest)
                    .filter(clientResponse -> clientResponse.statusCode() != HttpStatus.UNAUTHORIZED)
                    // handle 401 Unauthorized (token expired)
                    .switchIfEmpty(refreshToken(user).flatMap(updatedUser ->
                            next.exchange(ClientRequest.from(request)
                                    .headers(httpHeaders -> httpHeaders.setBearerAuth(updatedUser.getAccessToken()))
                                    .build())));
        };
    }

    @GetMapping("/hello")
    public Mono<String> getHello() {
        return Mono.just("Hello, the time at the server is now " + new Date() + "\n");
    }

    @GetMapping("/whoami")
    public Mono<ResponseEntity<User>> getWhoAmI(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return Mono.just(ResponseEntity.ok(user));
    }

    @GetMapping("/homesdata")
    public Mono<String> getHomesData(Principal principal) {
//        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
//        return apiWebClient.get().uri("/homesdata").
//                header("Authorization", "Bearer " + user.getAccessToken())
//                .retrieve().bodyToMono(String.class);
        return createApiWebClient(principal).get().uri("/homesdata").retrieve().bodyToMono(String.class);
    }

    @GetMapping("/homestatus")
    public Mono<String> getHomeStatus(Principal principal, @RequestParam("home_id") String homeId) {
//        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
//        return apiWebClient.get().uri(uriBuilder -> uriBuilder.path("/homestatus")
//                        .queryParam("home_id", homeId).build())
//                .header("Authorization", "Bearer " + user.getAccessToken())
//                .retrieve().bodyToMono(String.class);
        return createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/homestatus")
                        .queryParam("home_id", homeId).build())
                .retrieve().bodyToMono(String.class);
    }

    private Mono<User> refreshToken(User user) {
        return WebClient.builder().baseUrl(NETATMO_API_URI)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build().post().uri("/oauth2/token").body(
                        BodyInserters.fromFormData("grant_type", "refresh_token")
                                .with("client_id", netatmoConfig.getClientId())
                                .with("client_secret", netatmoConfig.getClientSecret())
                                .with("refresh_token", user.getRefreshToken()))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .flatMap(tokenResponse -> {
                    System.out.println("Token refreshed!");
                    user.setAccessToken(tokenResponse.getAccessToken());
                    user.setRefreshToken(tokenResponse.getRefreshToken());
                    return userService.save(user);
                });
    }
}
