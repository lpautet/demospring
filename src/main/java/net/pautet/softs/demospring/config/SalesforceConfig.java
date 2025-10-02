package net.pautet.softs.demospring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

@Slf4j
@ConfigurationProperties("salesforce")
public record SalesforceConfig (

     String clientId,
     String username,
     String loginUrl,
     Long sessionTimeout,
     String connectorName,
     PrivateKey privateKey
     ) {

    @ConstructorBinding
    public SalesforceConfig(String clientId, String username, String loginUrl, Long sessionTimeout, String connectorName) throws InvalidKeySpecException, NoSuchAlgorithmException {
        this(
                clientId,
                username,
                loginUrl,
                sessionTimeout,
                connectorName,
                generatePrivateKey()
        );
    }

    private static PrivateKey generatePrivateKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
        if (System.getenv("SF_PRIVATE_KEY") == null) {
            log.warn("SF_PRIVATE_KEY not found in environment variables. Salesforce Data Cloud integration will be disabled.");
            return null;
        }
        String keyContent = System.getenv("SF_PRIVATE_KEY")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decodedKey = java.util.Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

}
