package net.pautet.softs.demospring.identity.internal.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.pautet.softs.demospring.foundation.AppConfig;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(AppConfig appConfig) {
        byte[] decoded = Base64.getDecoder().decode(appConfig.tokenEncryptionKey());
        if (decoded.length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be a base64-encoded 256-bit key");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String value) {
        if (value == null) return null;
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt OAuth credential", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String value) {
        if (value == null) return null;
        if (!value.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported encrypted credential format");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid encrypted credential", ex);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalStateException("Invalid encrypted credential");
        }
        byte[] iv = new byte[IV_BYTES];
        byte[] encrypted = new byte[payload.length - IV_BYTES];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not decrypt OAuth credential", ex);
        }
    }
}
