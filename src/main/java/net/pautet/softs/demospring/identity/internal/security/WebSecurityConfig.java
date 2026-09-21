package net.pautet.softs.demospring.identity.internal.security;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.foundation.AppConfig;
import net.pautet.softs.demospring.identity.internal.web.PasskeyController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;

import java.net.URI;

@Configuration
@AllArgsConstructor
public class WebSecurityConfig {

    private final ApiAuthenticationEntryPoint unauthorizedHandler;
    private final AppConfig appConfig;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String origin = configuredOrDefault(appConfig.webauthnOrigin(), normalizedOrigin(appConfig.redirectUri()));
        String rpId = configuredOrDefault(appConfig.webauthnRpId(), URI.create(origin).getHost());
        HttpSessionSecurityContextRepository securityContexts = new HttpSessionSecurityContextRepository();

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .cors(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContexts))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/*", "/assets/**", "/static/**", "/api/auth/hello",
                                        "/api/auth/passkey/csrf", "/api/auth/passkey/begin",
                                        "/webauthn/authenticate/options", "/login/webauthn",
                                        "/api/auth/callbackAtmo", "/api/netatmo/authorize", "/api/netatmo/callback",
                                        "/api/datacloud/data", "/api/salesforce/**").permitAll()
                                .requestMatchers("/webauthn/register/**", "/api/auth/passkey/complete-registration")
                                        .hasRole("PASSKEY_REGISTRATION")
                                .anyRequest().hasRole("USER")
                )
                .webAuthn(webAuthn -> webAuthn
                        .rpId(rpId)
                        .rpName("DemoSpring")
                        .allowedOrigins(origin)
                        .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
                            @Override
                            public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                                configureLoginSuccess(filter, securityContexts);
                                return filter;
                            }
                        }))
                .logout(Customizer.withDefaults());
        return http.build();
    }

    private WebAuthnAuthenticationFilter configureLoginSuccess(
            WebAuthnAuthenticationFilter filter,
            HttpSessionSecurityContextRepository securityContexts) {
        filter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            String expected = request.getSession(false) == null ? null
                    : (String) request.getSession(false).getAttribute(PasskeyController.EXPECTED_EMAIL);
            if (expected == null || !expected.equalsIgnoreCase(authentication.getName())) {
                SecurityContextHolder.clearContext();
                response.sendError(401, "The passkey does not belong to the requested email address");
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContexts.saveContext(context, request, response);
            request.getSession().removeAttribute(PasskeyController.EXPECTED_EMAIL);
            response.setContentType("application/json");
            response.getWriter().write("{\"authenticated\":true,\"redirectUrl\":\"/\"}");
        });
        return filter;
    }

    private static String configuredOrDefault(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private static String normalizedOrigin(String redirectUri) {
        URI uri = URI.create(redirectUri);
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port == -1 ? "" : ":" + port);
    }
}
