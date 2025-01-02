package net.pautet.softs.demospring.service;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.config.JWTUtil;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Component
@AllArgsConstructor
public class JWTAuthenticationManager implements ReactiveAuthenticationManager {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private final JWTUtil jwtUtil;
    private final UserService userService;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        String token = authentication.getCredentials().toString();
        String username = jwtUtil.extractUsername(token);

        return userService.findByUsername(username)
                .map(user -> {
                    if (jwtUtil.validateToken(token, user.getUsername())) {
                        return new UsernamePasswordAuthenticationToken(user, null, getGrantedAuthorities());
                    } else {
                        throw new AuthenticationException("Invalid JWT token") {};
                    }
                });
    }

    private Collection<SimpleGrantedAuthority> getGrantedAuthorities() {
        return List.of(new SimpleGrantedAuthority(DEFAULT_ROLE));
    }

}
