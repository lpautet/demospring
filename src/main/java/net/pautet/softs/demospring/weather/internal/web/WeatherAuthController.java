package net.pautet.softs.demospring.weather.internal.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.foundation.AppConfig;
import net.pautet.softs.demospring.identity.UserService;
import net.pautet.softs.demospring.identity.User;
import net.pautet.softs.demospring.weather.NetatmoService;
import net.pautet.softs.demospring.weather.NetatmoTokenResponse;
import net.pautet.softs.demospring.weather.internal.config.NetatmoConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import static net.pautet.softs.demospring.weather.NetatmoService.NETATMO_API_URI;
import static net.pautet.softs.demospring.weather.NetatmoService.NETATMO_SCOPE;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
class WeatherAuthController {

    private static final String CALLBACK_PATH = "/callbackAtmo";
    private static final String REDIRECT_ENDPOINT = "/api/auth" + CALLBACK_PATH;
    private static final String OAUTH_STATE = WeatherAuthController.class.getName() + ".state";
    private static final String OAUTH_USER = WeatherAuthController.class.getName() + ".user";

    private final NetatmoConfig netatmoConfig;
    private final UserService userService;
    private final AppConfig appConfig;
    private final NetatmoService netatmoService;

    @GetMapping("/authorizeAtmo")
    ResponseEntity<String> authorize(Principal principal, HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(OAUTH_STATE, state);
        session.setAttribute(OAUTH_USER, principal.getName());
        URI uri = UriComponentsBuilder.fromUriString(NETATMO_API_URI + "/oauth2/authorize")
                .queryParam("client_id", netatmoConfig.clientId())
                .queryParam("redirect_uri", appConfig.redirectUri() + REDIRECT_ENDPOINT)
                .queryParam("scope", NETATMO_SCOPE)
                .queryParam("state", state)
                .build().toUri();
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build();
    }

    @GetMapping(CALLBACK_PATH)
    ResponseEntity<String> callback(@RequestParam String state, @RequestParam String code, HttpSession session) throws IOException {
        String expectedState = (String) session.getAttribute(OAUTH_STATE);
        String username = (String) session.getAttribute(OAUTH_USER);
        if (expectedState == null || username == null || !expectedState.equals(state)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid OAuth state");
        }
        session.removeAttribute(OAUTH_STATE);
        session.removeAttribute(OAUTH_USER);
        NetatmoTokenResponse tokenResponse = netatmoService.exchangeCodeForTokens(
                code, appConfig.redirectUri() + REDIRECT_ENDPOINT);
        User user = userService.findByUsername(username);
        user.setAccessToken(tokenResponse.accessToken());
        user.setRefreshToken(tokenResponse.refreshToken());
        user.setExpiresAt(System.currentTimeMillis() + tokenResponse.expiresIn() * 1000);
        userService.save(user);
        log.info("New access/refresh tokens saved for user {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create("/")).build();
    }
}
