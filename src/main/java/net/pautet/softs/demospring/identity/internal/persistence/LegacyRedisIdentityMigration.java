package net.pautet.softs.demospring.identity.internal.persistence;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.identity.NetatmoCredentialService;
import net.pautet.softs.demospring.identity.NetatmoCredentials;
import net.pautet.softs.demospring.identity.User;
import net.pautet.softs.demospring.identity.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.legacy-redis-migration.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
class LegacyRedisIdentityMigration implements ApplicationRunner {

    private final StringRedisTemplate redis;
    private final UserService users;
    private final NetatmoCredentialService credentials;

    LegacyRedisIdentityMigration(StringRedisTemplate redis,
                                 UserService users,
                                 NetatmoCredentialService credentials) {
        this.redis = redis;
        this.users = users;
        this.credentials = credentials;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateUsers();
        migrateSystemConnection();
    }

    private void migrateUsers() {
        Set<String> keys = redis.keys("user:*");
        if (keys == null || keys.isEmpty()) return;
        int migrated = 0;
        for (String key : keys) {
            Map<Object, Object> values = redis.opsForHash().entries(key);
            String username = string(values.get("username"));
            if (username == null || users.findByUsername(username) != null) continue;
            User user = new User();
            user.setUsername(username);
            user.setAccessToken(string(values.get("accessToken")));
            user.setRefreshToken(string(values.get("refreshToken")));
            user.setExpiresAt(number(values.get("expiresAt")));
            user.setRefreshedAt(number(values.get("refreshedAt")));
            users.save(user);
            migrated++;
        }
        if (migrated > 0) log.info("Migrated {} legacy Redis user record(s) to PostgreSQL", migrated);
    }

    private void migrateSystemConnection() {
        if (credentials.findSystemCredentials() != null) return;
        String refreshToken = redis.opsForValue().get("netatmo:refresh_token");
        if (refreshToken == null) return;
        credentials.saveSystemCredentials(new NetatmoCredentials(
                redis.opsForValue().get("netatmo:access_token"),
                refreshToken,
                number(redis.opsForValue().get("netatmo:expires_at")),
                System.currentTimeMillis()));
        log.info("Migrated legacy system Netatmo credentials from Redis to PostgreSQL");
    }

    private static String string(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Long number(Object value) {
        String text = string(value);
        return text == null ? null : Long.parseLong(text);
    }
}
