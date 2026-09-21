package net.pautet.softs.demospring.identity.internal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
class WebAuthnPersistenceConfig {

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    WebAuthnSchemaInitializer webAuthnSchemaInitializer(DataSource dataSource, JdbcTemplate jdbc) {
        return new WebAuthnSchemaInitializer(dataSource, jdbc);
    }

    static final class WebAuthnSchemaInitializer {
        WebAuthnSchemaInitializer(DataSource dataSource, JdbcTemplate jdbc) {
            String product = databaseProduct(dataSource);
            String binaryType = product.contains("PostgreSQL") || product.contains("H2") ? "bytea" : "blob";
            jdbc.execute("""
                    create table if not exists user_entities (
                        id varchar(1000) not null primary key,
                        name varchar(100) not null unique,
                        display_name varchar(200)
                    )
                    """);
            jdbc.execute("""
                    create table if not exists user_credentials (
                        credential_id varchar(1000) not null primary key,
                        user_entity_user_id varchar(1000) not null,
                        public_key %s not null,
                        signature_count bigint,
                        uv_initialized boolean,
                        backup_eligible boolean not null,
                        authenticator_transports varchar(1000),
                        public_key_credential_type varchar(100),
                        backup_state boolean not null,
                        attestation_object %s,
                        attestation_client_data_json %s,
                        created timestamp,
                        last_used timestamp,
                        label varchar(1000) not null
                    )
                    """.formatted(binaryType, binaryType, binaryType));
        }

        private static String databaseProduct(DataSource dataSource) {
            try (Connection connection = dataSource.getConnection()) {
                return connection.getMetaData().getDatabaseProductName();
            } catch (SQLException ex) {
                throw new IllegalStateException("Could not initialize WebAuthn schema", ex);
            }
        }
    }
}
