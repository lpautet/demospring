package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.NetatmoConfig;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.entity.TokenResponse;
import net.pautet.softs.demospring.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;


import java.security.Principal;
import java.util.Date;
import java.util.Map;

import static net.pautet.softs.demospring.rest.AuthController.NETATMO_API_URI;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {

    private NetatmoConfig netatmoConfig;
    private UserRepository userService;

    private RestClient createApiWebClient(Principal principal) {
        User user =(User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return RestClient.builder().baseUrl("https://api.netatmo.com/api").defaultHeader("Authorization", "Bearer " + user.getAccessToken()).build();
    }

//    private ExchangeFilterFunction tokenHandlingFilter(Principal principal) {
//        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
//
//        return (request, next) -> {
//            ClientRequest newRequest = ClientRequest.from(request)
//                    .headers(httpHeaders -> httpHeaders.setBearerAuth(user.getAccessToken()))
//                    .build();
//            return next.exchange(newRequest)
//                    .filter(clientResponse -> clientResponse.statusCode() != HttpStatus.FORBIDDEN)
//                    // handle 403 FORBIDDEN (Access token expired if error.code == 3)
//                    .switchIfEmpty(refreshToken(user).flatMap(updatedUser ->
//                            next.exchange(ClientRequest.from(request)
//                                    .headers(httpHeaders -> httpHeaders.setBearerAuth(updatedUser.getAccessToken()))
//                                    .build())));
//        };
//    }

    @GetMapping("/whoami")
    public ResponseEntity<User> getWhoAmI(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return ResponseEntity.ok(user);
    }

    @GetMapping("/homesdata")
    public String getHomesData(Principal principal) {
//        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
//        return apiWebClient.get().uri("/homesdata").
//                header("Authorization", "Bearer " + user.getAccessToken())
//                .retrieve().bodyToMono(String.class);
        return createApiWebClient(principal).get().uri("/homesdata")
                .retrieve().body(String.class);
    }

    @GetMapping("/homestatus")
    public String getHomeStatus(Principal principal, @RequestParam("home_id") String homeId) {
        return createApiWebClient(principal).get().uri(uriBuilder -> uriBuilder.path("/homestatus")
                        .queryParam("home_id", homeId).build())
                .retrieve()
                .onStatus(HttpStatus.FORBIDDEN::equals, (request,response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    System.out.println("/homestatus: FORBIDDEN: " + body);
                    //return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("getHomeStatus: FORBIDDEN " + body);
                })
                .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, (request, response) ->  {
                    String body = new String(response.getBody().readAllBytes());
                    System.out.println("/homestatus: SERVICE_UNAVAILABLE: " + body);
                    //return new Exception("getHomeStatus: SERVICE_UNAVAILABLE " + string);
                })
                .body(String.class);
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
        System.out.println("Token refreshed!");
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());
        return userService.save(user);

    }
}
