package net.pautet.softs.demospring.rest;

import net.pautet.softs.demospring.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final WebClient apiWebClient = WebClient.builder().baseUrl("https://api.netatmo.com/api").build();
//WebClient.builder().filter((request, next) -> {
//        ClientRequest newReuqest = ClientRequest.from(request)
//                .header("Authorization", "YOUR_TOKEN")
//                .build();
//
//        return next.exchange(newRequest);
 //   }).build();
//    WebClient.builder().filter((request, next) -> {
//        final Mono<ClientResponse> response = next.exchange(request);
//        return response.filter(clientResponse -> clientResponse.statusCode() != HttpStatus.UNAUTHORIZED)
//                // handle 401 Unauthorized (token expired)
//                .switchIfEmpty(next.exchange(ClientRequest.from(request)
//                        .headers(httpHeaders -> httpHeaders.setBearerAuth(getNewToken()))
//                        .build()));
//    }).build();
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
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return apiWebClient.get().uri("/homesdata").
                header("Authorization", "Bearer " + user.getAccessToken()).retrieve().bodyToMono(String.class);
    }
}
