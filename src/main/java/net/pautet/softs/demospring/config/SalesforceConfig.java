package net.pautet.softs.demospring.config;

import io.jsonwebtoken.Jwts;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.SalesforceCredentials;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

@Slf4j
@ConfigurationProperties("salesforce")
@Setter
@Configuration
public class SalesforceConfig {

    private String clientId;
    private String username;
    private String loginUrl;

    @Bean
    public SalesforceCredentials salesforceCredentials() throws Exception {
        try {
            PrivateKey privateKey = loadPrivateKey();
            if (privateKey == null) {
                throw new IllegalStateException("Private key could not be loaded");
            }

            long now = System.currentTimeMillis();
            String jwt = Jwts.builder()
                    .issuer(clientId)
                    .subject(username)
                    .claim("aud", loginUrl)
                    .expiration(new Date(now + 3600 * 1000))
                    .signWith(privateKey)
                    .compact();

            // Get Salesforce token
            String[] salesforceTokenData = getSalesforceToken(jwt);
            String salesforceAccessToken = salesforceTokenData[0];
            String instanceUrl = salesforceTokenData[1];

            // Exchange for Data Cloud token
            return getDataCloudToken(salesforceAccessToken, instanceUrl);
        } catch (Exception e) {
            log.error("Error generating Salesforce tokens: ", e);
            throw e;
        }
    }

    private PrivateKey loadPrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent = System.getenv("SF_PRIVATE_KEY")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decodedKey = java.util.Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = null;
        kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private String[] getSalesforceToken(String jwt) throws IOException {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost post = new HttpPost(loginUrl + "/services/oauth2/token");
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt;
        post.setEntity(new StringEntity(body));

        HttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        log.debug("Salesforce token response: {}", responseBody);

        if (!responseBody.contains("access_token")) {
            throw new IllegalStateException("Failed to retrieve Salesforce token: " + responseBody);
        }

        String accessToken = parseAccessToken(responseBody);
        String instanceUrl = parseInstanceUrl(responseBody);
        return new String[]{accessToken, instanceUrl};
    }

    private SalesforceCredentials getDataCloudToken(String salesforceAccessToken, String instanceUrl) throws IOException {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost post = new HttpPost(instanceUrl + "/services/a360/token");
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        String body = "grant_type=urn:salesforce:grant-type:external:cdp&subject_token=" + salesforceAccessToken + "&subject_token_type=urn:ietf:params:oauth:token-type:access_token";
        post.setEntity(new StringEntity(body));

        HttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        log.debug("Data Cloud token response: {}", responseBody);

        if (!responseBody.contains("access_token")) {
            throw new IllegalStateException("Failed to retrieve Data Cloud token: " + responseBody);
        }

        return new SalesforceCredentials(salesforceAccessToken, instanceUrl, parseAccessToken(responseBody), "https://" + parseInstanceUrl(responseBody));
    }

    private String parseAccessToken(String json) {
        String tokenKey = "\"access_token\":\"";
        int start = json.indexOf(tokenKey) + tokenKey.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String parseInstanceUrl(String json) {
        String urlKey = "\"instance_url\":\"";
        int start = json.indexOf(urlKey) + urlKey.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
