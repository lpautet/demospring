package net.pautet.softs.demospring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.SalesforceCredentials;
import net.pautet.softs.demospring.entity.SalesforceUserInfo;
import net.pautet.softs.demospring.entity.TokenResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private Long sessionTimeout;

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
                .build();
    }

    public RestClient createSalesforceApiClient() throws IOException {
        if (this.salesforceCredentials.salesforceAccessToken() == null || this.salesforceCredentials.salesforceAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Salesforce Access Token");
            getSalesforceToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.salesforceInstanceUrl())
                .defaultHeader("Authorization", "Bearer " + this.salesforceCredentials.salesforceAccessToken())
                .build();
    }

    public RestClient createSalesforceIdClient() throws IOException {
        if (this.salesforceCredentials.salesforceAccessToken() == null || this.salesforceCredentials.salesforceAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Salesforce Token");
            getSalesforceToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.salesforceUserId())
                .defaultHeader("Authorization", "Bearer " + this.salesforceCredentials.salesforceAccessToken())
                .build();
    }

    @PostConstruct
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
        if (sessionTimeout == null) {
            throw new IllegalStateException("No salesforce.sessionTimeout defined");
        }
        // salesforce token expiration is defined by the session level parameter in
        // salesforce configuration and there is no way to get a true expiration date for the token
        // usual default is 2 hours
        long salesforceTokenExpiresAt = System.currentTimeMillis() + sessionTimeout * 1000;

        String jwt = Jwts.builder()
                .issuer(clientId)
                .subject(username)
                .claim("aud", loginUrl)
                .expiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000)) // 5 mins
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

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new IOException("Unexpected TokenResponse for Salesforce token: " + tokenResponse);
        } else {
            log.info("Salesforce access token response: {}", tokenResponse);
            this.salesforceCredentials = new SalesforceCredentials(this.salesforceCredentials, salesforceTokenExpiresAt - 60 * 1000, tokenResponse.getAccessToken(), tokenResponse.getId(), tokenResponse.getInstanceUrl());
            getSalesforceUser();
        }
    }

    public SalesforceUserInfo getSalesforceUser() throws IOException {
        RestClient apiClient = createSalesforceIdClient();
        if (salesforceCredentials.salesforceUserId() == null) {
            throw new IllegalStateException("No salesforce user id !");
        }
        ResponseEntity<SalesforceUserInfo> userResponse = apiClient.get().retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Getting Salesforce User failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                })
                .toEntity(SalesforceUserInfo.class);

        System.out.println(userResponse.getBody());
        return userResponse.getBody();
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
        ResponseEntity<String> postResponse = apiClient.post().uri("/services/a360/token")
                .body(formData).retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a string
                    String errorBody = objectMapper.readValue(response.getBody(), String.class);
                    throw new IOException("Getting Data Cloud Token failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                })
                .toEntity(String.class);

        // Get the Content-Type header
        MediaType contentType = postResponse.getHeaders().getContentType();

        // Handle based on Content-Type
        if (contentType != null && contentType.includes(MediaType.APPLICATION_JSON)) {
            TokenResponse tokenResponse = objectMapper.readValue(postResponse.getBody(), TokenResponse.class);
            if (tokenResponse.getAccessToken() == null || tokenResponse.getExpiresIn() == null) {
                throw new IOException("Unexpected Data Cloud access token response: " + tokenResponse);
            }
            log.info("Data Cloud Token: {}", tokenResponse);
            long expiresAt = System.currentTimeMillis() - 60000 + 1000 * tokenResponse.getExpiresIn();
            this.salesforceCredentials = new SalesforceCredentials(salesforceCredentials, tokenResponse.getAccessToken(), expiresAt, "https://" + tokenResponse.getInstanceUrl());
        } else if (contentType != null && contentType.includes(MediaType.TEXT_HTML)) {
            // Salesforce token is likely invalid now
            this.salesforceCredentials = new SalesforceCredentials();
            throw new IOException("Unexpected Data Cloud access token response: " + postResponse.getBody());
        } else {
            throw new IOException("Unexpected Data Cloud access token response, content-type=: " + contentType);
        }
    }

}
