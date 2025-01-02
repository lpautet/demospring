package net.pautet.softs.demospring.config;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.repository.JWTSecurityContextRepository;
import net.pautet.softs.demospring.service.JWTAuthenticationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final JWTAuthenticationManager authenticationManager;
    private final JWTSecurityContextRepository securityContextRepository;

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {

        return serverHttpSecurity.authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers( "/api/**").authenticated()
                        .anyExchange().permitAll()
                )
                .build();
    }

}