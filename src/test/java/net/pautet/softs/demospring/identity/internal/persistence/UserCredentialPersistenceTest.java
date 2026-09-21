package net.pautet.softs.demospring.identity.internal.persistence;

import jakarta.persistence.EntityManager;
import net.pautet.softs.demospring.identity.User;
import net.pautet.softs.demospring.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@SpringBootTest
@Transactional
class UserCredentialPersistenceTest {

    @Autowired
    private UserService users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean(answers = RETURNS_DEEP_STUBS)
    private StringRedisTemplate redis;

    @Test
    void persistsUsersAndEncryptedOAuthCredentialsInPostgres() {
        User user = new User();
        user.setUsername("Storage.User@Example.com");
        user.setAccessToken("access-secret");
        user.setRefreshToken("refresh-secret");
        user.setExpiresAt(1234L);
        users.save(user);
        entityManager.flush();
        entityManager.clear();

        String storedAccessToken = jdbc.sql("select access_token from netatmo_connections where user_id = :userId")
                .param("userId", user.getId())
                .query(String.class)
                .single();
        assertThat(storedAccessToken).startsWith("v1:").doesNotContain("access-secret");

        User reloaded = users.findByUsername("storage.user@example.com");
        assertThat(reloaded.getUsername()).isEqualTo("storage.user@example.com");
        assertThat(reloaded.getAccessToken()).isEqualTo("access-secret");
        assertThat(reloaded.getRefreshToken()).isEqualTo("refresh-secret");
    }
}
