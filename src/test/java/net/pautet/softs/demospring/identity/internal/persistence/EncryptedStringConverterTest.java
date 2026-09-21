package net.pautet.softs.demospring.identity.internal.persistence;

import net.pautet.softs.demospring.foundation.AppConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter(new AppConfig(
            "http://localhost:8080", "localhost", "http://localhost:8080", "admin@example.com",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));

    @Test
    void encryptsWithAUniqueIvAndDecryptsTheOriginalValue() {
        String first = converter.convertToDatabaseColumn("oauth-secret");
        String second = converter.convertToDatabaseColumn("oauth-secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("oauth-secret");
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo("oauth-secret");
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo("oauth-secret");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = converter.convertToDatabaseColumn("oauth-secret");
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
