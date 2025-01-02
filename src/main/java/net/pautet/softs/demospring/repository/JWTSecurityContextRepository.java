package net.pautet.softs.demospring.repository;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.service.JWTAuthenticationManager;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class JWTSecurityContextRepository implements ServerSecurityContextRepository {

    private static final String TOKEN_PREFIX = "Bearer ";
    private final JWTAuthenticationManager authenticationManager;

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        throw new UnsupportedOperationException("JWTSecurityContextRepository.save is not implemented");
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange serverWebExchange) {
         return Mono.just(serverWebExchange.getRequest())
                .mapNotNull(serverHttpRequest -> serverHttpRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authenticationHeader -> authenticationHeader !=null && authenticationHeader.startsWith(TOKEN_PREFIX))
                .switchIfEmpty(Mono.empty())
                .map(authHeader -> authHeader.replace(TOKEN_PREFIX, "".trim()))
                .flatMap(authToken ->
                     authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authToken, authToken))
                )
                .map(authentication -> {
                    return new SecurityContextImpl(authentication);
                });
    }
}
