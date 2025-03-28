package net.pautet.softs.demospring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.SalesforceCredentials;
import net.pautet.softs.demospring.entity.TokenResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

@Slf4j
@ConfigurationProperties("salesforce")
@Configuration
@Setter
public class SalesforceConfig {

    private String clientId;
    private String username;
    private String loginUrl;

    private SalesforceCredentials salesforceCredentials = new SalesforceCredentials();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PrivateKey privateKey;

    public RestClient createDataCloudApiClient() throws IOException {
        if (this.salesforceCredentials.dataCloudAccessToken() == null || this.salesforceCredentials.dataCloudAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Data Cloud Access Token");
            getDataCloudToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.dataCloudInstanceUrl())
                .defaultHeader("Authorization", "Bearer " + this.salesforceCredentials.dataCloudAccessToken())
                //  .requestInterceptor(new ApiController.RefreshTokenInterceptor(principal))
                .build();
    }

    public RestClient createSalesforceApiClient() throws IOException {
        if (this.salesforceCredentials.salesforceAccessToken() == null || this.salesforceCredentials.salesforceAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Salesforce Access Token");
            getSalesforceToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.salesforceInstanceUrl())
                .defaultHeader("Authorization", "Bearer " + this.salesforceCredentials.salesforceAccessToken())
                //  .requestInterceptor(new ApiController.RefreshTokenInterceptor(principal))
                .build();
    }

    @PostConstruct
    public void createSalesforceConfig() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        loadPrivateKey();
    }

    private void loadPrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (System.getenv("SF_PRIVATE_KEY") == null) {
            throw new IllegalStateException("Cannot get SF_PRIVATE_KEY !");
        }
        String keyContent = System.getenv("SF_PRIVATE_KEY")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decodedKey = java.util.Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.privateKey = kf.generatePrivate(spec);
    }

    private void getSalesforceToken() throws IOException {
        if (loginUrl == null) {
            throw new IllegalStateException("No salesforce.loginUrl defined");
        }
        if (clientId == null) {
            throw new IllegalStateException("No salesforce.clientId defined");
        }
        if (username == null) {
            throw new IllegalStateException("No salesforce.username defined");
        }
        long salesforceTokenExpiresAt = System.currentTimeMillis() + 3 * 3600 * 1000;

        String jwt = Jwts.builder()
                .issuer(clientId)
                .subject(username)
                .claim("aud", loginUrl)
                .expiration(new Date(salesforceTokenExpiresAt))
                .signWith(privateKey)
                .compact();

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", jwt);
        TokenResponse tokenResponse = RestClient.builder().baseUrl(loginUrl)
                .build().post().uri("/services/oauth2/token").body(formData)
                .retrieve().onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Getting Salesforce Token failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                })
                .body(TokenResponse.class);
        if (tokenResponse == null) {
            throw new IOException("Unexpected null TokenResponse for Salesforce token");
        } else {
            log.info("Salesforce access token response: {}", tokenResponse);
            this.salesforceCredentials = new SalesforceCredentials(this.salesforceCredentials, salesforceTokenExpiresAt - 60 * 1000, tokenResponse.getAccessToken(), tokenResponse.getInstanceUrl());
        }
    }

    private void getDataCloudToken() throws IOException {
        RestClient apiClient = createSalesforceApiClient();
        if (salesforceCredentials.salesforceAccessToken() == null) {
            throw new IllegalStateException("No salesforce access token to get data cloud token !");
        }
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:salesforce:grant-type:external:cdp");
        formData.add("subject_token", salesforceCredentials.salesforceAccessToken());
        formData.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        TokenResponse tokenResponse = apiClient.post().uri("/services/a360/token")
                .body(formData).retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Getting Data Cloud Token failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                })
                .body(TokenResponse.class);

        if (tokenResponse == null || tokenResponse.getExpiresIn() == null) {
            throw new IOException("Unexpected Data Cloud access token response: " + tokenResponse);
        }

        long expiresAt = System.currentTimeMillis() - 60000 + 1000 * tokenResponse.getExpiresIn();
        this.salesforceCredentials = new SalesforceCredentials(salesforceCredentials, tokenResponse.getAccessToken(), expiresAt, "https://" + tokenResponse.getInstanceUrl());
    }

}
