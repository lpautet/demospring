package net.pautet.softs.demospring.identity;

import net.pautet.softs.demospring.foundation.AppConfig;
import net.pautet.softs.demospring.identity.internal.persistence.NetatmoConnection;
import net.pautet.softs.demospring.identity.internal.persistence.NetatmoConnectionRepository;
import net.pautet.softs.demospring.identity.internal.persistence.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;
    private final NetatmoConnectionRepository connections;
    private final AppConfig appConfig;

    public UserService(UserRepository users, NetatmoConnectionRepository connections, AppConfig appConfig) {
        this.users = users;
        this.connections = connections;
        this.appConfig = appConfig;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        if (username == null) return null;
        return users.findByUsernameIgnoreCase(normalize(username)).map(this::attachCredentials).orElse(null);
    }

    @Transactional
    public User save(User user) {
        user.setUsername(normalize(user.getUsername()));
        User saved = users.save(user);
        if (user.getAccessToken() != null || user.getRefreshToken() != null) {
            NetatmoConnection connection = connections
                    .findByUserIdAndPurpose(saved.getId(), NetatmoConnection.Purpose.USER_DASHBOARD)
                    .orElseGet(NetatmoConnection::new);
            connection.setUser(saved);
            connection.setPurpose(NetatmoConnection.Purpose.USER_DASHBOARD);
            NetatmoCredentialService.copy(new NetatmoCredentials(user.getAccessToken(), user.getRefreshToken(),
                    user.getExpiresAt(), user.getRefreshedAt()), connection);
            connections.save(connection);
        }
        return attachCredentials(saved);
    }

    @Transactional
    public void delete(String username) {
        users.findByUsernameIgnoreCase(normalize(username)).ifPresent(user -> {
            connections.deleteByUserId(user.getId());
            users.delete(user);
        });
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        if (user == null) throw new UsernameNotFoundException("User not found: " + username);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (appConfig.adminEmail() != null && !appConfig.adminEmail().isBlank()
                && appConfig.adminEmail().equalsIgnoreCase(user.getUsername())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new org.springframework.security.core.userdetails.User(user.getUsername(), "", authorities);
    }

    private User attachCredentials(User user) {
        connections.findByUserIdAndPurpose(user.getId(), NetatmoConnection.Purpose.USER_DASHBOARD)
                .ifPresent(connection -> {
                    user.setAccessToken(connection.getAccessToken());
                    user.setRefreshToken(connection.getRefreshToken());
                    user.setExpiresAt(connection.getExpiresAt());
                    user.setRefreshedAt(connection.getRefreshedAt());
                });
        return user;
    }

    private static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
